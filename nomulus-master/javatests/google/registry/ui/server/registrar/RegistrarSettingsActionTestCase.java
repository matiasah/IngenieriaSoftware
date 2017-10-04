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

package google.registry.ui.server.registrar;

import static google.registry.config.RegistryConfig.getGSuiteOutgoingEmailAddress;
import static google.registry.config.RegistryConfig.getGSuiteOutgoingEmailDisplayName;
import static google.registry.security.JsonHttpTestUtils.createJsonPayload;
import static google.registry.security.JsonHttpTestUtils.createJsonResponseSupplier;
import static google.registry.testing.DatastoreHelper.loadRegistrar;
import static google.registry.util.ResourceUtils.readResourceUtf8;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.appengine.api.modules.ModulesService;
import com.google.appengine.api.users.User;
import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import google.registry.export.sheet.SyncRegistrarsSheetAction;
import google.registry.model.ofy.Ofy;
import google.registry.request.JsonActionRunner;
import google.registry.request.JsonResponse;
import google.registry.request.ResponseImpl;
import google.registry.request.auth.AuthLevel;
import google.registry.request.auth.AuthResult;
import google.registry.request.auth.UserAuthInfo;
import google.registry.testing.AppEngineRule;
import google.registry.testing.FakeClock;
import google.registry.testing.InjectRule;
import google.registry.util.SendEmailService;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Map;
import java.util.Properties;
import javax.mail.Message;
import javax.mail.Session;
import javax.mail.internet.MimeMessage;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.joda.time.DateTime;
import org.json.simple.JSONValue;
import org.json.simple.parser.ParseException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Base class for tests using {@link RegistrarSettingsAction}. */
@RunWith(JUnit4.class)
public class RegistrarSettingsActionTestCase {

  static final String CLIENT_ID = "TheRegistrar";

  @Rule
  public final AppEngineRule appEngine = AppEngineRule.builder()
      .withDatastore()
      .withTaskQueue()
      .build();

  @Rule
  public final InjectRule inject = new InjectRule();

  final HttpServletRequest req = mock(HttpServletRequest.class);
  final HttpServletResponse rsp = mock(HttpServletResponse.class);
  final SendEmailService emailService = mock(SendEmailService.class);
  final ModulesService modulesService = mock(ModulesService.class);
  final SessionUtils sessionUtils = mock(SessionUtils.class);
  final User user = new User("user", "gmail.com");

  Message message;

  final RegistrarSettingsAction action = new RegistrarSettingsAction();
  final StringWriter writer = new StringWriter();
  final Supplier<Map<String, Object>> json = createJsonResponseSupplier(writer);
  final FakeClock clock = new FakeClock(DateTime.parse("2014-01-01T00:00:00Z"));

  @Before
  public void setUp() throws Exception {
    action.request = req;
    action.sessionUtils = sessionUtils;
    action.authResult = AuthResult.create(AuthLevel.USER, UserAuthInfo.create(user, false));
    action.jsonActionRunner = new JsonActionRunner(
        ImmutableMap.<String, Object>of(), new JsonResponse(new ResponseImpl(rsp)));
    action.registrarChangesNotificationEmailAddresses = ImmutableList.of(
        "notification@test.example", "notification2@test.example");
    action.sendEmailUtils =
        new SendEmailUtils(getGSuiteOutgoingEmailAddress(), getGSuiteOutgoingEmailDisplayName());
    inject.setStaticField(Ofy.class, "clock", clock);
    inject.setStaticField(SendEmailUtils.class, "emailService", emailService);
    inject.setStaticField(SyncRegistrarsSheetAction.class, "modulesService", modulesService);
    message = new MimeMessage(Session.getDefaultInstance(new Properties(), null));
    when(emailService.createMessage()).thenReturn(message);
    when(req.getMethod()).thenReturn("POST");
    when(rsp.getWriter()).thenReturn(new PrintWriter(writer));
    when(req.getContentType()).thenReturn("application/json");
    when(req.getReader()).thenReturn(createJsonPayload(ImmutableMap.of("op", "read")));
    when(sessionUtils.getRegistrarForAuthResult(req, action.authResult))
        .thenReturn(loadRegistrar(CLIENT_ID));
    when(modulesService.getVersionHostname("backend", null)).thenReturn("backend.hostname");
  }

  protected Map<String, Object> readJsonFromFile(String filename) {
    String contents = readResourceUtf8(getClass(), filename);
    try {
      @SuppressWarnings("unchecked")
      Map<String, Object> json = (Map<String, Object>) JSONValue.parseWithException(contents);
      return json;
    } catch (ParseException ex) {
      throw new RuntimeException(ex);
    }
  }
}
