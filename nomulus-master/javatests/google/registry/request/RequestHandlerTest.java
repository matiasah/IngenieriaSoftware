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

package google.registry.request;

import static com.google.common.truth.Truth.assertThat;
import static google.registry.request.Action.Method.GET;
import static google.registry.request.Action.Method.POST;
import static google.registry.request.auth.Auth.AUTH_INTERNAL_OR_ADMIN;
import static google.registry.request.auth.Auth.AUTH_PUBLIC;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import com.google.appengine.api.users.User;
import com.google.common.base.Optional;
import com.google.common.testing.NullPointerTester;
import google.registry.request.HttpException.ServiceUnavailableException;
import google.registry.request.auth.AuthLevel;
import google.registry.request.auth.AuthResult;
import google.registry.request.auth.RequestAuthenticator;
import google.registry.request.auth.UserAuthInfo;
import google.registry.testing.AppEngineRule;
import google.registry.testing.Providers;
import google.registry.testing.UserInfo;
import java.io.PrintWriter;
import java.io.StringWriter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link RequestHandler}. */
@RunWith(JUnit4.class)
public final class RequestHandlerTest {

  @Rule
  public final AppEngineRule appEngine =
      AppEngineRule.builder()
          .withDatastore()
          .withUserService(UserInfo.create("test@example.com", "test@example.com"))
          .build();

  @Action(
    path = "/bumblebee",
    method = {GET, POST},
    isPrefix = true,
    auth = AUTH_PUBLIC
  )
  public static class BumblebeeTask implements Runnable {
    @Override
    public void run() {}
  }

  @Action(
    path = "/sloth",
    method = POST,
    automaticallyPrintOk = true,
    auth = AUTH_PUBLIC
  )
  public static class SlothTask implements Runnable {
    @Override
    public void run() {}
  }

  @Action(
    path = "/safe-sloth",
    method = {GET, POST},
    auth = AUTH_PUBLIC
  )
  public static class SafeSlothTask implements Runnable {
    @Override
    public void run() {}
  }

  @Action(path = "/fail", auth = AUTH_PUBLIC)
  public static final class FailTask implements Runnable {
    @Override
    public void run() {
      throw new ServiceUnavailableException("Set sail for fail");
    }
  }

  @Action(path = "/failAtConstruction", auth = AUTH_PUBLIC)
  public static final class FailAtConstructionTask implements Runnable {
    public FailAtConstructionTask() {
      throw new ServiceUnavailableException("Fail at construction");
    }

    @Override
    public void run() {
      throw new AssertionError("should not get here");
    }
  }

  class AuthBase implements Runnable {
    private final AuthResult authResult;

    AuthBase(AuthResult authResult) {
      this.authResult = authResult;
    }

    @Override
    public void run() {
      providedAuthResult = authResult;
    }
  }

  @Action(
    path = "/auth/none",
    auth = AUTH_PUBLIC,
    method = Action.Method.GET
  )
  public class AuthNoneAction extends AuthBase {
    AuthNoneAction(AuthResult authResult) {
      super(authResult);
    }
  }

  @Action(
      path = "/auth/adminUser",
      auth = AUTH_INTERNAL_OR_ADMIN,
      method = Action.Method.GET)
  public class AuthAdminUserAction extends AuthBase {
    AuthAdminUserAction(AuthResult authResult) {
      super(authResult);
    }
  }

  public class Component {

    private RequestModule requestModule = null;

    public RequestModule getRequestModule() {
      return requestModule;
    }

    public void setRequestModule(RequestModule requestModule) {
      this.requestModule = requestModule;
    }

    public BumblebeeTask bumblebeeTask() {
      return bumblebeeTask;
    }

    public SlothTask slothTask() {
      return slothTask;
    }

    public SafeSlothTask safeSlothTask() {
      return safeSlothTask;
    }

    public FailTask failTask() {
      return new FailTask();
    }

    public FailAtConstructionTask failAtConstructionTask() {
      return new FailAtConstructionTask();
    }

    public AuthNoneAction authNoneAction() {
      return new AuthNoneAction(component.getRequestModule().provideAuthResult());
    }

    public AuthAdminUserAction authAdminUserAction() {
      return new AuthAdminUserAction(component.getRequestModule().provideAuthResult());
    }
  }

  /** Fake Builder for the fake component above to satisfy RequestHandler expectations. */
  public abstract class Builder implements RequestComponentBuilder<Component> {
    @Override
    public Builder requestModule(RequestModule requestModule) {
      component.setRequestModule(requestModule);
      return this;
    }
  }

  private final HttpServletRequest req = mock(HttpServletRequest.class);
  private final HttpServletResponse rsp = mock(HttpServletResponse.class);
  private final BumblebeeTask bumblebeeTask = mock(BumblebeeTask.class);
  private final SlothTask slothTask = mock(SlothTask.class);
  private final SafeSlothTask safeSlothTask = mock(SafeSlothTask.class);
  private final RequestAuthenticator requestAuthenticator = mock(RequestAuthenticator.class);

