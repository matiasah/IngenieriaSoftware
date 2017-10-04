// Copyright 2017 The Nomulus Authors. All Rights Reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package google.registry.rde;

import static com.google.appengine.api.taskqueue.QueueFactory.getQueue;
import static com.google.appengine.api.taskqueue.TaskOptions.Builder.withUrl;
import static com.google.appengine.tools.cloudstorage.GcsServiceFactory.createGcsService;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Verify.verify;
import static google.registry.model.common.Cursor.getCursorTimeOrStartOfTime;
import static google.registry.model.ofy.ObjectifyService.ofy;
import static google.registry.xml.ValidationMode.LENIENT;
import static google.registry.xml.ValidationMode.STRICT;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.appengine.tools.cloudstorage.GcsFilename;
import com.google.appengine.tools.cloudstorage.RetryParams;
import com.google.appengine.tools.mapreduce.Reducer;
import com.google.appengine.tools.mapreduce.ReducerInput;
import com.googlecode.objectify.VoidWork;
import google.registry.config.RegistryConfig.Config;
import google.registry.gcs.GcsUtils;
import google.registry.keyring.api.KeyModule;
import google.registry.keyring.api.PgpHelper;
import google.registry.model.common.Cursor;
import google.registry.model.rde.RdeMode;
import google.registry.model.rde.RdeNamingUtils;
import google.registry.model.rde.RdeRevision;
import google.registry.model.registry.Registry;
import google.registry.request.Parameter;
import google.registry.request.RequestParameters;
import google.registry.request.lock.LockHandler;
import google.registry.tldconfig.idn.IdnTableEnum;
import google.registry.util.FormattingLogger;
import google.registry.util.TaskEnqueuer;
import google.registry.xjc.rdeheader.XjcRdeHeader;
import google.registry.xjc.rdeheader.XjcRdeHeaderElement;
import google.registry.xml.XmlException;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.security.Security;
import java.util.Iterator;
import java.util.concurrent.Callable;
import javax.inject.Inject;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.joda.time.DateTime;
import org.joda.time.Duration;

/** Reducer for {@link RdeStagingAction}. */
public final class RdeStagingReducer extends Reducer<PendingDeposit, DepositFragment, Void> {

  private static final long serialVersionUID = 60326234579091203L;

  private static final FormattingLogger logger = FormattingLogger.getLoggerForCallerClass();

  private final TaskEnqueuer taskEnqueuer;
  private final LockHandler lockHandler;
  private final int gcsBufferSize;
  private final String bucket;
  private final int ghostrydeBufferSize;
  private final Duration lockTimeout;
  private final byte[] stagingKeyBytes;
  private final RdeMarshaller marshaller;

  @Inject
  RdeStagingReducer(
      TaskEnqueuer taskEnqueuer,
      LockHandler lockHandler,
      @Config("gcsBufferSize") int gcsBufferSize,
      @Config("rdeBucket") String bucket,
      @Config("rdeGhostrydeBufferSize") int ghostrydeBufferSize,
      @Config("rdeStagingLockTimeout") Duration lockTimeout,
      @KeyModule.Key("rdeStagingEncryptionKey") byte[] stagingKeyBytes,
      @Parameter(RdeModule.PARAM_LENIENT) boolean lenient) {
    this.taskEnqueuer = taskEnqueuer;
    this.lockHandler = lockHandler;
    this.gcsBufferSize = gcsBufferSize;
    this.bucket = bucket;
    this.ghostrydeBufferSize = ghostrydeBufferSize;
    this.lockTimeout = lockTimeout;
    this.stagingKeyBytes = stagingKeyBytes;
    this.marshaller = new RdeMarshaller(lenient ? LENIENT : STRICT);
  }

  @Override
  public void reduce(final PendingDeposit key, final ReducerInput<DepositFragment> fragments) {
    Callable<Void> lockRunner = new Callable<Void>() {
      @Override
      public Void call() throws Exception {
        reduceWithLock(key, fragments);
        return null;
      }};
    String lockName = String.format("RdeStaging %s %s", key.tld(), key.mode());
    if (!lockHandler.executeWithLocks(lockRunner, null, lockTimeout, lockName)) {
      logger.warningfmt("Lock in use: %s", lockName);
    }
  }

