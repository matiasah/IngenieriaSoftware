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

import static com.google.common.base.Preconditions.checkState;
import static google.registry.model.ofy.ObjectifyService.ofy;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.googlecode.objectify.VoidWork;
import google.registry.model.domain.DomainResource;
import google.registry.model.registrar.Registrar;
import google.registry.model.registry.Registry;
import google.registry.model.registry.Registry.TldType;
import google.registry.tools.Command.RemoteApiCommand;

/**
 * Command to delete the {@link Registry} associated with the specified TLD in Datastore.
 *
 * <p>This command will fail if any domains are currently registered on the TLD.
 */
@Parameters(separators = " =", commandDescription = "Delete a TLD from Datastore.")
final class DeleteTldCommand extends ConfirmingCommand implements RemoteApiCommand {

  private Registry registry;

  @Parameter(
      names = {"-t", "--tld"},
      description = "The TLD to delete.",
      required = true)
  private String tld;

  /**
   * Perform the command by deleting the TLD.
   *
   * <p>Note that this uses an eventually consistent query, so theoretically, if you create a TLD,
   * create domains on it, then delete the TLD quickly enough, the code won't notice the domains,
   * and will let you delete the TLD. Since this command is only intended to be used in cleanup
   * tasks, that should be ok, and the check should always provide the desired safety against
   * accidental deletion of established TLDs with domains on them.
   */
  @Override
  protected void init() throws Exception {
    registry = Registry.get(tld);
    checkState(registry.getTldType().equals(TldType.TEST), "Cannot delete a real TLD");

    for (Registrar registrar : Registrar.loadAll()) {
      checkState(
          !registrar.getAllowedTlds().contains(tld),
          "Cannot delete TLD because registrar %s lists it as an allowed TLD",
          registrar.getClientId());
    }

    int count = ofy().load()
        .type(DomainResource.class)
        .filter("tld", tld)
        .limit(1)
        .count();
    checkState(count == 0, "Cannot delete TLD because a domain is defined on it");
  }

  @Override
  protected String prompt() {
    return "You are about to delete TLD: " + tld;
  }

  @Override
  protected String execute() throws Exception {
    ofy().transactNew(new VoidWork() {
      @Override
      public void vrun() {
        ofy().delete().entity(registry).now();
      }});
    registry.invalidateInCache();
    return String.format("Deleted TLD '%s'.\n", tld);
  }
}
