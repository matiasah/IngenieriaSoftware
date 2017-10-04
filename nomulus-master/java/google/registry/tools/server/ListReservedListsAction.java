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

import static google.registry.model.common.EntityGroupRoot.getCrossTldKey;
import static google.registry.model.ofy.ObjectifyService.ofy;
import static google.registry.request.Action.Method.GET;
import static google.registry.request.Action.Method.POST;

import com.google.common.collect.ImmutableSet;
import google.registry.model.registry.label.ReservedList;
import google.registry.request.Action;
import google.registry.request.auth.Auth;
import javax.inject.Inject;

/** A that lists reserved lists, for use by the {@code nomulus list_reserved_lists} command. */
@Action(
  path = ListReservedListsAction.PATH,
  method = {GET, POST},
  auth = Auth.AUTH_INTERNAL_OR_ADMIN
)
public final class ListReservedListsAction extends ListObjectsAction<ReservedList> {

  public static final String PATH = "/_dr/admin/list/reservedLists";

  @Inject ListReservedListsAction() {}

  @Override
  public ImmutableSet<String> getPrimaryKeyFields() {
    return ImmutableSet.of("name");
  }

  @Override
  public ImmutableSet<ReservedList> loadObjects() {
    return ImmutableSet.copyOf(
        ofy().load().type(ReservedList.class).ancestor(getCrossTldKey()).list());
  }
}
