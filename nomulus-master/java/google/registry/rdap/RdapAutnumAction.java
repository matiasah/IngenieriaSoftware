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

package google.registry.rdap;

import static google.registry.request.Action.Method.GET;
import static google.registry.request.Action.Method.HEAD;

import com.google.common.collect.ImmutableMap;
import google.registry.request.Action;
import google.registry.request.HttpException.NotImplementedException;
import google.registry.request.auth.Auth;
import javax.inject.Inject;

/**
 * RDAP (new WHOIS) action for RDAP autonomous system number requests.
 *
 * <p>This feature is not implemented because it's only necessary for <i>address</i> registries like
 * ARIN, not domain registries.
 */
@Action(
  path = RdapAutnumAction.PATH,
  method = {GET, HEAD},
  isPrefix = true,
  auth = Auth.AUTH_PUBLIC_ANONYMOUS
)
public class RdapAutnumAction extends RdapActionBase {

  public static final String PATH = "/rdap/autnum/";

  @Inject RdapAutnumAction() {}

  @Override
  public String getHumanReadableObjectTypeName() {
    return "autnum";
  }

  @Override
  public String getActionPath() {
    return PATH;
  }

  @Override
  public ImmutableMap<String, Object> getJsonObjectForResource(
      String pathSearchString, boolean isHeadRequest, String linkBase) {
    throw new NotImplementedException("Domain Name Registry information only");
  }
}
