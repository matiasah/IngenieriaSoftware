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

package google.registry.request.auth;

import static com.google.common.net.HttpHeaders.AUTHORIZATION;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import com.google.appengine.api.users.User;
import com.google.appengine.api.users.UserService;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import google.registry.request.auth.RequestAuthenticator.AuthMethod;
import google.registry.request.auth.RequestAuthenticator.AuthSettings;
import google.registry.request.auth.RequestAuthenticator.UserPolicy;
import google.registry.security.XsrfTokenManager;
import google.registry.testing.AppEngineRule;
import google.registry.testing.ExceptionRule;
import google.registry.testing.FakeClock;
import google.registry.testing.FakeOAuthService;
import google.registry.testing.FakeUserService;
import javax.servlet.http.HttpServletRequest;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link RequestAuthenticator}. */
@RunWith(JUnit4.class)
public class RequestAuthenticatorTest {

  @Rule
  public final AppEngineRule appEngine = AppEngineRule.builder().build();

  @Rule
  public final ExceptionRule thrown = new ExceptionRule();

  private static final AuthSettings AUTH_NONE = AuthSettings.create(
      ImmutableList.of(AuthMethod.INTERNAL),
      AuthLevel.NONE,
      UserPolicy.IGNORED);

  private static final AuthSettings AUTH_INTERNAL_ONLY = AuthSettings.create(
      ImmutableList.of(AuthMethod.INTERNAL),
      AuthLevel.APP,
      UserPolicy.IGNORED);

  private static final AuthSettings AUTH_ANY_USER_ANY_METHOD = AuthSettings.create(
      ImmutableList.of(AuthMethod.API, AuthMethod.LEGACY),
      AuthLevel.USER,
      UserPolicy.PUBLIC);

  private static final AuthSettings AUTH_ANY_USER_NO_LEGACY = AuthSettings.create(
      ImmutableList.of(AuthMethod.API),
      AuthLevel.USER,
      UserPolicy.PUBLIC);

  private static final AuthSettings AUTH_ADMIN_USER_ANY_METHOD = AuthSettings.create(
      ImmutableList.of(AuthMethod.API, AuthMethod.LEGACY),
      AuthLevel.USER,
      UserPolicy.ADMIN);

  private static final AuthSettings AUTH_NO_METHODS = AuthSettings.create(
      ImmutableList.<AuthMethod>of(),
      AuthLevel.APP,
      UserPolicy.IGNORED);

  private static final AuthSettings AUTH_WRONG_METHOD_ORDERING = AuthSettings.create(
      ImmutableList.of(AuthMethod.API, AuthMethod.INTERNAL),
      AuthLevel.APP,
      UserPolicy.IGNORED);

  private static final AuthSettings AUTH_DUPLICATE_METHODS = AuthSettings.create(
      ImmutableList.of(AuthMethod.INTERNAL, AuthMethod.API, AuthMethod.API),
      AuthLevel.APP,
      UserPolicy.IGNORED);

  private static final AuthSettings AUTH_INTERNAL_WITH_USER = AuthSettings.create(
      ImmutableList.of(AuthMethod.INTERNAL, AuthMethod.API),
      AuthLevel.USER,
      UserPolicy.IGNORED);

  private static final AuthSettings AUTH_WRONGLY_IGNORING_USER = AuthSettings.create(
      ImmutableList.of(AuthMethod.INTERNAL, AuthMethod.API),
      AuthLevel.APP,
      UserPolicy.IGNORED);

  private final UserService mockUserService = mock(UserService.class);
  private final HttpServletRequest req = mock(HttpServletRequest.class);

  private final User testUser = new User("test@google.com", "test@google.com");
  private final FakeUserService fakeUserService = new FakeUserService();
  private final XsrfTokenManager xsrfTokenManager =
      new XsrfTokenManager(new FakeClock(), fakeUserService);
  private final FakeOAuthService fakeOAuthService = new FakeOAuthService(
      false /* isOAuthEnabled */,
      testUser,
      false /* isUserAdmin */,
      "test-client-id",
      ImmutableList.of("test-scope1", "test-scope2", "nontest-scope"));

  @Before
  public void before() throws Exception {
    when(req.getMethod()).thenReturn("POST");
  }

  private RequestAuthenticator createRequestAuthenticator(UserService userService) {
    return new RequestAuthenticator(
        new AppEngineInternalAuthenticationMechanism(),
        ImmutableList.<AuthenticationMechanism>of(
            new OAuthAuthenticationMechanism(
                fakeOAuthService,
                ImmutableSet.of("test-scope1", "test-scope2", "test-scope3"),
                ImmutableSet.of("test-scope1", "test-scope2"),
                ImmutableSet.of("test-client-id", "other-test-client-id"))),
        new LegacyAuthenticationMechanism(userService, xsrfTokenManager));
  }

