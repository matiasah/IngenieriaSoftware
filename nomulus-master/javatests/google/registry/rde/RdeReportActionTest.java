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

import static com.google.appengine.api.urlfetch.HTTPMethod.PUT;
import static com.google.common.net.MediaType.PLAIN_TEXT_UTF_8;
import static com.google.common.truth.Truth.assertThat;
import static google.registry.model.ofy.ObjectifyService.ofy;
import static google.registry.testing.DatastoreHelper.createTld;
import static google.registry.testing.DatastoreHelper.persistResource;
import static google.registry.testing.GcsTestingUtils.writeGcsFile;
import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static javax.servlet.http.HttpServletResponse.SC_OK;
import static org.joda.time.DateTimeZone.UTC;
import static org.joda.time.Duration.standardDays;
import static org.joda.time.Duration.standardSeconds;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.google.appengine.api.urlfetch.HTTPHeader;
import com.google.appengine.api.urlfetch.HTTPRequest;
import com.google.appengine.api.urlfetch.HTTPResponse;
import com.google.appengine.api.urlfetch.URLFetchService;
import com.google.appengine.tools.cloudstorage.GcsFilename;
import com.google.appengine.tools.cloudstorage.GcsService;
import com.google.appengine.tools.cloudstorage.GcsServiceFactory;
import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.ByteSource;
import google.registry.gcs.GcsUtils;
import google.registry.model.common.Cursor;
import google.registry.model.common.Cursor.CursorType;
import google.registry.model.registry.Registry;
import google.registry.request.HttpException.InternalServerErrorException;
import google.registry.testing.AppEngineRule;
import google.registry.testing.BouncyCastleProviderRule;
import google.registry.testing.ExceptionRule;
import google.registry.testing.FakeClock;
import google.registry.testing.FakeKeyringModule;
import google.registry.testing.FakeResponse;
import google.registry.testing.FakeSleeper;
import google.registry.util.Retrier;
import google.registry.xjc.XjcXmlTransformer;
import google.registry.xjc.rdereport.XjcRdeReportReport;
import google.registry.xml.XmlException;
import java.io.ByteArrayInputStream;
import java.net.SocketTimeoutException;
import java.util.Map;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;

/** Unit tests for {@link RdeReportAction}. */
@RunWith(JUnit4.class)
public class RdeReportActionTest {

  private static final ByteSource REPORT_XML = RdeTestData.get("report.xml");
  private static final ByteSource IIRDEA_BAD_XML = RdeTestData.get("iirdea_bad.xml");
  private static final ByteSource IIRDEA_GOOD_XML = RdeTestData.get("iirdea_good.xml");

  @Rule
  public final ExceptionRule thrown = new ExceptionRule();

  @Rule
  public final BouncyCastleProviderRule bouncy = new BouncyCastleProviderRule();

  @Rule
  public final AppEngineRule appEngine = AppEngineRule.builder()
      .withDatastore()
      .build();

  private final FakeResponse response = new FakeResponse();
  private final EscrowTaskRunner runner = mock(EscrowTaskRunner.class);
  private final URLFetchService urlFetchService = mock(URLFetchService.class);
  private final ArgumentCaptor<HTTPRequest> request = ArgumentCaptor.forClass(HTTPRequest.class);
  private final HTTPResponse httpResponse = mock(HTTPResponse.class);

  private final GcsService gcsService = GcsServiceFactory.createGcsService();
  private final GcsFilename reportFile =
      new GcsFilename("tub", "test_2006-06-06_full_S1_R0-report.xml.ghostryde");

  private RdeReportAction createAction() {
    RdeReporter reporter = new RdeReporter();
    reporter.reportUrlPrefix = "https://rde-report.example";
    reporter.urlFetchService = urlFetchService;
    reporter.password = "foo";
    reporter.retrier = new Retrier(new FakeSleeper(new FakeClock()), 3);
    RdeReportAction action = new RdeReportAction();
    action.gcsUtils = new GcsUtils(gcsService, 1024);
    action.ghostryde = new Ghostryde(1024);
    action.response = response;
    action.bucket = "tub";
    action.tld = "test";
    action.interval = standardDays(1);
    action.reporter = reporter;
    action.timeout = standardSeconds(30);
    action.stagingDecryptionKey = new FakeKeyringModule().get().getRdeStagingDecryptionKey();
    action.runner = runner;
    return action;
  }

  @Before
  public void before() throws Exception {
    PGPPublicKey encryptKey = new FakeKeyringModule().get().getRdeStagingEncryptionKey();
    createTld("test");
    persistResource(
        Cursor.create(CursorType.RDE_REPORT, DateTime.parse("2006-06-06TZ"), Registry.get("test")));
    persistResource(
        Cursor.create(CursorType.RDE_UPLOAD, DateTime.parse("2006-06-07TZ"), Registry.get("test")));
    writeGcsFile(
        gcsService,
        reportFile,
        Ghostryde.encode(REPORT_XML.read(), encryptKey, "darkside.xml", DateTime.now(UTC)));
  }

