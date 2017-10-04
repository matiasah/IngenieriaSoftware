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

package google.registry.export;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static google.registry.export.ExportReservedTermsAction.EXPORT_MIME_TYPE;
import static google.registry.export.ExportReservedTermsAction.RESERVED_TERMS_FILENAME;
import static google.registry.testing.DatastoreHelper.createTld;
import static google.registry.testing.DatastoreHelper.persistReservedList;
import static google.registry.testing.DatastoreHelper.persistResource;
import static java.nio.charset.StandardCharsets.UTF_8;
import static javax.servlet.http.HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
import static javax.servlet.http.HttpServletResponse.SC_OK;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableSet;
import com.google.common.net.MediaType;
import google.registry.model.registry.Registry;
import google.registry.model.registry.label.ReservedList;
import google.registry.request.Response;
import google.registry.storage.drive.DriveConnection;
import google.registry.testing.AppEngineRule;
import java.io.IOException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link ExportReservedTermsAction}. */
@RunWith(JUnit4.class)
public class ExportReservedTermsActionTest {

  @Rule
  public final AppEngineRule appEngine = AppEngineRule.builder()
      .withDatastore()
      .build();

  private final DriveConnection driveConnection = mock(DriveConnection.class);
  private final Response response = mock(Response.class);

  private void runAction(String tld) {
    ExportReservedTermsAction action = new ExportReservedTermsAction();
    action.response = response;
    action.driveConnection = driveConnection;
    action.exportUtils = new ExportUtils("This is a disclaimer.\n");
    action.tld = tld;
    action.run();
  }

  @Before
  public void init() throws Exception {
    ReservedList rl = persistReservedList(
        "tld-reserved",
        "lol,FULLY_BLOCKED",
        "cat,FULLY_BLOCKED");
    createTld("tld");
    persistResource(Registry.get("tld").asBuilder()
        .setReservedLists(rl)
        .setDriveFolderId("brouhaha").build());
    when(driveConnection.createOrUpdateFile(
        anyString(),
        any(MediaType.class),
        anyString(),
        any(byte[].class))).thenReturn("1001");
  }

  @Test
  public void test_uploadFileToDrive_succeeds() throws Exception {
    runAction("tld");
    byte[] expected =
        ("This is a disclaimer.\ncat\nlol\n")
        .getBytes(UTF_8);
    verify(driveConnection)
        .createOrUpdateFile(RESERVED_TERMS_FILENAME, EXPORT_MIME_TYPE, "brouhaha", expected);
    verify(response).setStatus(SC_OK);
    verify(response).setPayload("1001");
  }

  @Test
  public void test_uploadFileToDrive_doesNothingIfReservedListsNotConfigured() throws Exception {
    persistResource(Registry.get("tld").asBuilder()
        .setReservedLists(ImmutableSet.<ReservedList>of())
        .setDriveFolderId(null)
        .build());
    runAction("tld");
    verify(response).setStatus(SC_OK);
    verify(response).setPayload("No reserved lists configured");
  }

  @Test
  public void test_uploadFileToDrive_doesNothingWhenDriveFolderIdIsNull() throws Exception {
    persistResource(Registry.get("tld").asBuilder().setDriveFolderId(null).build());
    runAction("tld");
    verify(response).setStatus(SC_OK);
    verify(response)
        .setPayload("Skipping export because no Drive folder is associated with this TLD");
  }

  @Test
  public void test_uploadFileToDrive_failsWhenDriveCannotBeReached() throws Exception {
    when(driveConnection.createOrUpdateFile(
        anyString(),
        any(MediaType.class),
        anyString(),
        any(byte[].class))).thenThrow(new IOException("errorMessage"));
    try {
      runAction("tld");
      assertWithMessage("Expected RuntimeException to be thrown").fail();
    } catch (RuntimeException e) {
      verify(response).setStatus(SC_INTERNAL_SERVER_ERROR);
      assertThat(e).hasCauseThat().hasMessageThat().isEqualTo("errorMessage");
    }
  }

  @Test
  public void test_uploadFileToDrive_failsWhenTldDoesntExist() throws Exception {
    try {
      runAction("fakeTld");
      assertWithMessage("Expected RuntimeException to be thrown").fail();
    } catch (RuntimeException e) {
      verify(response).setStatus(SC_INTERNAL_SERVER_ERROR);
      assertThat(e)
          .hasCauseThat()
          .hasMessageThat()
          .isEqualTo("No registry object found for fakeTld");
    }
  }
}