  private Optional<AuthResult> runTest(UserService userService, AuthSettings auth) {
    return createRequestAuthenticator(userService)
        .authorize(auth, req);
  }

  @Test
  public void testNoAuthNeeded_noneFound() throws Exception {
    Optional<AuthResult> authResult = runTest(mockUserService, AUTH_NONE);

    verifyZeroInteractions(mockUserService);
    assertThat(authResult).isPresent();
    assertThat(authResult.get().authLevel()).isEqualTo(AuthLevel.NONE);
  }

  @Test
  public void testNoAuthNeeded_internalFound() throws Exception {
    when(req.getHeader("X-AppEngine-QueueName")).thenReturn("__cron");

    Optional<AuthResult> authResult = runTest(mockUserService, AUTH_NONE);

    verifyZeroInteractions(mockUserService);
    assertThat(authResult).isPresent();
    assertThat(authResult.get().authLevel()).isEqualTo(AuthLevel.APP);
    assertThat(authResult.get().userAuthInfo()).isAbsent();
  }

  @Test
  public void testInternalAuth_notInvokedInternally() throws Exception {
    Optional<AuthResult> authResult = runTest(mockUserService, AUTH_INTERNAL_ONLY);

    verifyZeroInteractions(mockUserService);
    assertThat(authResult).isAbsent();
  }

  @Test
  public void testInternalAuth_success() throws Exception {
    when(req.getHeader("X-AppEngine-QueueName")).thenReturn("__cron");

    Optional<AuthResult> authResult = runTest(mockUserService, AUTH_INTERNAL_ONLY);

    verifyZeroInteractions(mockUserService);
    assertThat(authResult).isPresent();
    assertThat(authResult.get().authLevel()).isEqualTo(AuthLevel.APP);
    assertThat(authResult.get().userAuthInfo()).isAbsent();
  }

  @Test
  public void testAnyUserAnyMethod_notLoggedIn() throws Exception {
    Optional<AuthResult> authResult = runTest(fakeUserService, AUTH_ANY_USER_ANY_METHOD);

    assertThat(authResult).isAbsent();
  }

  @Test
  public void testAnyUserAnyMethod_xsrfFailure() throws Exception {
    fakeUserService.setUser(testUser, false);

    Optional<AuthResult> authResult = runTest(fakeUserService, AUTH_ANY_USER_ANY_METHOD);

    assertThat(authResult).isAbsent();
  }

  @Test
  public void testAnyUserAnyMethod_success() throws Exception {
    fakeUserService.setUser(testUser, false /* isAdmin */);
    when(req.getHeader(XsrfTokenManager.X_CSRF_TOKEN))
        .thenReturn(xsrfTokenManager.generateToken(testUser.getEmail()));

    Optional<AuthResult> authResult = runTest(fakeUserService, AUTH_ANY_USER_ANY_METHOD);

    assertThat(authResult).isPresent();
    assertThat(authResult.get().authLevel()).isEqualTo(AuthLevel.USER);
    assertThat(authResult.get().userAuthInfo()).isPresent();
    assertThat(authResult.get().userAuthInfo().get().user()).isEqualTo(testUser);
    assertThat(authResult.get().userAuthInfo().get().isUserAdmin()).isFalse();
    assertThat(authResult.get().userAuthInfo().get().oauthTokenInfo()).isAbsent();
  }

  @Test
  public void testAnyUserAnyMethod_xsrfNotRequiredForGet() throws Exception {
    fakeUserService.setUser(testUser, false);
    when(req.getMethod()).thenReturn("GET");

    Optional<AuthResult> authResult = runTest(fakeUserService, AUTH_ANY_USER_ANY_METHOD);

    assertThat(authResult).isPresent();
    assertThat(authResult.get().authLevel()).isEqualTo(AuthLevel.USER);
    assertThat(authResult.get().userAuthInfo()).isPresent();
    assertThat(authResult.get().userAuthInfo().get().user()).isEqualTo(testUser);
    assertThat(authResult.get().userAuthInfo().get().oauthTokenInfo()).isAbsent();
  }

  @Test
  public void testAdminUserAnyMethod_notLoggedIn() throws Exception {
    Optional<AuthResult> authResult = runTest(fakeUserService, AUTH_ADMIN_USER_ANY_METHOD);

    assertThat(authResult).isAbsent();
  }

  @Test
  public void testAdminUserAnyMethod_notAdminUser() throws Exception {
    fakeUserService.setUser(testUser, false /* isAdmin */);

    Optional<AuthResult> authResult = runTest(fakeUserService, AUTH_ADMIN_USER_ANY_METHOD);

    assertThat(authResult).isAbsent();
  }