  private final Component component = new Component();
  private final StringWriter httpOutput = new StringWriter();
  private RequestHandler<Component> handler;
  private AuthResult providedAuthResult = null;
  private final User testUser = new User("test@example.com", "test@example.com");

  @Before
  public void before() throws Exception {
    // Initialize here, not inline, so that we pick up the mocked UserService.
    handler = RequestHandler.<Component>createForTest(
        Component.class,
        Providers.<Builder>of(new Builder() {
          @Override
          public Component build() {
            // Use a fake Builder that returns the single component instance that uses the mocks.
            return component;
          }
        }),
        requestAuthenticator);
    when(rsp.getWriter()).thenReturn(new PrintWriter(httpOutput));
  }

  @After
  public void after() throws Exception {
    verifyNoMoreInteractions(rsp, bumblebeeTask, slothTask, safeSlothTask);
  }

  @Test
  public void testHandleRequest_normalRequest_works() throws Exception {
    when(req.getMethod()).thenReturn("GET");
    when(req.getRequestURI()).thenReturn("/bumblebee");
    when(requestAuthenticator.authorize(AUTH_PUBLIC.authSettings(), req))
        .thenReturn(Optional.of(AuthResult.create(AuthLevel.NONE)));

    handler.handleRequest(req, rsp);

    verifyZeroInteractions(rsp);
    verify(bumblebeeTask).run();
  }

  @Test
  public void testHandleRequest_multipleMethodMappings_works() throws Exception {
    when(req.getMethod()).thenReturn("POST");
    when(req.getRequestURI()).thenReturn("/bumblebee");
    when(requestAuthenticator.authorize(AUTH_PUBLIC.authSettings(), req))
        .thenReturn(Optional.of(AuthResult.create(AuthLevel.NONE)));

    handler.handleRequest(req, rsp);

    verify(bumblebeeTask).run();
  }

  @Test
  public void testHandleRequest_prefixEnabled_subpathsWork() throws Exception {
    when(req.getMethod()).thenReturn("GET");
    when(req.getRequestURI()).thenReturn("/bumblebee/hive");
    when(requestAuthenticator.authorize(AUTH_PUBLIC.authSettings(), req))
        .thenReturn(Optional.of(AuthResult.create(AuthLevel.NONE)));

    handler.handleRequest(req, rsp);

    verify(bumblebeeTask).run();
  }

  @Test
  public void testHandleRequest_taskHasAutoPrintOk_printsOk() throws Exception {
    when(req.getMethod()).thenReturn("POST");
    when(req.getRequestURI()).thenReturn("/sloth");
    when(requestAuthenticator.authorize(AUTH_PUBLIC.authSettings(), req))
        .thenReturn(Optional.of(AuthResult.create(AuthLevel.NONE)));

    handler.handleRequest(req, rsp);

    verify(slothTask).run();
    verify(rsp).setContentType("text/plain; charset=utf-8");
    verify(rsp).getWriter();
    assertThat(httpOutput.toString()).isEqualTo("OK\n");
  }

  @Test
  public void testHandleRequest_prefixDisabled_subpathsReturn404NotFound() throws Exception {
    when(req.getMethod()).thenReturn("POST");
    when(req.getRequestURI()).thenReturn("/sloth/nest");
    when(requestAuthenticator.authorize(AUTH_PUBLIC.authSettings(), req))
        .thenReturn(Optional.of(AuthResult.create(AuthLevel.NONE)));

    handler.handleRequest(req, rsp);

    verify(rsp).sendError(404);
  }

  @Test
  public void testHandleRequest_taskThrowsHttpException_getsHandledByHandler() throws Exception {
    when(req.getMethod()).thenReturn("GET");
    when(req.getRequestURI()).thenReturn("/fail");
    when(requestAuthenticator.authorize(AUTH_PUBLIC.authSettings(), req))
        .thenReturn(Optional.of(AuthResult.create(AuthLevel.NONE)));

    handler.handleRequest(req, rsp);

    verify(rsp).sendError(503, "Set sail for fail");
  }

  /** Test for a regression of the issue in b/21377705. */
  @Test
  public void testHandleRequest_taskThrowsHttpException_atConstructionTime_getsHandledByHandler()
      throws Exception {
    when(req.getMethod()).thenReturn("GET");
    when(req.getRequestURI()).thenReturn("/failAtConstruction");
    when(requestAuthenticator.authorize(AUTH_PUBLIC.authSettings(), req))
        .thenReturn(Optional.of(AuthResult.create(AuthLevel.NONE)));

    handler.handleRequest(req, rsp);

    verify(rsp).sendError(503, "Fail at construction");
  }

  @Test
  public void testHandleRequest_notFound_returns404NotFound() throws Exception {
    when(req.getMethod()).thenReturn("GET");
    when(req.getRequestURI()).thenReturn("/bogus");
    when(requestAuthenticator.authorize(AUTH_PUBLIC.authSettings(), req))
        .thenReturn(Optional.of(AuthResult.create(AuthLevel.NONE)));

    handler.handleRequest(req, rsp);

    verify(rsp).sendError(404);
  }

