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

package google.registry.flows;

import google.registry.request.Action;
import google.registry.request.Action.Method;
import google.registry.request.Payload;
import google.registry.request.auth.Auth;
import google.registry.util.FormattingLogger;
import javax.inject.Inject;
import javax.servlet.http.HttpSession;

/**
 * Establishes a transport for EPP+TLS over HTTP. All commands and responses are EPP XML according
 * to RFC 5730. Commands must be requested via POST.
 */
@Action(
  path = "/_dr/epp",
  method = Method.POST,
  auth = Auth.AUTH_INTERNAL_OR_ADMIN
)
public class EppTlsAction implements Runnable {

  private static final FormattingLogger logger = FormattingLogger.getLoggerForCallerClass();

  @Inject @Payload byte[] inputXmlBytes;
  @Inject TlsCredentials tlsCredentials;
  @Inject HttpSession session;
  @Inject EppRequestHandler eppRequestHandler;
  @Inject EppTlsAction() {}

  @Override
  public void run() {
    // Check that SNI header is present. This is a signal that we're receiving traffic proxied by a
    // GFE, which is the expectation of this servlet. The value is unused.
    if (!tlsCredentials.hasSni()) {
      logger.warning("Request did not include required SNI header.");
    }
    eppRequestHandler.executeEpp(
        new HttpSessionMetadata(session),
        tlsCredentials,
        EppRequestSource.TLS,
        false,  // This endpoint is never a dry run.
        false,  // This endpoint is never a superuser.
        inputXmlBytes);
  }
}