  @Test
  public void testAdminUserAnyMethod_xsrfFailure() throws Exception {
    fakeUserService.setUser(testUser, true);

    Optional<AuthResult> authResult = runTest(fakeUserService, AUTH_ADMIN_USER_ANY_METHOD);

    assertThat(authResult).isAbsent();
  }

  @Test
  public void testAdminUserAnyMethod_success() throws Exception {
    fakeUserService.setUser(testUser, true /* isAdmin */);
    when(req.getHeader(XsrfTokenManager.X_CSRF_TOKEN))
        .thenReturn(xsrfTokenManager.generateToken(testUser.getEmail()));

    Optional<AuthResult> authResult = runTest(fakeUserService, AUTH_ADMIN_USER_ANY_METHOD);

    assertThat(authResult).isPresent();
    assertThat(authResult.get().authLevel()).isEqualTo(AuthLevel.USER);
    assertThat(authResult.get().userAuthInfo()).isPresent();
    assertThat(authResult.get().userAuthInfo().get().user()).isEqualTo(testUser);
    assertThat(authResult.get().userAuthInfo().get().isUserAdmin()).isTrue();
    assertThat(authResult.get().userAuthInfo().get().oauthTokenInfo()).isAbsent();
  }

  @Test
  public void testOAuth_success() throws Exception {
    fakeOAuthService.setUser(testUser);
    fakeOAuthService.setOAuthEnabled(true);
    when(req.getHeader(AUTHORIZATION)).thenReturn("Bearer TOKEN");

    Optional<AuthResult> authResult = runTest(fakeUserService, AUTH_ANY_USER_NO_LEGACY);

    assertThat(authResult).isPresent();
    assertThat(authResult.get().authLevel()).isEqualTo(AuthLevel.USER);
    assertThat(authResult.get().userAuthInfo()).isPresent();
    assertThat(authResult.get().userAuthInfo().get().user()).isEqualTo(testUser);
    assertThat(authResult.get().userAuthInfo().get().isUserAdmin()).isFalse();
    assertThat(authResult.get().userAuthInfo().get().oauthTokenInfo()).isPresent();
    assertThat(authResult.get().userAuthInfo().get().oauthTokenInfo().get().authorizedScopes())
      .containsAllOf("test-scope1", "test-scope2");
    assertThat(authResult.get().userAuthInfo().get().oauthTokenInfo().get().oauthClientId())
      .isEqualTo("test-client-id");
    assertThat(authResult.get().userAuthInfo().get().oauthTokenInfo().get().rawAccessToken())
      .isEqualTo("TOKEN");
  }

  @Test
  public void testOAuthAdmin_success() throws Exception {
    fakeOAuthService.setUser(testUser);
    fakeOAuthService.setUserAdmin(true);
    fakeOAuthService.setOAuthEnabled(true);
    when(req.getHeader(AUTHORIZATION)).thenReturn("Bearer TOKEN");

    Optional<AuthResult> authResult = runTest(fakeUserService, AUTH_ANY_USER_NO_LEGACY);

    assertThat(authResult).isPresent();
    assertThat(authResult.get().authLevel()).isEqualTo(AuthLevel.USER);
    assertThat(authResult.get().userAuthInfo()).isPresent();
    assertThat(authResult.get().userAuthInfo().get().user()).isEqualTo(testUser);
    assertThat(authResult.get().userAuthInfo().get().isUserAdmin()).isTrue();
    assertThat(authResult.get().userAuthInfo().get().oauthTokenInfo()).isPresent();
    assertThat(authResult.get().userAuthInfo().get().oauthTokenInfo().get().authorizedScopes())
      .containsAllOf("test-scope1", "test-scope2");
    assertThat(authResult.get().userAuthInfo().get().oauthTokenInfo().get().oauthClientId())
      .isEqualTo("test-client-id");
    assertThat(authResult.get().userAuthInfo().get().oauthTokenInfo().get().rawAccessToken())
      .isEqualTo("TOKEN");
  }

  @Test
  public void testOAuthMissingAuthenticationToken_failure() throws Exception {
    fakeOAuthService.setUser(testUser);
    fakeOAuthService.setOAuthEnabled(true);

    Optional<AuthResult> authResult = runTest(fakeUserService, AUTH_ANY_USER_NO_LEGACY);

    assertThat(authResult).isAbsent();
  }

  @Test
  public void testOAuthClientIdMismatch_failure() throws Exception {
    fakeOAuthService.setUser(testUser);
    fakeOAuthService.setOAuthEnabled(true);
    fakeOAuthService.setClientId("wrong-client-id");
    when(req.getHeader(AUTHORIZATION)).thenReturn("Bearer TOKEN");

    Optional<AuthResult> authResult = runTest(fakeUserService, AUTH_ANY_USER_NO_LEGACY);

    assertThat(authResult).isAbsent();
  }