  @Test
  public void testRun() throws Exception {
    createTld("lol");
    RdeReportAction action = createAction();
    action.tld = "lol";
    action.run();
    verify(runner).lockRunAndRollForward(
        action, Registry.get("lol"), standardSeconds(30), CursorType.RDE_REPORT, standardDays(1));
    verifyNoMoreInteractions(runner);
  }

  @Test
  public void testRunWithLock() throws Exception {
    when(httpResponse.getResponseCode()).thenReturn(SC_OK);
    when(httpResponse.getContent()).thenReturn(IIRDEA_GOOD_XML.read());
    when(urlFetchService.fetch(request.capture())).thenReturn(httpResponse);
    createAction().runWithLock(loadRdeReportCursor());
    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(response.getContentType()).isEqualTo(PLAIN_TEXT_UTF_8);
    assertThat(response.getPayload()).isEqualTo("OK test 2006-06-06T00:00:00.000Z\n");

    // Verify the HTTP request was correct.
    assertThat(request.getValue().getMethod()).isSameAs(PUT);
    assertThat(request.getValue().getURL().getProtocol()).isEqualTo("https");
    assertThat(request.getValue().getURL().getPath()).endsWith("/test/20101017001");
    Map<String, String> headers = mapifyHeaders(request.getValue().getHeaders());
    assertThat(headers).containsEntry("CONTENT_TYPE", "text/xml");
    assertThat(headers)
        .containsEntry("AUTHORIZATION", "Basic dGVzdF9yeTpmb28=");

    // Verify the payload XML was the same as what's in testdata/report.xml.
    XjcRdeReportReport report = parseReport(request.getValue().getPayload());
    assertThat(report.getId()).isEqualTo("20101017001");
    assertThat(report.getCrDate()).isEqualTo(DateTime.parse("2010-10-17T00:15:00.0Z"));
    assertThat(report.getWatermark()).isEqualTo(DateTime.parse("2010-10-17T00:00:00Z"));
  }

  @Test
  public void testRunWithLock_badRequest_throws500WithErrorInfo() throws Exception {
    when(httpResponse.getResponseCode()).thenReturn(SC_BAD_REQUEST);
    when(httpResponse.getContent()).thenReturn(IIRDEA_BAD_XML.read());
    when(urlFetchService.fetch(request.capture())).thenReturn(httpResponse);
    thrown.expect(InternalServerErrorException.class, "The structure of the report is invalid.");
    createAction().runWithLock(loadRdeReportCursor());
  }

  @Test
  public void testRunWithLock_fetchFailed_throwsRuntimeException() throws Exception {
    class ExpectedException extends RuntimeException {}
    when(urlFetchService.fetch(any(HTTPRequest.class))).thenThrow(new ExpectedException());
    thrown.expect(ExpectedException.class);
    createAction().runWithLock(loadRdeReportCursor());
  }

  @Test
  public void testRunWithLock_socketTimeout_doesRetry() throws Exception {
    when(httpResponse.getResponseCode()).thenReturn(SC_OK);
    when(httpResponse.getContent()).thenReturn(IIRDEA_GOOD_XML.read());
    when(urlFetchService.fetch(request.capture()))
        .thenThrow(new SocketTimeoutException())
        .thenReturn(httpResponse);
    createAction().runWithLock(loadRdeReportCursor());
    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(response.getContentType()).isEqualTo(PLAIN_TEXT_UTF_8);
    assertThat(response.getPayload()).isEqualTo("OK test 2006-06-06T00:00:00.000Z\n");
  }

  private DateTime loadRdeReportCursor() {
    return ofy()
        .load()
        .key(Cursor.createKey(CursorType.RDE_REPORT, Registry.get("test")))
        .now()
        .getCursorTime();
  }

  private static ImmutableMap<String, String> mapifyHeaders(Iterable<HTTPHeader> headers) {
    ImmutableMap.Builder<String, String> builder = new ImmutableMap.Builder<>();
    for (HTTPHeader header : headers) {
      builder.put(Ascii.toUpperCase(header.getName().replace('-', '_')), header.getValue());
    }
    return builder.build();
  }

  private static XjcRdeReportReport parseReport(byte[] data) {
    try {
      return XjcXmlTransformer.unmarshal(XjcRdeReportReport.class, new ByteArrayInputStream(data));
    } catch (XmlException e) {
      throw new RuntimeException(e);
    }
  }
}
