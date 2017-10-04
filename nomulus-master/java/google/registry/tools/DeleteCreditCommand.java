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

package google.registry.tools;

import static com.google.common.base.Preconditions.checkNotNull;
import static google.registry.model.ofy.ObjectifyService.ofy;
import static google.registry.util.PreconditionsUtils.checkArgumentPresent;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import google.registry.model.billing.RegistrarCredit;
import google.registry.model.billing.RegistrarCreditBalance;
import google.registry.model.registrar.Registrar;

/** Command for deleting a registrar credit object and all of its child balances. */
@Parameters(separators = " =", commandDescription = "Delete a registrar credit")
final class DeleteCreditCommand extends MutatingCommand {

  @Parameter(
      names = "--registrar",
      description = "Client ID of the registrar owning the credit to delete",
      required = true)
  private String clientId;

  @Parameter(
      names = "--credit_id",
      description = "ID of credit to delete",
      required = true)
  private long creditId;

  @Override
  protected void init() throws Exception {
    Registrar registrar =
        checkArgumentPresent(
            Registrar.loadByClientId(clientId), "Registrar %s not found", clientId);
    RegistrarCredit credit = ofy().load()
        .type(RegistrarCredit.class)
        .parent(registrar)
        .id(creditId)
        .now();
    checkNotNull(credit, "Registrar credit for %s with ID %s not found", clientId, creditId);
    stageEntityChange(credit, null);

    for (RegistrarCreditBalance balance :
        ofy().load().type(RegistrarCreditBalance.class).ancestor(credit)) {
      stageEntityChange(balance, null);
    }
  }
}