  @Test
  public void testOAuthNoScopes_failure() throws Exception {
    fakeOAuthService.setUser(testUser);
    fakeOAuthService.setOAuthEnabled(true);
    fakeOAuthService.setAuthorizedScopes();
    when(req.getHeader(AUTHORIZATION)).thenReturn("Bearer TOKEN");

    Optional<AuthResult> authResult = runTest(fakeUserService, AUTH_ANY_USER_NO_LEGACY);

    assertThat(authResult).isAbsent();
  }

  @Test
  public void testOAuthMissingScope_failure() throws Exception {
    fakeOAuthService.setUser(testUser);
    fakeOAuthService.setOAuthEnabled(true);
    fakeOAuthService.setAuthorizedScopes("test-scope1", "test-scope3");
    when(req.getHeader(AUTHORIZATION)).thenReturn("Bearer TOKEN");

    Optional<AuthResult> authResult = runTest(fakeUserService, AUTH_ANY_USER_NO_LEGACY);

    assertThat(authResult).isAbsent();
  }

  @Test
  public void testOAuthExtraScope_success() throws Exception {
    fakeOAuthService.setUser(testUser);
    fakeOAuthService.setOAuthEnabled(true);
    fakeOAuthService.setAuthorizedScopes("test-scope1", "test-scope2", "test-scope3");
    when(req.getHeader(AUTHORIZATION)).thenReturn("Bearer TOKEN");

    Optional<AuthResult> authResult = runTest(fakeUserService, AUTH_ANY_USER_NO_LEGACY);

    assertThat(authResult).isPresent();
    assertThat(authResult.get().authLevel()).isEqualTo(AuthLevel.USER);
    assertThat(authResult.get().userAuthInfo()).isPresent();
    assertThat(authResult.get().userAuthInfo().get().user()).isEqualTo(testUser);
    assertThat(authResult.get().userAuthInfo().get().isUserAdmin()).isFalse();
    assertThat(authResult.get().userAuthInfo().get().oauthTokenInfo()).isPresent();
    assertThat(authResult.get().userAuthInfo().get().oauthTokenInfo().get().authorizedScopes())
      .containsAllOf("test-scope1", "test-scope2", "test-scope3");
    assertThat(authResult.get().userAuthInfo().get().oauthTokenInfo().get().oauthClientId())
      .isEqualTo("test-client-id");
    assertThat(authResult.get().userAuthInfo().get().oauthTokenInfo().get().rawAccessToken())
      .isEqualTo("TOKEN");
  }

  @Test
  public void testAnyUserNoLegacy_failureWithLegacyUser() throws Exception {
    fakeUserService.setUser(testUser, false /* isAdmin */);

    Optional<AuthResult> authResult = runTest(fakeUserService, AUTH_ANY_USER_NO_LEGACY);

    assertThat(authResult).isAbsent();
  }

  @Test
  public void testCheckAuthConfig_NoMethods_failure() throws Exception {
    thrown.expect(IllegalArgumentException.class, "Must specify at least one auth method");

    RequestAuthenticator.checkAuthConfig(AUTH_NO_METHODS);
  }

  @Test
  public void testCheckAuthConfig_WrongMethodOrdering_failure() throws Exception {
    thrown.expect(IllegalArgumentException.class,
        "Auth methods must be unique and strictly in order - INTERNAL, API, LEGACY");

    RequestAuthenticator.checkAuthConfig(AUTH_WRONG_METHOD_ORDERING);
  }

  @Test
  public void testCheckAuthConfig_DuplicateMethods_failure() throws Exception {
    thrown.expect(IllegalArgumentException.class,
        "Auth methods must be unique and strictly in order - INTERNAL, API, LEGACY");

    RequestAuthenticator.checkAuthConfig(AUTH_DUPLICATE_METHODS);
  }

  @Test
  public void testCheckAuthConfig_InternalWithUser_failure() throws Exception {
    thrown.expect(IllegalArgumentException.class,
        "Actions with INTERNAL auth method may not require USER auth level");

    RequestAuthenticator.checkAuthConfig(AUTH_INTERNAL_WITH_USER);
  }

  @Test
  public void testCheckAuthConfig_WronglyIgnoringUser_failure() throws Exception {
    thrown.expect(IllegalArgumentException.class,
        "Actions with auth methods beyond INTERNAL must not specify the IGNORED user policy");

    RequestAuthenticator.checkAuthConfig(AUTH_WRONGLY_IGNORING_USER);
  }
}
