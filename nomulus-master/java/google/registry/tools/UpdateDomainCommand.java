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

import static com.google.common.base.Preconditions.checkArgument;
import static google.registry.model.EppResourceUtils.loadByForeignKey;
import static google.registry.model.ofy.ObjectifyService.ofy;
import static org.joda.time.DateTimeZone.UTC;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Sets;
import com.google.template.soy.data.SoyMapData;
import google.registry.model.domain.DesignatedContact;
import google.registry.model.domain.DomainResource;
import google.registry.model.eppcommon.StatusValue;
import google.registry.tools.soy.DomainUpdateSoyInfo;
import google.registry.util.FormattingLogger;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.joda.time.DateTime;

/** A command to update a new domain via EPP. */
@Parameters(separators = " =", commandDescription = "Update a new domain via EPP.")
final class UpdateDomainCommand extends CreateOrUpdateDomainCommand {

  private static final FormattingLogger logger = FormattingLogger.getLoggerForCallerClass();

  @Parameter(names = "--statuses", description = "Comma-separated list of statuses to set.")
  private List<String> statuses = new ArrayList<>();

  @Parameter(
    names = "--add_nameservers",
    description =
        "Comma-separated list of nameservers to add, up to 13. "
            + "Cannot be set if --nameservers is set."
  )
  private List<String> addNameservers = new ArrayList<>();

  @Parameter(
    names = "--add_admins",
    description = "Admins to add. Cannot be set if --admins is set."
  )
  private List<String> addAdmins = new ArrayList<>();

  @Parameter(names = "--add_techs", description = "Techs to add. Cannot be set if --techs is set.")
  private List<String> addTechs = new ArrayList<>();

  @Parameter(
    names = "--add_statuses",
    description = "Statuses to add. Cannot be set if --statuses is set."
  )
  private List<String> addStatuses = new ArrayList<>();

  @Parameter(
    names = "--remove_nameservers",
    description =
        "Comma-separated list of nameservers to remove, up to 13. "
            + "Cannot be set if --nameservers is set."
  )
  private List<String> removeNameservers = new ArrayList<>();

  @Parameter(
    names = "--remove_admins",
    description = "Admins to remove. Cannot be set if --admins is set."
  )
  private List<String> removeAdmins = new ArrayList<>();

  @Parameter(
    names = "--remove_techs",
    description = "Techs to remove. Cannot be set if --techs is set."
  )
  private List<String> removeTechs = new ArrayList<>();

  @Parameter(
    names = "--remove_statuses",
    description = "Statuses to remove. Cannot be set if --statuses is set."
  )
  private List<String> removeStatuses = new ArrayList<>();

  @Override
  protected void initMutatingEppToolCommand() {
    if (!nameservers.isEmpty()) {
      checkArgument(
          addNameservers.isEmpty() && removeNameservers.isEmpty(),
          "If you provide the nameservers flag, "
              + "you cannot use the add_nameservers and remove_nameservers flags.");
    } else {
      checkArgument(addNameservers.size() <= 13, "You can add at most 13 nameservers.");
    }
    if (!admins.isEmpty()) {
      checkArgument(
          addAdmins.isEmpty() && removeAdmins.isEmpty(),
          "If you provide the admins flag, you cannot use the add_admins and remove_admins flags.");
    }
    if (!techs.isEmpty()) {
      checkArgument(
          addTechs.isEmpty() && removeTechs.isEmpty(),
          "If you provide the techs flag, you cannot use the add_techs and remove_techs flags.");
    }
    if (!statuses.isEmpty()) {
      checkArgument(
          addStatuses.isEmpty() && removeStatuses.isEmpty(),
          "If you provide the statuses flag, "
              + "you cannot use the add_statuses and remove_statuses flags.");
    }

    for (String domain : domains) {
      if (!nameservers.isEmpty() || !admins.isEmpty() || !techs.isEmpty() || !statuses.isEmpty()) {
        DateTime now = DateTime.now(UTC);
        DomainResource domainResource = loadByForeignKey(DomainResource.class, domain, now);
        checkArgument(domainResource != null, "Domain '%s' does not exist", domain);
        if (!nameservers.isEmpty()) {
          ImmutableSortedSet<String> existingNameservers =
              domainResource.loadNameserverFullyQualifiedHostNames();
          populateAddRemoveLists(
              ImmutableSet.copyOf(nameservers),
              existingNameservers,
              addNameservers,
              removeNameservers);
          checkArgument(
              existingNameservers.size() + addNameservers.size() - removeNameservers.size() <= 13,
              "The resulting nameservers count for domain %s would be more than 13",
              domain);
        }
        if (!admins.isEmpty() || !techs.isEmpty()) {
          ImmutableSet<String> existingAdmins =
              getContactsOfType(domainResource, DesignatedContact.Type.ADMIN);
          ImmutableSet<String> existingTechs =
              getContactsOfType(domainResource, DesignatedContact.Type.TECH);

          if (!admins.isEmpty()) {
            populateAddRemoveLists(
                ImmutableSet.copyOf(admins), existingAdmins, addAdmins, removeAdmins);
          }
          if (!techs.isEmpty()) {
            populateAddRemoveLists(
                ImmutableSet.copyOf(techs), existingTechs, addTechs, removeTechs);
          }
        }
        if (!statuses.isEmpty()) {
          Set<String> currentStatusValues = new HashSet<>();
          for (StatusValue statusValue : domainResource.getStatusValues()) {
            currentStatusValues.add(statusValue.getXmlName());
          }
          populateAddRemoveLists(
              ImmutableSet.copyOf(statuses), currentStatusValues, addStatuses, removeStatuses);
        }
      }

      boolean add =
          !addNameservers.isEmpty()
              || !addAdmins.isEmpty()
              || !addTechs.isEmpty()
              || !addStatuses.isEmpty();

      boolean remove =
          !removeNameservers.isEmpty()
              || !removeAdmins.isEmpty()
              || !removeTechs.isEmpty()
              || !removeStatuses.isEmpty();

      boolean change = registrant != null || password != null;

      if (!add && !remove && !change) {
        logger.infofmt("No changes need to be made to domain %s", domain);
        continue;
      }

      setSoyTemplate(DomainUpdateSoyInfo.getInstance(), DomainUpdateSoyInfo.DOMAINUPDATE);
      addSoyRecord(
          clientId,
          new SoyMapData(
              "domain", domain,
              "add", add,
              "addNameservers", addNameservers,
              "addAdmins", addAdmins,
              "addTechs", addTechs,
              "addStatuses", addStatuses,
              "remove", remove,
              "removeNameservers", removeNameservers,
              "removeAdmins", removeAdmins,
              "removeTechs", removeTechs,
              "removeStatuses", removeStatuses,
              "change", change,
              "registrant", registrant,
              "password", password));
    }
  }

  protected void populateAddRemoveLists(
      Set<String> targetSet, Set<String> oldSet, List<String> addList, List<String> removeList) {
    addList.addAll(Sets.difference(targetSet, oldSet));
    removeList.addAll(Sets.difference(oldSet, targetSet));
  }

  ImmutableSet<String> getContactsOfType(
      DomainResource domainResource, final DesignatedContact.Type contactType) {
    return FluentIterable.from(domainResource.getContacts())
        .filter(
            new Predicate<DesignatedContact>() {
              @Override
              public boolean apply(DesignatedContact contact) {
                return contact.getType().equals(contactType);
              }
            })
        .transform(
            new Function<DesignatedContact, String>() {
              @Override
              public String apply(DesignatedContact contact) {
                return ofy().load().key(contact.getContactKey()).now().getContactId();
              }
            })
        .toSet();
  }
}