  private void reduceWithLock(final PendingDeposit key, Iterator<DepositFragment> fragments) {
    logger.infofmt("RdeStagingReducer %s", key);

    // Normally this is done by BackendServlet but it's not present in MapReduceServlet.
    Security.addProvider(new BouncyCastleProvider());

    // Construct things that Dagger would inject if this wasn't serialized.
    Ghostryde ghostryde = new Ghostryde(ghostrydeBufferSize);
    PGPPublicKey stagingKey = PgpHelper.loadPublicKeyBytes(stagingKeyBytes);
    GcsUtils cloudStorage =
        new GcsUtils(createGcsService(RetryParams.getDefaultInstance()), gcsBufferSize);
    RdeCounter counter = new RdeCounter();

    // Determine some basic things about the deposit.
    final RdeMode mode = key.mode();
    final String tld = key.tld();
    final DateTime watermark = key.watermark();
    final int revision = key.revision().or(RdeRevision.getNextRevision(tld, watermark, mode));
    String id = RdeUtil.timestampToId(watermark);
    String prefix = RdeNamingUtils.makeRydeFilename(tld, watermark, mode, 1, revision);
    if (key.manual()) {
      checkState(key.directoryWithTrailingSlash().isPresent(), "Manual subdirectory not specified");
      prefix = "manual/" + key.directoryWithTrailingSlash().get() + prefix;
    }
    GcsFilename xmlFilename = new GcsFilename(bucket, prefix + ".xml.ghostryde");
    GcsFilename xmlLengthFilename = new GcsFilename(bucket, prefix + ".xml.length");
    GcsFilename reportFilename = new GcsFilename(bucket, prefix + "-report.xml.ghostryde");

    // These variables will be populated as we write the deposit XML and used for other files.
    boolean failed = false;
    long xmlLength;
    XjcRdeHeader header;

    // Write a gigantic XML file to GCS. We'll start by opening encrypted out/err file handles.
    logger.infofmt("Writing %s", xmlFilename);
    try (OutputStream gcsOutput = cloudStorage.openOutputStream(xmlFilename);
        Ghostryde.Encryptor encryptor = ghostryde.openEncryptor(gcsOutput, stagingKey);
        Ghostryde.Compressor kompressor = ghostryde.openCompressor(encryptor);
        Ghostryde.Output gOutput = ghostryde.openOutput(kompressor, prefix + ".xml", watermark);
        Writer output = new OutputStreamWriter(gOutput, UTF_8)) {

      // Output the top portion of the XML document.
      output.write(marshaller.makeHeader(id, watermark, RdeResourceType.getUris(mode), revision));

      // Output XML fragments emitted to us by RdeStagingMapper while counting them.
      while (fragments.hasNext()) {
        DepositFragment fragment = fragments.next();
        if (!fragment.xml().isEmpty()) {
          output.write(fragment.xml());
          counter.increment(fragment.type());
        }
        if (!fragment.error().isEmpty()) {
          failed = true;
          logger.severe(fragment.error());
        }
      }
      for (IdnTableEnum idn : IdnTableEnum.values()) {
        output.write(marshaller.marshalIdn(idn.getTable()));
        counter.increment(RdeResourceType.IDN);
      }

      // Output XML that says how many resources were emitted.
      header = counter.makeHeader(tld, mode);
      output.write(marshaller.marshalOrDie(new XjcRdeHeaderElement(header)));

      // Output the bottom of the XML document.
      output.write(marshaller.makeFooter());

      // And we're done! How many raw XML bytes did we write?
      output.flush();
      xmlLength = gOutput.getBytesWritten();
    } catch (IOException | PGPException e) {
      throw new RuntimeException(e);
    }

    // If an entity was broken, abort after writing as much logs/deposit data as possible.
    verify(!failed, "RDE staging failed for TLD %s", tld);

    // Write a file to GCS containing the byte length (ASCII) of the raw unencrypted XML.
    //
    // This is necessary because RdeUploadAction creates a tar file which requires that the length
    // be outputted. We don't want to have to decrypt the entire ghostryde file to determine the
    // length, so we just save it separately.
    logger.infofmt("Writing %s", xmlLengthFilename);
    try (OutputStream gcsOutput = cloudStorage.openOutputStream(xmlLengthFilename)) {
      gcsOutput.write(Long.toString(xmlLength).getBytes(US_ASCII));
    } catch (IOException e) {
      throw new RuntimeException(e);
    }

    // Write a tiny XML file to GCS containing some information about the deposit.
    //
    // This will be sent to ICANN once we're done uploading the big XML to the escrow provider.
    if (mode == RdeMode.FULL) {
      logger.infofmt("Writing %s", reportFilename);
      String innerName = prefix + "-report.xml";
      try (OutputStream gcsOutput = cloudStorage.openOutputStream(reportFilename);
          Ghostryde.Encryptor encryptor = ghostryde.openEncryptor(gcsOutput, stagingKey);
          Ghostryde.Compressor kompressor = ghostryde.openCompressor(encryptor);
          Ghostryde.Output output = ghostryde.openOutput(kompressor, innerName, watermark)) {
        counter.makeReport(id, watermark, header, revision).marshal(output, UTF_8);
      } catch (IOException | PGPException | XmlException e) {
        throw new RuntimeException(e);
      }
    }

    // Now that we're done, kick off RdeUploadAction and roll forward the cursor transactionally.
    if (key.manual()) {
      logger.info("Manual operation; not advancing cursor or enqueuing upload task");
      return;
    }
    ofy().transact(new VoidWork() {
      @Override
      public void vrun() {
        Registry registry = Registry.get(tld);
        DateTime position = getCursorTimeOrStartOfTime(
            ofy().load().key(Cursor.createKey(key.cursor().get(), registry)).now());
        checkState(key.interval().isPresent(), "Interval must be present");
        DateTime newPosition = key.watermark().plus(key.interval().get());
        if (!position.isBefore(newPosition)) {
          logger.warning("Cursor has already been rolled forward.");
          return;
        }
        verify(position.equals(key.watermark()),
            "Partial ordering of RDE deposits broken: %s %s", position, key);
        ofy().save().entity(Cursor.create(key.cursor().get(), newPosition, registry)).now();
        logger.infofmt("Rolled forward %s on %s cursor to %s", key.cursor(), tld, newPosition);
        RdeRevision.saveRevision(tld, watermark, mode, revision);
        if (mode == RdeMode.FULL) {
          taskEnqueuer.enqueue(getQueue("rde-upload"),
              withUrl(RdeUploadAction.PATH)
                  .param(RequestParameters.PARAM_TLD, tld));
        } else {
          taskEnqueuer.enqueue(getQueue("brda"),
              withUrl(BrdaCopyAction.PATH)
                  .param(RequestParameters.PARAM_TLD, tld)
                  .param(RdeModule.PARAM_WATERMARK, watermark.toString()));
        }
      }});
  }
}
