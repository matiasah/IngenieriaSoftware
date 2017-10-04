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

import static google.registry.request.auth.AuthLevel.APP;
import static google.registry.request.auth.AuthLevel.NONE;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;

/**
 * Authentication mechanism which uses the X-AppEngine-QueueName header set by App Engine for
 * internal requests.
 *
 * <p>
 * Task queue push task requests set this header value to the actual queue name. Cron requests set
 * this header value to __cron, since that's actually the name of the hidden queue used for cron
 * requests. Cron also sets the header X-AppEngine-Cron, which we could check, but it's simpler just
 * to check the one.
 *
 * <p>
 * App Engine allows app admins to set these headers for testing purposes. This means that this auth
 * method is somewhat unreliable - any app admin can access any internal endpoint and pretend to be
 * the app itself by setting these headers, which would circumvent any finer-grained authorization
 * if we added it in the future (assuming we did not apply it to the app itself). And App Engine's
 * concept of an "admin" includes all project owners, editors and viewers. So anyone with access to
 * the project will be able to access anything the app itself can access.
 *
 * <p>
 * For now, it's probably okay to allow this behavior, especially since it could indeed be
 * convenient for testing. If we wanted to revisit this decision in the future, we have a couple
 * options for locking this down:
 *
 * <ul>
 * <li>1. Always include the result of UserService.getCurrentUser() as the active user</li>
 * <li>2. Validate that the requests came from special AppEngine internal IPs</li>
 * </ul>
 *
 * <p>See <a href=
 * "https://cloud.google.com/appengine/docs/java/taskqueue/push/creating-handlers#reading_request_headers">task
 * handler request header documentation</a>
 */
public class AppEngineInternalAuthenticationMechanism implements AuthenticationMechanism {

  // As defined in the App Engine request header documentation.
  private static final String QUEUE_NAME_HEADER = "X-AppEngine-QueueName";

  @Inject
  public AppEngineInternalAuthenticationMechanism() {}

  @Override
  public AuthResult authenticate(HttpServletRequest request) {
    if (request.getHeader(QUEUE_NAME_HEADER) == null) {
      return AuthResult.create(NONE);
    } else {
      return AuthResult.create(APP);
    }
  }
}
