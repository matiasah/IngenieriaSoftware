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

import static com.google.common.base.Preconditions.checkArgument;

import com.google.auto.value.AutoValue;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Ordering;
import com.google.errorprone.annotations.Immutable;
import google.registry.util.FormattingLogger;
import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;

/** Top-level authentication/authorization class; calls authentication mechanisms as needed. */
public class RequestAuthenticator {

  private final AppEngineInternalAuthenticationMechanism appEngineInternalAuthenticationMechanism;
  private final ImmutableList<AuthenticationMechanism> apiAuthenticationMechanisms;
  private final LegacyAuthenticationMechanism legacyAuthenticationMechanism;

  private static final FormattingLogger logger = FormattingLogger.getLoggerForCallerClass();

  @VisibleForTesting
  @Inject
  public RequestAuthenticator(
      AppEngineInternalAuthenticationMechanism appEngineInternalAuthenticationMechanism,
      ImmutableList<AuthenticationMechanism> apiAuthenticationMechanisms,
      LegacyAuthenticationMechanism legacyAuthenticationMechanism) {
    this.appEngineInternalAuthenticationMechanism = appEngineInternalAuthenticationMechanism;
    this.apiAuthenticationMechanisms = apiAuthenticationMechanisms;
    this.legacyAuthenticationMechanism = legacyAuthenticationMechanism;
  }

  /**
   * Parameters used to configure the authenticator.
   *
   * AuthSettings shouldn't be used directly, instead - use one of the predefined {@link Auth} enum
   * values.
   */
  @Immutable
  @AutoValue
  public abstract static class AuthSettings {

    public abstract ImmutableList<AuthMethod> methods();
    public abstract AuthLevel minimumLevel();
    public abstract UserPolicy userPolicy();

    static AuthSettings create(
        ImmutableList<AuthMethod> methods, AuthLevel minimumLevel, UserPolicy userPolicy) {
      return new AutoValue_RequestAuthenticator_AuthSettings(methods, minimumLevel, userPolicy);
    }
  }

  /** Available methods for authentication. */
  public enum AuthMethod {

    /** App Engine internal authentication. Must always be provided as the first method. */
    INTERNAL,

    /** Authentication methods suitable for API-style access, such as OAuth 2. */
    API,

    /** Legacy authentication using cookie-based App Engine Users API. Must come last if present. */
    LEGACY
  }

  /** User authorization policy options. */
  public enum UserPolicy {

    /** This action ignores end users; the only configured auth method must be INTERNAL. */
    IGNORED,

    /** No user policy is enforced; anyone can access this action. */
    PUBLIC,

    /**
     * If there is a user, it must be an admin, as determined by isUserAdmin().
     *
     * <p>Note that, according to App Engine, anybody with access to the app in the GCP Console,
     * including editors and viewers, is an admin.
     */
    ADMIN
  }

  /**
   * Attempts to authenticate and authorize the user, according to the settings of the action.
   *
   * @param auth the auth settings of the action, which determine what authentication and
   *     authorization are allowed
   * @param req the {@link HttpServletRequest}; some authentication mechanisms use HTTP headers
   * @return an authentication result if authentication/authorization was successful, or absent() if
   *     not; authentication can be "successful" even without any authentication if the action's
   *     auth settings are set to NONE -- in this case, NOT_AUTHENTICATED is returned
   */
  public Optional<AuthResult> authorize(AuthSettings auth, HttpServletRequest req) {
    logger.infofmt("Action requires auth: %s", auth);
    AuthResult authResult = authenticate(auth, req);
    switch (auth.minimumLevel()) {
      case NONE:
        // Any authentication result is ok.
        break;
      case APP:
        if (!authResult.isAuthenticated()) {
          logger.warning("Not authorized; no authentication found");
          return Optional.absent();
        }
        break;
      case USER:
        if (authResult.authLevel() != AuthLevel.USER) {
          logger.warning("Not authorized; no authenticated user");
          // TODO(mountford): change this so that the caller knows to return a more helpful error
          return Optional.absent();
        }
        break;
    }
    switch (auth.userPolicy()) {
      case IGNORED:
        if (authResult.authLevel() == AuthLevel.USER) {
          logger.warning("Not authorized; user policy is IGNORED, but a user was found");
          return Optional.absent();
        }
        break;
      case PUBLIC:
        // Any user auth result is okay.
        break;
      case ADMIN:
        if (authResult.userAuthInfo().isPresent()
            && !authResult.userAuthInfo().get().isUserAdmin()) {
          logger.warning("Not authorized; user policy is ADMIN, but the user was not an admin");
          return Optional.absent();
        }
        break;
    }
    return Optional.of(authResult);
  }

  /**
   * Attempts to authenticate the user, according to the settings of the action.
   *
   * @param auth the auth settings of the action, which determine what authentication is allowed
   * @param req the {@link HttpServletRequest}; some authentication mechanisms use HTTP headers
   * @return an authentication result; if no authentication was made, returns NOT_AUTHENTICATED
   */
  private AuthResult authenticate(AuthSettings auth, HttpServletRequest req) {
    checkAuthConfig(auth);
    for (AuthMethod authMethod : auth.methods()) {
      switch (authMethod) {
        // App Engine internal authentication, using the queue name header
        case INTERNAL:
          // checkAuthConfig will have insured that the user policy is not USER.
          {
            AuthResult authResult = appEngineInternalAuthenticationMechanism.authenticate(req);
            if (authResult.isAuthenticated()) {
              logger.infofmt("Authenticated via internal auth: %s", authResult);
              return authResult;
            }
          }
          break;
        // API-based user authentication mechanisms, such as OAuth
        case API:
          // checkAuthConfig will have insured that the user policy is not IGNORED.
          for (AuthenticationMechanism authMechanism : apiAuthenticationMechanisms) {
            AuthResult authResult = authMechanism.authenticate(req);
            if (authResult.isAuthenticated()) {
              logger.infofmt(
                  "Authenticated via %s: %s", authMechanism.getClass().getSimpleName(), authResult);
              return authResult;
            }
          }
          break;
        // Legacy authentication via UserService
        case LEGACY:
          // checkAuthConfig will have insured that the user policy is not IGNORED.
          AuthResult authResult = legacyAuthenticationMechanism.authenticate(req);
          if (authResult.isAuthenticated()) {
            logger.infofmt("Authenticated via legacy auth: %s", authResult);
            return authResult;
          }
          break;
      }
    }
    logger.info("No authentication found");
    return AuthResult.NOT_AUTHENTICATED;
  }

  /** Validates an AuthSettings object, checking for invalid setting combinations. */
  static void checkAuthConfig(AuthSettings auth) {
    ImmutableList<AuthMethod> authMethods = ImmutableList.copyOf(auth.methods());
    checkArgument(!authMethods.isEmpty(), "Must specify at least one auth method");
    checkArgument(
        Ordering.explicit(AuthMethod.INTERNAL, AuthMethod.API, AuthMethod.LEGACY)
            .isStrictlyOrdered(authMethods),
        "Auth methods must be unique and strictly in order - INTERNAL, API, LEGACY");
    checkArgument(
        !(authMethods.contains(AuthMethod.INTERNAL) && auth.minimumLevel().equals(AuthLevel.USER)),
        "Actions with INTERNAL auth method may not require USER auth level");
    checkArgument(
        !(auth.userPolicy().equals(UserPolicy.IGNORED)
          && !authMethods.equals(ImmutableList.of(AuthMethod.INTERNAL))),
        "Actions with auth methods beyond INTERNAL must not specify the IGNORED user policy");
  }
}
