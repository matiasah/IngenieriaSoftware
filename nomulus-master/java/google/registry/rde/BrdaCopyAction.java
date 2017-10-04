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

import static google.registry.model.rde.RdeMode.THIN;
import static google.registry.request.Action.Method.POST;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.appengine.tools.cloudstorage.GcsFilename;
import com.google.common.io.ByteStreams;
import google.registry.config.RegistryConfig.Config;
import google.registry.gcs.GcsUtils;
import google.registry.keyring.api.KeyModule.Key;
import google.registry.model.rde.RdeNamingUtils;
import google.registry.request.Action;
import google.registry.request.Parameter;
import google.registry.request.RequestParameters;
import google.registry.request.auth.Auth;
import google.registry.util.FormattingLogger;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import javax.inject.Inject;
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPKeyPair;
import org.bouncycastle.openpgp.PGPPrivateKey;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.joda.time.DateTime;

/**
 * Action that re-encrypts a BRDA escrow deposit and puts it into the upload bucket.
 *
 * <p>This action is run by the mapreduce for each BRDA staging file it generates. The staging file
 * is encrypted with our internal {@link Ghostryde} encryption. We then re-encrypt it as a RyDE
 * file, which is what the third-party escrow provider understands.
 *
 * <p>Then we put the RyDE file (along with our digital signature) into the configured BRDA bucket.
 * This bucket is special because a separate script will rsync it to the third party escrow provider
 * SFTP server. This is why the internal staging files are stored in the separate RDE bucket.
 *
 * @see <a href="http://newgtlds.icann.org/en/applicants/agb/agreement-approved-09jan14-en.htm">Registry Agreement</a>
 */
@Action(
  path = BrdaCopyAction.PATH,
  method = POST,
  automaticallyPrintOk = true,
  auth = Auth.AUTH_INTERNAL_ONLY
)
public final class BrdaCopyAction implements Runnable {

  static final String PATH = "/_dr/task/brdaCopy";

  private static final FormattingLogger logger = FormattingLogger.getLoggerForCallerClass();

  @Inject GcsUtils gcsUtils;
  @Inject Ghostryde ghostryde;
  @Inject RydePgpCompressionOutputStreamFactory pgpCompressionFactory;
  @Inject RydePgpFileOutputStreamFactory pgpFileFactory;
  @Inject RydePgpEncryptionOutputStreamFactory pgpEncryptionFactory;
  @Inject RydePgpSigningOutputStreamFactory pgpSigningFactory;
  @Inject RydeTarOutputStreamFactory tarFactory;
  @Inject @Config("brdaBucket") String brdaBucket;
  @Inject @Config("rdeBucket") String stagingBucket;
  @Inject @Parameter(RequestParameters.PARAM_TLD) String tld;
  @Inject @Parameter(RdeModule.PARAM_WATERMARK) DateTime watermark;
  @Inject @Key("brdaReceiverKey") PGPPublicKey receiverKey;
  @Inject @Key("brdaSigningKey") PGPKeyPair signingKey;
  @Inject @Key("rdeStagingDecryptionKey") PGPPrivateKey stagingDecryptionKey;
  @Inject BrdaCopyAction() {}

  @Override
  public void run() {
    try {
      copyAsRyde();
    } catch (IOException | PGPException e) {
      throw new RuntimeException(e);
    }
  }

  private void copyAsRyde() throws IOException, PGPException {
    String prefix = RdeNamingUtils.makeRydeFilename(tld, watermark, THIN, 1, 0);
    GcsFilename xmlFilename = new GcsFilename(stagingBucket, prefix + ".xml.ghostryde");
    GcsFilename xmlLengthFilename = new GcsFilename(stagingBucket, prefix + ".xml.length");
    GcsFilename rydeFile = new GcsFilename(brdaBucket, prefix + ".ryde");
    GcsFilename sigFile = new GcsFilename(brdaBucket, prefix + ".sig");

    long xmlLength = readXmlLength(xmlLengthFilename);

    logger.infofmt("Writing %s", rydeFile);
    byte[] signature;
    try (InputStream gcsInput = gcsUtils.openInputStream(xmlFilename);
        Ghostryde.Decryptor decryptor = ghostryde.openDecryptor(gcsInput, stagingDecryptionKey);
        Ghostryde.Decompressor decompressor = ghostryde.openDecompressor(decryptor);
        Ghostryde.Input ghostInput = ghostryde.openInput(decompressor);
        BufferedInputStream xmlInput = new BufferedInputStream(ghostInput);
        OutputStream gcsOutput = gcsUtils.openOutputStream(rydeFile);
        RydePgpSigningOutputStream signLayer = pgpSigningFactory.create(gcsOutput, signingKey)) {
      try (OutputStream encryptLayer = pgpEncryptionFactory.create(signLayer, receiverKey);
          OutputStream compressLayer = pgpCompressionFactory.create(encryptLayer);
          OutputStream fileLayer = pgpFileFactory.create(compressLayer, watermark, prefix + ".tar");
          OutputStream tarLayer =
              tarFactory.create(fileLayer, xmlLength, watermark, prefix + ".xml")) {
        ByteStreams.copy(xmlInput, tarLayer);
      }
      signature = signLayer.getSignature();
    }

    logger.infofmt("Writing %s", sigFile);
    try (OutputStream gcsOutput = gcsUtils.openOutputStream(sigFile)) {
      gcsOutput.write(signature);
    }
  }

  /** Reads the contents of a file from Cloud Storage that contains nothing but an integer. */
  private long readXmlLength(GcsFilename xmlLengthFilename) throws IOException {
    try (InputStream input = gcsUtils.openInputStream(xmlLengthFilename)) {
      return Long.parseLong(new String(ByteStreams.toByteArray(input), UTF_8).trim());
    }
  }
}