  @Test
  public void testHandleRequest_methodNotAllowed_returns405MethodNotAllowed() throws Exception {
    when(req.getMethod()).thenReturn("POST");
    when(req.getRequestURI()).thenReturn("/fail");
    when(requestAuthenticator.authorize(AUTH_PUBLIC.authSettings(), req))
        .thenReturn(Optional.of(AuthResult.create(AuthLevel.NONE)));

    handler.handleRequest(req, rsp);

    verify(rsp).sendError(405);
  }

  @Test
  public void testHandleRequest_insaneMethod_returns405MethodNotAllowed() throws Exception {
    when(req.getMethod()).thenReturn("FIREAWAY");
    when(req.getRequestURI()).thenReturn("/fail");
    when(requestAuthenticator.authorize(AUTH_PUBLIC.authSettings(), req))
        .thenReturn(Optional.of(AuthResult.create(AuthLevel.NONE)));

    handler.handleRequest(req, rsp);

    verify(rsp).sendError(405);
  }

  /** @see <a href="http://www.w3.org/Protocols/rfc2616/rfc2616-sec5.html#sec5.1.1">
   *     RFC2616 - HTTP/1.1 - Method</a> */
  @Test
  public void testHandleRequest_lowercaseMethod_notRecognized() throws Exception {
    when(req.getMethod()).thenReturn("get");
    when(req.getRequestURI()).thenReturn("/bumblebee");
    when(requestAuthenticator.authorize(AUTH_PUBLIC.authSettings(), req))
        .thenReturn(Optional.of(AuthResult.create(AuthLevel.NONE)));

    handler.handleRequest(req, rsp);

    verify(rsp).sendError(405);
  }

  @Test
  public void testNullness() {
    NullPointerTester tester = new NullPointerTester();
    tester.setDefault(Class.class, Component.class);
    tester.setDefault(RequestAuthenticator.class, requestAuthenticator);
    tester.testAllPublicStaticMethods(RequestHandler.class);
    tester.testAllPublicInstanceMethods(handler);
  }

  @Test
  public void testXsrfProtection_validTokenProvided_runsAction() throws Exception {
    when(req.getMethod()).thenReturn("POST");
    when(req.getRequestURI()).thenReturn("/safe-sloth");
    when(requestAuthenticator.authorize(AUTH_PUBLIC.authSettings(), req))
        .thenReturn(Optional.of(AuthResult.create(AuthLevel.NONE)));

    handler.handleRequest(req, rsp);

    verify(safeSlothTask).run();
  }

  @Test
  public void testXsrfProtection_GETMethodWithoutToken_doesntCheckToken() throws Exception {
    when(req.getMethod()).thenReturn("GET");
    when(req.getRequestURI()).thenReturn("/safe-sloth");
    when(requestAuthenticator.authorize(AUTH_PUBLIC.authSettings(), req))
        .thenReturn(Optional.of(AuthResult.create(AuthLevel.NONE)));

    handler.handleRequest(req, rsp);

    verify(safeSlothTask).run();
  }

  @Test
  public void testNoAuthNeeded_success() throws Exception {
    when(req.getMethod()).thenReturn("GET");
    when(req.getRequestURI()).thenReturn("/auth/none");
    when(requestAuthenticator.authorize(AUTH_PUBLIC.authSettings(), req))
        .thenReturn(Optional.of(AuthResult.create(AuthLevel.NONE)));

    handler.handleRequest(req, rsp);

    assertThat(providedAuthResult).isNotNull();
    assertThat(providedAuthResult.authLevel()).isEqualTo(AuthLevel.NONE);
    assertThat(providedAuthResult.userAuthInfo()).isAbsent();
  }

  @Test
  public void testAuthNeeded_failure() throws Exception {
    when(req.getMethod()).thenReturn("GET");
    when(req.getRequestURI()).thenReturn("/auth/adminUser");
    when(requestAuthenticator.authorize(AUTH_INTERNAL_OR_ADMIN.authSettings(), req))
        .thenReturn(Optional.<AuthResult>absent());

    handler.handleRequest(req, rsp);

    verify(rsp).sendError(403, "Not authorized");
    assertThat(providedAuthResult).isNull();
  }

  @Test
  public void testAuthNeeded_success() throws Exception {
    when(req.getMethod()).thenReturn("GET");
    when(req.getRequestURI()).thenReturn("/auth/adminUser");
    when(requestAuthenticator.authorize(AUTH_INTERNAL_OR_ADMIN.authSettings(), req))
        .thenReturn(
            Optional.of(AuthResult.create(AuthLevel.USER, UserAuthInfo.create(testUser, true))));

    handler.handleRequest(req, rsp);

    assertThat(providedAuthResult).isNotNull();
    assertThat(providedAuthResult.authLevel()).isEqualTo(AuthLevel.USER);
    assertThat(providedAuthResult.userAuthInfo()).isPresent();
    assertThat(providedAuthResult.userAuthInfo().get().user()).isEqualTo(testUser);
    assertThat(providedAuthResult.userAuthInfo().get().oauthTokenInfo()).isAbsent();
  }

}
