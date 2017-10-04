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

package google.registry.tools.server;

import static com.google.appengine.tools.cloudstorage.GcsServiceFactory.createGcsService;
import static com.google.common.truth.Truth.assertThat;
import static google.registry.testing.DatastoreHelper.createTld;
import static google.registry.testing.DatastoreHelper.newDomainResource;
import static google.registry.testing.DatastoreHelper.newHostResource;
import static google.registry.testing.DatastoreHelper.persistActiveContact;
import static google.registry.testing.DatastoreHelper.persistActiveDomain;
import static google.registry.testing.DatastoreHelper.persistActiveDomainApplication;
import static google.registry.testing.DatastoreHelper.persistActiveHost;
import static google.registry.testing.DatastoreHelper.persistResource;
import static google.registry.testing.GcsTestingUtils.readGcsFile;
import static google.registry.util.ResourceUtils.readResourceUtf8;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.joda.time.Duration.standardDays;

import com.google.appengine.tools.cloudstorage.GcsFilename;
import com.google.appengine.tools.cloudstorage.GcsService;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.googlecode.objectify.Key;
import google.registry.model.domain.secdns.DelegationSignerData;
import google.registry.model.eppcommon.StatusValue;
import google.registry.model.host.HostResource;
import google.registry.testing.FakeClock;
import google.registry.testing.mapreduce.MapreduceTestCase;
import java.net.InetAddress;
import java.util.Map;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.Duration;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link GenerateZoneFilesAction}.*/
@RunWith(JUnit4.class)
public class GenerateZoneFilesActionTest extends MapreduceTestCase<GenerateZoneFilesAction> {

  private final GcsService gcsService = createGcsService();

  @Test
  public void testGenerate() throws Exception {
    DateTime now = DateTime.now(DateTimeZone.UTC).withTimeAtStartOfDay();

    createTld("tld");
    createTld("com");

    ImmutableSet<InetAddress> ips =
        ImmutableSet.of(InetAddress.getByName("127.0.0.1"), InetAddress.getByName("::1"));
    HostResource host1 =
        persistResource(newHostResource("ns.foo.tld").asBuilder().addInetAddresses(ips).build());
    HostResource host2 =
        persistResource(newHostResource("ns.bar.tld").asBuilder().addInetAddresses(ips).build());

    ImmutableSet<Key<HostResource>> nameservers =
        ImmutableSet.of(Key.create(host1), Key.create(host2));
    // This domain will have glue records, because it has a subordinate host which is its own
    // nameserver. None of the other domains should have glue records, because their nameservers are
    // subordinate to different domains.
    persistResource(newDomainResource("bar.tld").asBuilder()
        .addNameservers(nameservers)
        .addSubordinateHost("ns.bar.tld")
        .build());
    persistResource(newDomainResource("foo.tld").asBuilder()
        .addSubordinateHost("ns.foo.tld")
        .build());
    persistResource(newDomainResource("ns-and-ds.tld").asBuilder()
        .addNameservers(nameservers)
        .setDsData(ImmutableSet.of(DelegationSignerData.create(1, 2, 3, new byte[] {0, 1, 2})))
        .build());
    persistResource(newDomainResource("ns-only.tld").asBuilder()
        .addNameservers(nameservers)
        .build());
    persistResource(newDomainResource("ns-only-client-hold.tld").asBuilder()
        .addNameservers(nameservers)
        .setStatusValues(ImmutableSet.of(StatusValue.CLIENT_HOLD))
        .build());
    persistResource(newDomainResource("ns-only-pending-delete.tld").asBuilder()
        .addNameservers(nameservers)
        .setStatusValues(ImmutableSet.of(StatusValue.PENDING_DELETE))
        .build());
    persistResource(newDomainResource("ns-only-server-hold.tld").asBuilder()
        .addNameservers(nameservers)
        .setStatusValues(ImmutableSet.of(StatusValue.SERVER_HOLD))
        .build());
    // These should be ignored; contact and applications aren't in DNS, hosts need to be from the
    // same tld and have ip addresses, and domains need to be from the same tld and have hosts (even
    // in the case where domains contain DS data).
    persistResource(newDomainResource("ds-only.tld").asBuilder()
        .setDsData(ImmutableSet.of(DelegationSignerData.create(1, 2, 3, new byte[] {0, 1, 2})))
        .build());
    persistActiveContact("ignored_contact");
    persistActiveDomainApplication("ignored_application.tld");
    persistActiveHost("ignored.host.tld");  // No ips.
    persistActiveDomain("ignored_domain.tld");  // No hosts or DS data.
    persistResource(newHostResource("ignored.foo.com").asBuilder().addInetAddresses(ips).build());
    persistResource(newDomainResource("ignored.com")
        .asBuilder()
        .addNameservers(nameservers)
        .setDsData(ImmutableSet.of(DelegationSignerData.create(1, 2, 3, new byte[] {0, 1, 2})))
        .build());

    GenerateZoneFilesAction action = new GenerateZoneFilesAction();
    action.mrRunner = makeDefaultRunner();
    action.bucket = "zonefiles-bucket";
    action.gcsBufferSize = 123;
    action.datastoreRetention = standardDays(29);
    action.dnsDefaultATtl = Duration.standardSeconds(11);
    action.dnsDefaultNsTtl = Duration.standardSeconds(222);
    action.dnsDefaultDsTtl = Duration.standardSeconds(3333);
    action.clock = new FakeClock(now.plusMinutes(2));  // Move past the actions' 2 minute check.

    Map<String, Object> response = action.handleJsonRequest(ImmutableMap.<String, Object>of(
        "tlds", ImmutableList.of("tld"),
        "exportTime", now));
    assertThat(response).containsEntry(
        "filenames",
        ImmutableList.of("gs://zonefiles-bucket/tld-" + now + ".zone"));

    executeTasksUntilEmpty("mapreduce");

    GcsFilename gcsFilename =
        new GcsFilename("zonefiles-bucket", String.format("tld-%s.zone", now));
    String generatedFile = new String(readGcsFile(gcsService, gcsFilename), UTF_8);
    // The generated file contains spaces and tabs, but the golden file contains only spaces, as
    // files with literal tabs irritate our build tools.
    Splitter splitter = Splitter.on('\n').omitEmptyStrings();
    Iterable<String> generatedFileLines = splitter.split(generatedFile.replaceAll("\t", " "));
    Iterable<String> goldenFileLines =
        splitter.split(readResourceUtf8(getClass(), "testdata/tld.zone"));
    // The first line needs to be the same as the golden file.
    assertThat(generatedFileLines.iterator().next()).isEqualTo(goldenFileLines.iterator().next());
    // The remaining lines can be in any order.
    assertThat(generatedFileLines).containsExactlyElementsIn(goldenFileLines);
  }
}
