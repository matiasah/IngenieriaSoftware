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

import static com.google.common.base.Strings.nullToEmpty;
import static google.registry.model.EppResourceUtils.isLinked;
import static google.registry.model.ofy.ObjectifyService.ofy;
import static google.registry.util.CollectionUtils.union;
import static google.registry.util.DomainNameUtils.ACE_PREFIX;

import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.base.Optional;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.collect.Ordering;
import com.google.common.net.InetAddresses;
import com.googlecode.objectify.Key;
import google.registry.config.RdapNoticeDescriptor;
import google.registry.config.RegistryConfig.Config;
import google.registry.model.EppResource;
import google.registry.model.contact.ContactPhoneNumber;
import google.registry.model.contact.ContactResource;
import google.registry.model.contact.PostalInfo;
import google.registry.model.domain.DesignatedContact;
import google.registry.model.domain.DesignatedContact.Type;
import google.registry.model.domain.DomainResource;
import google.registry.model.eppcommon.Address;
import google.registry.model.eppcommon.StatusValue;
import google.registry.model.host.HostResource;
import google.registry.model.registrar.Registrar;
import google.registry.model.registrar.RegistrarAddress;
import google.registry.model.registrar.RegistrarContact;
import google.registry.model.reporting.HistoryEntry;
import google.registry.request.HttpException.InternalServerErrorException;
import google.registry.request.HttpException.NotFoundException;
import google.registry.util.FormattingLogger;
import google.registry.util.Idn;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.joda.time.DateTime;

/**
 * Helper class to create RDAP JSON objects for various registry entities and objects.
 *
 * <p>The JSON format specifies that entities should be supplied with links indicating how to fetch
 * them via RDAP, which requires the URL to the RDAP server. The linkBase parameter, passed to many
 * of the methods, is used as the first part of the link URL. For instance, if linkBase is
 * "http://rdap.org/dir/", the link URLs will look like "http://rdap.org/dir/domain/XXXX", etc.
 *
 * @see <a href="https://tools.ietf.org/html/rfc7483">
 *        RFC 7483: JSON Responses for the Registration Data Access Protocol (RDAP)</a>
 */
@Singleton
public class RdapJsonFormatter {

  @Inject @Config("rdapTosPath") String rdapTosPath;
  @Inject @Config("rdapHelpMap") ImmutableMap<String, RdapNoticeDescriptor> rdapHelpMap;
  @Inject RdapJsonFormatter() {}

  private static final FormattingLogger logger = FormattingLogger.getLoggerForCallerClass();

  /**
   * What type of data to generate. Summary data includes only information about the object itself,
   * while full data includes associated items (e.g. for domains, full data includes the hosts,
   * contacts and history entries connected with the domain). Summary data is appropriate for search
   * queries which return many results, to avoid load on the system. According to the ICANN
   * operational profile, a remark must be attached to the returned object indicating that it
   * includes only summary data.
   */
  public enum OutputDataType {
    FULL,
    SUMMARY
  }

  /**
   * Indication of what type of boilerplate notices are required for the RDAP JSON messages. The
   * ICANN RDAP Profile specifies that, for instance, domain name responses should include a remark
   * about domain status codes. So we need to know when to include such boilerplate. On the other
   * hand, remarks are not allowed except in domain, nameserver and entity objects, so we need to
   * suppress them for other types of responses (e.g. help).
   */
  public enum BoilerplateType {
    DOMAIN,
    NAMESERVER,
    ENTITY,
    OTHER
  }

  private static final String RDAP_CONFORMANCE_LEVEL = "rdap_level_0";
  private static final String VCARD_VERSION_NUMBER = "4.0";
  static final String NOTICES = "notices";
  private static final String REMARKS = "remarks";

  private enum RdapStatus {

    // Status values specified in RFC 7483 § 10.2.2.
    VALIDATED("validated"),
    RENEW_PROHIBITED("renew prohibited"),
    UPDATE_PROHIBITED("update prohibited"),
    TRANSFER_PROHIBITED("transfer prohibited"),
    DELETE_PROHIBITED("delete prohibited"),
    PROXY("proxy"),
    PRIVATE("private"),
    REMOVED("removed"),
    OBSCURED("obscured"),
    ASSOCIATED("associated"),
    ACTIVE("active"),
    INACTIVE("inactive"),
    LOCKED("locked"),
    PENDING_CREATE("pending create"),
    PENDING_RENEW("pending renew"),
    PENDING_TRANSFER("pending transfer"),
    PENDING_UPDATE("pending update"),
    PENDING_DELETE("pending delete"),

    // Additional status values defined in
    // https://tools.ietf.org/html/draft-ietf-regext-epp-rdap-status-mapping-01.
    ADD_PERIOD("add period"),
    AUTO_RENEW_PERIOD("auto renew period"),
    CLIENT_DELETE_PROHIBITED("client delete prohibited"),
    CLIENT_HOLD("client hold"),
    CLIENT_RENEW_PROHIBITED("client renew prohibited"),
    CLIENT_TRANSFER_PROHIBITED("client transfer prohibited"),
    CLIENT_UPDATE_PROHIBITED("client update prohibited"),
    PENDING_RESTORE("pending restore"),
    REDEMPTION_PERIOD("redemption period"),
    RENEW_PERIOD("renew period"),
    SERVER_DELETE_PROHIBITED("server deleted prohibited"),
    SERVER_RENEW_PROHIBITED("server renew prohibited"),
    SERVER_TRANSFER_PROHIBITED("server transfer prohibited"),
    SERVER_UPDATE_PROHIBITED("server update prohibited"),
    SERVER_HOLD("server hold"),
    TRANSFER_PERIOD("transfer period");

    /** Value as it appears in RDAP messages. */
    private final String rfc7483String;

    private RdapStatus(String rfc7483String) {
      this.rfc7483String = rfc7483String;
    }

    public String getDisplayName() {
      return rfc7483String;
    }
  }

  /** Map of EPP status values to the RDAP equivalents. */
  private static final ImmutableMap<StatusValue, RdapStatus> statusToRdapStatusMap =
      Maps.immutableEnumMap(
          new ImmutableMap.Builder<StatusValue, RdapStatus>()
              // StatusValue.ADD_PERIOD not defined in our system
              // StatusValue.AUTO_RENEW_PERIOD not defined in our system
              .put(StatusValue.CLIENT_DELETE_PROHIBITED, RdapStatus.CLIENT_DELETE_PROHIBITED)
              .put(StatusValue.CLIENT_HOLD, RdapStatus.CLIENT_HOLD)
              .put(StatusValue.CLIENT_RENEW_PROHIBITED, RdapStatus.CLIENT_RENEW_PROHIBITED)
              .put(StatusValue.CLIENT_TRANSFER_PROHIBITED, RdapStatus.CLIENT_TRANSFER_PROHIBITED)
              .put(StatusValue.CLIENT_UPDATE_PROHIBITED, RdapStatus.CLIENT_UPDATE_PROHIBITED)
              .put(StatusValue.INACTIVE, RdapStatus.INACTIVE)
              .put(StatusValue.LINKED, RdapStatus.ASSOCIATED)
              .put(StatusValue.OK, RdapStatus.ACTIVE)
              .put(StatusValue.PENDING_CREATE, RdapStatus.PENDING_CREATE)
              .put(StatusValue.PENDING_DELETE, RdapStatus.PENDING_DELETE)
              // StatusValue.PENDING_RENEW not defined in our system
              // StatusValue.PENDING_RESTORE not defined in our system
              .put(StatusValue.PENDING_TRANSFER, RdapStatus.PENDING_TRANSFER)
              .put(StatusValue.PENDING_UPDATE, RdapStatus.PENDING_UPDATE)
              // StatusValue.REDEMPTION_PERIOD not defined in our system
              // StatusValue.RENEW_PERIOD not defined in our system
              .put(StatusValue.SERVER_DELETE_PROHIBITED, RdapStatus.SERVER_DELETE_PROHIBITED)
              .put(StatusValue.SERVER_HOLD, RdapStatus.SERVER_HOLD)
              .put(StatusValue.SERVER_RENEW_PROHIBITED, RdapStatus.SERVER_RENEW_PROHIBITED)
              .put(StatusValue.SERVER_TRANSFER_PROHIBITED, RdapStatus.SERVER_TRANSFER_PROHIBITED)
              .put(StatusValue.SERVER_UPDATE_PROHIBITED, RdapStatus.SERVER_UPDATE_PROHIBITED)
              // StatusValue.TRANSFER_PERIOD not defined in our system
              .build());

  /** Role values specified in RFC 7483 § 10.2.4. */
  private enum RdapEntityRole {
    REGISTRANT("registrant"),
    TECH("technical"),
    ADMIN("administrative"),
    ABUSE("abuse"),
    BILLING("billing"),
    REGISTRAR("registrar"),
    RESELLER("reseller"),
    SPONSOR("sponsor"),
    PROXY("proxy"),
    NOTIFICATIONS("notifications"),
    NOC("noc");

    /** Value as it appears in RDAP messages. */
    final String rfc7483String;

    private RdapEntityRole(String rfc7483String) {
      this.rfc7483String = rfc7483String;
    }
  }

  /** Status values specified in RFC 7483 § 10.2.2. */
  private enum RdapEventAction {
    REGISTRATION("registration"),
    REREGISTRATION("reregistration"),
    LAST_CHANGED("last changed"),
    EXPIRATION("expiration"),
    DELETION("deletion"),
    REINSTANTIATION("reinstantiation"),
    TRANSFER("transfer"),
    LOCKED("locked"),
    UNLOCKED("unlocked"),
    LAST_UPDATE_OF_RDAP_DATABASE("last update of RDAP database");

    /** Value as it appears in RDAP messages. */
    private final String rfc7483String;

    private RdapEventAction(String rfc7483String) {
      this.rfc7483String = rfc7483String;
    }

    public String getDisplayName() {
      return rfc7483String;
    }
  }

  /** Map of EPP event values to the RDAP equivalents. */
  private static final ImmutableMap<HistoryEntry.Type, RdapEventAction>
      historyEntryTypeToRdapEventActionMap =
          Maps.immutableEnumMap(
              new ImmutableMap.Builder<HistoryEntry.Type, RdapEventAction>()
                  .put(HistoryEntry.Type.CONTACT_CREATE, RdapEventAction.REGISTRATION)
                  .put(HistoryEntry.Type.CONTACT_DELETE, RdapEventAction.DELETION)
                  .put(HistoryEntry.Type.CONTACT_TRANSFER_APPROVE, RdapEventAction.TRANSFER)
                  .put(HistoryEntry.Type.DOMAIN_APPLICATION_CREATE, RdapEventAction.REGISTRATION)
                  .put(HistoryEntry.Type.DOMAIN_APPLICATION_DELETE, RdapEventAction.DELETION)
                  .put(HistoryEntry.Type.DOMAIN_AUTORENEW, RdapEventAction.REREGISTRATION)
                  .put(HistoryEntry.Type.DOMAIN_CREATE, RdapEventAction.REGISTRATION)
                  .put(HistoryEntry.Type.DOMAIN_DELETE, RdapEventAction.DELETION)
                  .put(HistoryEntry.Type.DOMAIN_RENEW, RdapEventAction.REREGISTRATION)
                  .put(HistoryEntry.Type.DOMAIN_RESTORE, RdapEventAction.REINSTANTIATION)
                  .put(HistoryEntry.Type.DOMAIN_TRANSFER_APPROVE, RdapEventAction.TRANSFER)
                  .put(HistoryEntry.Type.HOST_CREATE, RdapEventAction.REGISTRATION)
                  .put(HistoryEntry.Type.HOST_DELETE, RdapEventAction.DELETION)
                  .build());

  private static final ImmutableList<String> CONFORMANCE_LIST =
      ImmutableList.of(RDAP_CONFORMANCE_LEVEL);

  private static final ImmutableList<String> STATUS_LIST_ACTIVE =
      ImmutableList.of(RdapStatus.ACTIVE.rfc7483String);
  private static final ImmutableMap<String, ImmutableList<String>> PHONE_TYPE_VOICE =
      ImmutableMap.of("type", ImmutableList.of("voice"));
  private static final ImmutableMap<String, ImmutableList<String>> PHONE_TYPE_FAX =
      ImmutableMap.of("type", ImmutableList.of("fax"));
  private static final ImmutableList<?> VCARD_ENTRY_VERSION =
      ImmutableList.of("version", ImmutableMap.of(), "text", VCARD_VERSION_NUMBER);

  /** Sets the ordering for hosts; just use the fully qualified host name. */
  private static final Ordering<HostResource> HOST_RESOURCE_ORDERING =
      Ordering.natural().onResultOf(new Function<HostResource, String>() {
        @Override
        public String apply(HostResource host) {
          return host.getFullyQualifiedHostName();
        }});

  /** Sets the ordering for designated contacts; order them in a fixed order by contact type. */
  private static final Ordering<DesignatedContact> DESIGNATED_CONTACT_ORDERING =
      Ordering.natural().onResultOf(new Function<DesignatedContact, DesignatedContact.Type>() {
        @Override
        public DesignatedContact.Type apply(DesignatedContact designatedContact) {
          return designatedContact.getType();
        }});

  ImmutableMap<String, Object> getJsonTosNotice(String rdapLinkBase) {
    return getJsonHelpNotice(rdapTosPath, rdapLinkBase);
  }

  ImmutableMap<String, Object> getJsonHelpNotice(
      String pathSearchString, String rdapLinkBase) {
    if (pathSearchString.isEmpty()) {
      pathSearchString = "/";
    }
    if (!rdapHelpMap.containsKey(pathSearchString)) {
      throw new NotFoundException("no help found for " + pathSearchString);
    }
    try {
      return RdapJsonFormatter.makeRdapJsonNotice(rdapHelpMap.get(pathSearchString), rdapLinkBase);
    } catch (Exception e) {
      logger.warningfmt(e, "Error reading RDAP help file: %s", pathSearchString);
      throw new InternalServerErrorException("unable to read help for " + pathSearchString);
    }
  }

  /**
   * Adds the required top-level boilerplate. RFC 7483 specifies that the top-level object should
   * include an entry indicating the conformance level. The ICANN RDAP Profile document (dated 3
   * December 2015) mandates several additional entries, in sections 1.4.4, 1.4.10, 1.5.18 and
   * 1.5.20. Note that this method will only work if there are no object-specific remarks already in
   * the JSON object being built. If there are, the boilerplate must be merged in.
   *
   * @param jsonBuilder a builder for a JSON map object
   * @param boilerplateType type of boilerplate to be added; the ICANN RDAP Profile document
   *        mandates extra boilerplate for domain objects
   * @param notices a list of notices to be inserted before the boilerplate notices. If the TOS
   *        notice is in this list, the method avoids adding a second copy.
   * @param remarks a list of remarks to be inserted before the boilerplate notices.
   * @param rdapLinkBase the base for link URLs
   */
  void addTopLevelEntries(
      ImmutableMap.Builder<String, Object> jsonBuilder,
      BoilerplateType boilerplateType,
      List<ImmutableMap<String, Object>> notices,
      List<ImmutableMap<String, Object>> remarks,
      String rdapLinkBase) {
    jsonBuilder.put("rdapConformance", CONFORMANCE_LIST);
    ImmutableList.Builder<ImmutableMap<String, Object>> noticesBuilder =
        new ImmutableList.Builder<>();
    ImmutableMap<String, Object> tosNotice = getJsonTosNotice(rdapLinkBase);
    boolean tosNoticeFound = false;
    if (!notices.isEmpty()) {
      noticesBuilder.addAll(notices);
      for (ImmutableMap<String, Object> notice : notices) {
        if (notice.equals(tosNotice)) {
          tosNoticeFound = true;
          break;
        }
      }
    }
    if (!tosNoticeFound) {
      noticesBuilder.add(tosNotice);
    }
    jsonBuilder.put(NOTICES, noticesBuilder.build());
    ImmutableList.Builder<ImmutableMap<String, Object>> remarksBuilder =
        new ImmutableList.Builder<>();
    remarksBuilder.addAll(remarks);
    switch (boilerplateType) {
      case DOMAIN:
        remarksBuilder.addAll(RdapIcannStandardInformation.domainBoilerplateRemarks);
        break;
      case NAMESERVER:
      case ENTITY:
        remarksBuilder.addAll(RdapIcannStandardInformation.nameserverAndEntityBoilerplateRemarks);
        break;
      default: // things other than domains, nameservers and entities cannot contain remarks
        break;
    }
    ImmutableList<ImmutableMap<String, Object>> remarksToAdd = remarksBuilder.build();
    if (!remarksToAdd.isEmpty()) {
      jsonBuilder.put(REMARKS, remarksToAdd);
    }
  }

  /**
   * Creates a JSON object containing a notice or remark object, as defined by RFC 7483 § 4.3.
   * The object should then be inserted into a notices or remarks array. The builder fields are:
   *
   * <p>title: the title of the notice; if null, the notice will have no title
   *
   * <p>description: objects which will be converted to strings to form the description of the
   * notice (this is the only required field; all others are optional)
   *
   * <p>typeString: the notice or remark type as defined in § 10.2.1; if null, no type
   *
   * <p>linkValueSuffix: the path at the end of the URL used in the value field of the link,
   * without any initial slash (e.g. a suffix of help/toc equates to a URL of
   * http://example.net/help/toc); if null, no link is created; if it is not null, a single link is
   * created; this method never creates more than one link)
   *
   * <p>htmlUrlString: the path, if any, to be used in the href value of the link; if the URL is
   * absolute, it is used as is; if it is relative, starting with a slash, it is appended to the
   * protocol and host of the link base; if it is relative, not starting with a slash, it is
   * appended to the complete link base; if null, a self link is generated instead, using the link
   * link value
   *
   * <p>linkBase: the base for the link value and href; if null, it is assumed to be the empty
   * string
   *
   * @see <a href="https://tools.ietf.org/html/rfc7483">
   *     RFC 7483: JSON Responses for the Registration Data Access Protocol (RDAP)</a>
   */
  static ImmutableMap<String, Object> makeRdapJsonNotice(
      RdapNoticeDescriptor parameters, @Nullable String linkBase) {
    ImmutableMap.Builder<String, Object> jsonBuilder = new ImmutableMap.Builder<>();
    if (parameters.getTitle() != null) {
      jsonBuilder.put("title", parameters.getTitle());
    }
    ImmutableList.Builder<String> descriptionBuilder = new ImmutableList.Builder<>();
    for (String line : parameters.getDescription()) {
      descriptionBuilder.add(nullToEmpty(line));
    }
    jsonBuilder.put("description", descriptionBuilder.build());
    if (parameters.getTypeString() != null) {
      jsonBuilder.put("typeString", parameters.getTypeString());
    }
    String linkValueString =
        nullToEmpty(linkBase) + nullToEmpty(parameters.getLinkValueSuffix());
    if (parameters.getLinkHrefUrlString() == null) {
      jsonBuilder.put("links", ImmutableList.of(ImmutableMap.of(
          "value", linkValueString,
          "rel", "self",
          "href", linkValueString,
          "type", "application/rdap+json")));
    } else {
      URI htmlBaseURI = URI.create(nullToEmpty(linkBase));
      URI htmlUri = htmlBaseURI.resolve(parameters.getLinkHrefUrlString());
      jsonBuilder.put("links", ImmutableList.of(ImmutableMap.of(
          "value", linkValueString,
          "rel", "alternate",
          "href", htmlUri.toString(),
          "type", "text/html")));
    }
    return jsonBuilder.build();
  }

  /**
   * Creates a JSON object for a {@link DomainResource}.
   *
   * @param domainResource the domain resource object from which the JSON object should be created
   * @param isTopLevel if true, the top-level boilerplate will be added
   * @param linkBase the URL base to be used when creating links
   * @param whoisServer the fully-qualified domain name of the WHOIS server to be listed in the
   *        port43 field; if null, port43 is not added to the object
   * @param now the as-date
   * @param outputDataType whether to generate full or summary data
   * @param authorization the authorization level of the request; if not authorized for the
   *        registrar owning the domain, no contact information is included
   */
  ImmutableMap<String, Object> makeRdapJsonForDomain(
      DomainResource domainResource,
      boolean isTopLevel,
      @Nullable String linkBase,
      @Nullable String whoisServer,
      DateTime now,
      OutputDataType outputDataType,
      RdapAuthorization authorization) {
    // Start with the domain-level information.
    ImmutableMap.Builder<String, Object> jsonBuilder = new ImmutableMap.Builder<>();
    jsonBuilder.put("objectClassName", "domain");
    jsonBuilder.put("handle", domainResource.getRepoId());
    jsonBuilder.put("ldhName", domainResource.getFullyQualifiedDomainName());
    // Only include the unicodeName field if there are unicode characters.
    if (hasUnicodeComponents(domainResource.getFullyQualifiedDomainName())) {
      jsonBuilder.put("unicodeName", Idn.toUnicode(domainResource.getFullyQualifiedDomainName()));
    }
    jsonBuilder.put("status", makeStatusValueList(domainResource.getStatusValues()));
    jsonBuilder.put("links", ImmutableList.of(
        makeLink("domain", domainResource.getFullyQualifiedDomainName(), linkBase)));
    boolean displayContacts =
        authorization.isAuthorizedForClientId(domainResource.getCurrentSponsorClientId());
    // If we are outputting all data (not just summary data), also add information about hosts,
    // contacts and events (history entries). If we are outputting summary data, instead add a
    // remark indicating that fact.
    List<ImmutableMap<String, Object>> remarks;
    if (outputDataType == OutputDataType.SUMMARY) {
      remarks = ImmutableList.of(RdapIcannStandardInformation.SUMMARY_DATA_REMARK);
    } else {
      remarks = displayContacts
        ? ImmutableList.<ImmutableMap<String, Object>>of()
        : ImmutableList.of(RdapIcannStandardInformation.DOMAIN_CONTACTS_HIDDEN_DATA_REMARK);
      ImmutableList<Object> events = makeEvents(domainResource, now);
      if (!events.isEmpty()) {
        jsonBuilder.put("events", events);
      }
      // Kick off the database loads of the nameservers that we will need, so it can load
      // asynchronously while we load and process the contacts.
      Map<Key<HostResource>, HostResource> loadedHosts =
          ofy().load().keys(domainResource.getNameservers());
      // Load the registrant and other contacts and add them to the data.
      if (displayContacts) {
        Map<Key<ContactResource>, ContactResource> loadedContacts =
            ofy().load().keys(domainResource.getReferencedContacts());
        ImmutableList.Builder<Object> entitiesBuilder = new ImmutableList.Builder<>();
        for (DesignatedContact designatedContact :
            FluentIterable.from(domainResource.getContacts())
                .append(DesignatedContact.create(Type.REGISTRANT, domainResource.getRegistrant()))
                .toSortedList(DESIGNATED_CONTACT_ORDERING)) {
          ContactResource loadedContact = loadedContacts.get(designatedContact.getContactKey());
          entitiesBuilder.add(makeRdapJsonForContact(
              loadedContact,
              false,
              Optional.of(designatedContact.getType()),
              linkBase,
              null,
              now,
              outputDataType,
              authorization));
        }
        ImmutableList<Object> entities = entitiesBuilder.build();
        if (!entities.isEmpty()) {
          jsonBuilder.put("entities", entities);
        }
      }
      // Add the nameservers to the data; the load was kicked off above for efficiency.
      ImmutableList.Builder<Object> nsBuilder = new ImmutableList.Builder<>();
      for (HostResource hostResource
          : HOST_RESOURCE_ORDERING.immutableSortedCopy(loadedHosts.values())) {
        nsBuilder.add(makeRdapJsonForHost(
            hostResource, false, linkBase, null, now, outputDataType));
      }
      ImmutableList<Object> ns = nsBuilder.build();
      if (!ns.isEmpty()) {
        jsonBuilder.put("nameservers", ns);
      }
    }
    if (whoisServer != null) {
      jsonBuilder.put("port43", whoisServer);
    }
    if (isTopLevel) {
      addTopLevelEntries(
          jsonBuilder,
          BoilerplateType.DOMAIN,
          remarks,
          ImmutableList.<ImmutableMap<String, Object>>of(), linkBase);
    } else if (!remarks.isEmpty()) {
      jsonBuilder.put(REMARKS, remarks);
    }
    return jsonBuilder.build();
  }

  /**
   * Creates a JSON object for a {@link HostResource}.
   *
   * @param hostResource the host resource object from which the JSON object should be created
   * @param isTopLevel if true, the top-level boilerplate will be added
   * @param linkBase the URL base to be used when creating links
   * @param whoisServer the fully-qualified domain name of the WHOIS server to be listed in the
   *        port43 field; if null, port43 is not added to the object
   * @param now the as-date
   * @param outputDataType whether to generate full or summary data
   */
  ImmutableMap<String, Object> makeRdapJsonForHost(
      HostResource hostResource,
      boolean isTopLevel,
      @Nullable String linkBase,
      @Nullable String whoisServer,
      DateTime now,
      OutputDataType outputDataType) {
    ImmutableMap.Builder<String, Object> jsonBuilder = new ImmutableMap.Builder<>();
    jsonBuilder.put("objectClassName", "nameserver");
    jsonBuilder.put("handle", hostResource.getRepoId());
    jsonBuilder.put("ldhName", hostResource.getFullyQualifiedHostName());
    // Only include the unicodeName field if there are unicode characters.
    if (hasUnicodeComponents(hostResource.getFullyQualifiedHostName())) {
      jsonBuilder.put("unicodeName", Idn.toUnicode(hostResource.getFullyQualifiedHostName()));
    }

    ImmutableSet.Builder<StatusValue> statuses = new ImmutableSet.Builder<>();
    statuses.addAll(hostResource.getStatusValues());
    if (isLinked(Key.create(hostResource), now)) {
      statuses.add(StatusValue.LINKED);
    }
    if (hostResource.isSubordinate()
        && ofy().load().key(hostResource.getSuperordinateDomain()).now().cloneProjectedAtTime(now)
            .getStatusValues()
                .contains(StatusValue.PENDING_TRANSFER)) {
      statuses.add(StatusValue.PENDING_TRANSFER);
    }
    jsonBuilder.put("status", makeStatusValueList(statuses.build()));
    jsonBuilder.put("links", ImmutableList.of(
        makeLink("nameserver", hostResource.getFullyQualifiedHostName(), linkBase)));
    List<ImmutableMap<String, Object>> remarks;
    // If we are outputting all data (not just summary data), also add events taken from the history
    // entries. If we are outputting summary data, instead add a remark indicating that fact.
    if (outputDataType == OutputDataType.SUMMARY) {
      remarks = ImmutableList.of(RdapIcannStandardInformation.SUMMARY_DATA_REMARK);
    } else {
      remarks = ImmutableList.of();
      ImmutableList<Object> events = makeEvents(hostResource, now);
      if (!events.isEmpty()) {
        jsonBuilder.put("events", events);
      }
    }
    ImmutableSet<InetAddress> inetAddresses = hostResource.getInetAddresses();
    if (!inetAddresses.isEmpty()) {
      ImmutableList.Builder<String> v4AddressesBuilder = new ImmutableList.Builder<>();
      ImmutableList.Builder<String> v6AddressesBuilder = new ImmutableList.Builder<>();
      for (InetAddress inetAddress : inetAddresses) {
        if (inetAddress instanceof Inet4Address) {
          v4AddressesBuilder.add(InetAddresses.toAddrString(inetAddress));
        } else if (inetAddress instanceof Inet6Address) {
          v6AddressesBuilder.add(InetAddresses.toAddrString(inetAddress));
        }
      }
      ImmutableMap.Builder<String, ImmutableList<String>> ipAddressesBuilder =
          new ImmutableMap.Builder<>();
      ImmutableList<String> v4Addresses = v4AddressesBuilder.build();
      if (!v4Addresses.isEmpty()) {
        ipAddressesBuilder.put("v4", Ordering.natural().immutableSortedCopy(v4Addresses));
      }
      ImmutableList<String> v6Addresses = v6AddressesBuilder.build();
      if (!v6Addresses.isEmpty()) {
        ipAddressesBuilder.put("v6", Ordering.natural().immutableSortedCopy(v6Addresses));
      }
      ImmutableMap<String, ImmutableList<String>> ipAddresses = ipAddressesBuilder.build();
      if (!ipAddresses.isEmpty()) {
        jsonBuilder.put("ipAddresses", ipAddressesBuilder.build());
      }
    }
    if (whoisServer != null) {
      jsonBuilder.put("port43", whoisServer);
    }
    if (isTopLevel) {
      addTopLevelEntries(
          jsonBuilder,
          BoilerplateType.NAMESERVER,
          remarks,
          ImmutableList.<ImmutableMap<String, Object>>of(), linkBase);
    } else if (!remarks.isEmpty()) {
      jsonBuilder.put(REMARKS, remarks);
    }
    return jsonBuilder.build();
  }

  /**
   * Creates a JSON object for a {@link ContactResource} and associated contact type.
   *
   * @param contactResource the contact resource object from which the JSON object should be created
   * @param isTopLevel if true, the top-level boilerplate will be added
   * @param contactType the contact type to map to an RDAP role; if absent, no role is listed
   * @param linkBase the URL base to be used when creating links
   * @param whoisServer the fully-qualified domain name of the WHOIS server to be listed in the
   *        port43 field; if null, port43 is not added to the object
   * @param now the as-date
   * @param outputDataType whether to generate full or summary data
   * @param authorization the authorization level of the request; personal contact data is only
   *        shown if the contact is owned by a registrar for which the request is authorized
   */
  ImmutableMap<String, Object> makeRdapJsonForContact(
      ContactResource contactResource,
      boolean isTopLevel,
      Optional<DesignatedContact.Type> contactType,
      @Nullable String linkBase,
      @Nullable String whoisServer,
      DateTime now,
      OutputDataType outputDataType,
      RdapAuthorization authorization) {
    ImmutableMap.Builder<String, Object> jsonBuilder = new ImmutableMap.Builder<>();
    ImmutableList.Builder<ImmutableMap<String, Object>> remarksBuilder
        = new ImmutableList.Builder<>();
    jsonBuilder.put("objectClassName", "entity");
    jsonBuilder.put("handle", contactResource.getRepoId());
    jsonBuilder.put("status", makeStatusValueList(
        isLinked(Key.create(contactResource), now)
            ? union(contactResource.getStatusValues(), StatusValue.LINKED)
            : contactResource.getStatusValues()));
    if (contactType.isPresent()) {
      jsonBuilder.put("roles",
          ImmutableList.of(convertContactTypeToRdapRole(contactType.get())));
    }
    jsonBuilder.put("links",
        ImmutableList.of(makeLink("entity", contactResource.getRepoId(), linkBase)));
    // If we are logged in as the owner of this contact, create the vCard.
    if (authorization.isAuthorizedForClientId(contactResource.getCurrentSponsorClientId())) {
      ImmutableList.Builder<Object> vcardBuilder = new ImmutableList.Builder<>();
      vcardBuilder.add(VCARD_ENTRY_VERSION);
      PostalInfo postalInfo = contactResource.getInternationalizedPostalInfo();
      if (postalInfo == null) {
        postalInfo = contactResource.getLocalizedPostalInfo();
      }
      if (postalInfo != null) {
        if (postalInfo.getName() != null) {
          vcardBuilder.add(ImmutableList.of("fn", ImmutableMap.of(), "text", postalInfo.getName()));
        }
        if (postalInfo.getOrg() != null) {
          vcardBuilder.add(ImmutableList.of("org", ImmutableMap.of(), "text", postalInfo.getOrg()));
        }
        ImmutableList<Object> addressEntry = makeVCardAddressEntry(postalInfo.getAddress());
        if (addressEntry != null) {
          vcardBuilder.add(addressEntry);
        }
      }
      ContactPhoneNumber voicePhoneNumber = contactResource.getVoiceNumber();
      if (voicePhoneNumber != null) {
        vcardBuilder.add(makePhoneEntry(PHONE_TYPE_VOICE, makePhoneString(voicePhoneNumber)));
      }
      ContactPhoneNumber faxPhoneNumber = contactResource.getFaxNumber();
      if (faxPhoneNumber != null) {
        vcardBuilder.add(makePhoneEntry(PHONE_TYPE_FAX, makePhoneString(faxPhoneNumber)));
      }
      String emailAddress = contactResource.getEmailAddress();
      if (emailAddress != null) {
        vcardBuilder.add(ImmutableList.of("email", ImmutableMap.of(), "text", emailAddress));
      }
      jsonBuilder.put("vcardArray", ImmutableList.of("vcard", vcardBuilder.build()));
    } else {
      remarksBuilder.add(RdapIcannStandardInformation.CONTACT_PERSONAL_DATA_HIDDEN_DATA_REMARK);
    }
    // If we are outputting all data (not just summary data), also add events taken from the history
    // entries. If we are outputting summary data, instead add a remark indicating that fact.
    if (outputDataType == OutputDataType.SUMMARY) {
      remarksBuilder.add(RdapIcannStandardInformation.SUMMARY_DATA_REMARK);
    } else {
      ImmutableList<Object> events = makeEvents(contactResource, now);
      if (!events.isEmpty()) {
        jsonBuilder.put("events", events);
      }
    }
    if (whoisServer != null) {
      jsonBuilder.put("port43", whoisServer);
    }
    if (isTopLevel) {
      addTopLevelEntries(
          jsonBuilder,
          BoilerplateType.ENTITY,
          remarksBuilder.build(),
          ImmutableList.<ImmutableMap<String, Object>>of(),
          linkBase);
    } else {
      ImmutableList<ImmutableMap<String, Object>> remarks = remarksBuilder.build();
      if (!remarks.isEmpty()) {
        jsonBuilder.put(REMARKS, remarks);
      }
    }
    return jsonBuilder.build();
  }

  /**
   * Creates a JSON object for a {@link Registrar}.
   *
   * @param registrar the registrar object from which the JSON object should be created
   * @param isTopLevel if true, the top-level boilerplate will be added
   * @param linkBase the URL base to be used when creating links
   * @param whoisServer the fully-qualified domain name of the WHOIS server to be listed in the
   *        port43 field; if null, port43 is not added to the object
   * @param now the as-date
   * @param outputDataType whether to generate full or summary data
   */
  ImmutableMap<String, Object> makeRdapJsonForRegistrar(
      Registrar registrar,
      boolean isTopLevel,
      @Nullable String linkBase,
      @Nullable String whoisServer,
      DateTime now,
      OutputDataType outputDataType) {
    ImmutableMap.Builder<String, Object> jsonBuilder = new ImmutableMap.Builder<>();
    jsonBuilder.put("objectClassName", "entity");
    jsonBuilder.put("handle", registrar.getIanaIdentifier().toString());
    jsonBuilder.put("status", STATUS_LIST_ACTIVE);
    jsonBuilder.put("roles", ImmutableList.of(RdapEntityRole.REGISTRAR.rfc7483String));
    jsonBuilder.put("links",
        ImmutableList.of(makeLink("entity", registrar.getIanaIdentifier().toString(), linkBase)));
    jsonBuilder.put("publicIds",
        ImmutableList.of(
            ImmutableMap.of(
                "type", "IANA Registrar ID",
                "identifier", registrar.getIanaIdentifier().toString())));
    // Create the vCard.
    ImmutableList.Builder<Object> vcardBuilder = new ImmutableList.Builder<>();
    vcardBuilder.add(VCARD_ENTRY_VERSION);
    String registrarName = registrar.getRegistrarName();
    if (registrarName != null) {
      vcardBuilder.add(ImmutableList.of("fn", ImmutableMap.of(), "text", registrarName));
    }
    RegistrarAddress address = registrar.getInternationalizedAddress();
    if (address == null) {
      address = registrar.getLocalizedAddress();
    }
    if (address != null) {
      ImmutableList<Object> addressEntry = makeVCardAddressEntry(address);
      if (addressEntry != null) {
        vcardBuilder.add(addressEntry);
      }
    }
    String voicePhoneNumber = registrar.getPhoneNumber();
    if (voicePhoneNumber != null) {
      vcardBuilder.add(makePhoneEntry(PHONE_TYPE_VOICE, "tel:" + voicePhoneNumber));
    }
    String faxPhoneNumber = registrar.getFaxNumber();
    if (faxPhoneNumber != null) {
      vcardBuilder.add(makePhoneEntry(PHONE_TYPE_FAX, "tel:" + faxPhoneNumber));
    }
    String emailAddress = registrar.getEmailAddress();
    if (emailAddress != null) {
      vcardBuilder.add(ImmutableList.of("email", ImmutableMap.of(), "text", emailAddress));
    }
    jsonBuilder.put("vcardArray", ImmutableList.of("vcard", vcardBuilder.build()));
    // If we are outputting all data (not just summary data), also add registrar contacts. If we are
    // outputting summary data, instead add a remark indicating that fact.
    List<ImmutableMap<String, Object>> remarks;
    if (outputDataType == OutputDataType.SUMMARY) {
      remarks = ImmutableList.of(RdapIcannStandardInformation.SUMMARY_DATA_REMARK);
    } else {
      remarks = ImmutableList.of();
      ImmutableList<Object> events = makeEvents(registrar, now);
      if (!events.isEmpty()) {
        jsonBuilder.put("events", events);
      }
      // include the registrar contacts as subentities
      ImmutableList.Builder<Map<String, Object>> registrarContactsBuilder =
          new ImmutableList.Builder<>();
      for (RegistrarContact registrarContact : registrar.getContacts()) {
        if (isVisible(registrarContact)) {
          registrarContactsBuilder.add(makeRdapJsonForRegistrarContact(registrarContact, null));
        }
      }
      ImmutableList<Map<String, Object>> registrarContacts = registrarContactsBuilder.build();
      if (!registrarContacts.isEmpty()) {
        jsonBuilder.put("entities", registrarContacts);
      }
    }
    if (whoisServer != null) {
      jsonBuilder.put("port43", whoisServer);
    }
    if (isTopLevel) {
      addTopLevelEntries(
          jsonBuilder,
          BoilerplateType.ENTITY,
          remarks,
          ImmutableList.<ImmutableMap<String, Object>>of(),
          linkBase);
    } else if (!remarks.isEmpty()) {
      jsonBuilder.put(REMARKS, remarks);
    }
    return jsonBuilder.build();
  }

  /**
   * Creates a JSON object for a {@link RegistrarContact}.
   *
   * @param registrarContact the registrar contact for which the JSON object should be created
   * @param whoisServer the fully-qualified domain name of the WHOIS server to be listed in the
   *        port43 field; if null, port43 is not added to the object
   */
  static ImmutableMap<String, Object> makeRdapJsonForRegistrarContact(
      RegistrarContact registrarContact, @Nullable String whoisServer) {
    ImmutableMap.Builder<String, Object> jsonBuilder = new ImmutableMap.Builder<>();
    jsonBuilder.put("objectClassName", "entity");
    String gaeUserId = registrarContact.getGaeUserId();
    if (gaeUserId != null) {
      jsonBuilder.put("handle", registrarContact.getGaeUserId());
    }
    jsonBuilder.put("status", STATUS_LIST_ACTIVE);
    jsonBuilder.put("roles", makeRdapRoleList(registrarContact));
    // Create the vCard.
    ImmutableList.Builder<Object> vcardBuilder = new ImmutableList.Builder<>();
    vcardBuilder.add(VCARD_ENTRY_VERSION);
    String name = registrarContact.getName();
    if (name != null) {
      vcardBuilder.add(ImmutableList.of("fn", ImmutableMap.of(), "text", name));
    }
    String voicePhoneNumber = registrarContact.getPhoneNumber();
    if (voicePhoneNumber != null) {
      vcardBuilder.add(makePhoneEntry(PHONE_TYPE_VOICE, "tel:" + voicePhoneNumber));
    }
    String faxPhoneNumber = registrarContact.getFaxNumber();
    if (faxPhoneNumber != null) {
      vcardBuilder.add(makePhoneEntry(PHONE_TYPE_FAX, "tel:" + faxPhoneNumber));
    }
    String emailAddress = registrarContact.getEmailAddress();
    if (emailAddress != null) {
      vcardBuilder.add(ImmutableList.of("email", ImmutableMap.of(), "text", emailAddress));
    }
    jsonBuilder.put("vcardArray", ImmutableList.of("vcard", vcardBuilder.build()));
    if (whoisServer != null) {
      jsonBuilder.put("port43", whoisServer);
    }
    return jsonBuilder.build();
  }

  /** Converts a domain registry contact type into a role as defined by RFC 7483. */
  private static String convertContactTypeToRdapRole(DesignatedContact.Type contactType) {
    switch (contactType) {
      case REGISTRANT:
        return RdapEntityRole.REGISTRANT.rfc7483String;
      case TECH:
        return RdapEntityRole.TECH.rfc7483String;
      case BILLING:
        return RdapEntityRole.BILLING.rfc7483String;
      case ADMIN:
        return RdapEntityRole.ADMIN.rfc7483String;
      default:
        throw new AssertionError();
    }
  }

  /**
   * Creates the list of RDAP roles for a registrar contact, using the visibleInWhoisAs* flags.
   */
  private static ImmutableList<String> makeRdapRoleList(RegistrarContact registrarContact) {
    ImmutableList.Builder<String> rolesBuilder = new ImmutableList.Builder<>();
    if (registrarContact.getVisibleInWhoisAsAdmin()) {
      rolesBuilder.add(RdapEntityRole.ADMIN.rfc7483String);
    }
    if (registrarContact.getVisibleInWhoisAsTech()) {
      rolesBuilder.add(RdapEntityRole.TECH.rfc7483String);
    }
    return rolesBuilder.build();
  }

  /** Checks whether the registrar contact should be visible (because it has visible roles). */
  private static boolean isVisible(RegistrarContact registrarContact) {
    return registrarContact.getVisibleInWhoisAsAdmin()
        || registrarContact.getVisibleInWhoisAsTech();
  }

  /**
   * Creates an event list for a domain, host or contact resource.
   */
  private static ImmutableList<Object> makeEvents(EppResource resource, DateTime now) {
    ImmutableList.Builder<Object> eventsBuilder = new ImmutableList.Builder<>();
    for (HistoryEntry historyEntry : ofy().load()
        .type(HistoryEntry.class)
        .ancestor(resource)
        .order("modificationTime")) {
      // Only create an event if this is a type we care about.
      if (!historyEntryTypeToRdapEventActionMap.containsKey(historyEntry.getType())) {
        continue;
      }
      RdapEventAction eventAction =
          historyEntryTypeToRdapEventActionMap.get(historyEntry.getType());
      eventsBuilder.add(makeEvent(
          eventAction, historyEntry.getClientId(), historyEntry.getModificationTime()));
    }
    if (resource instanceof DomainResource) {
      DateTime expirationTime = ((DomainResource) resource).getRegistrationExpirationTime();
      if (expirationTime != null) {
        eventsBuilder.add(makeEvent(RdapEventAction.EXPIRATION, null, expirationTime));
      }
    }
    if ((resource.getLastEppUpdateTime() != null)
        && resource.getLastEppUpdateTime().isAfter(resource.getCreationTime())) {
      eventsBuilder.add(makeEvent(
          RdapEventAction.LAST_CHANGED, null, resource.getLastEppUpdateTime()));
    }
    eventsBuilder.add(makeEvent(RdapEventAction.LAST_UPDATE_OF_RDAP_DATABASE, null, now));
    return eventsBuilder.build();
  }

  /**
   * Creates an event list for a {@link Registrar}.
   */
  private static ImmutableList<Object> makeEvents(Registrar registrar, DateTime now) {
    ImmutableList.Builder<Object> eventsBuilder = new ImmutableList.Builder<>();
    eventsBuilder.add(makeEvent(
        RdapEventAction.REGISTRATION,
        registrar.getIanaIdentifier().toString(),
        registrar.getCreationTime()));
    if ((registrar.getLastUpdateTime() != null)
        && registrar.getLastUpdateTime().isAfter(registrar.getCreationTime())) {
      eventsBuilder.add(makeEvent(
          RdapEventAction.LAST_CHANGED, null, registrar.getLastUpdateTime()));
    }
    eventsBuilder.add(makeEvent(RdapEventAction.LAST_UPDATE_OF_RDAP_DATABASE, null, now));
    return eventsBuilder.build();
  }

  /**
   * Creates an RDAP event object as defined by RFC 7483.
   */
  private static ImmutableMap<String, Object> makeEvent(
      RdapEventAction eventAction, @Nullable String eventActor, DateTime eventDate) {
    ImmutableMap.Builder<String, Object> jsonBuilder = new ImmutableMap.Builder<>();
    jsonBuilder.put("eventAction", eventAction.getDisplayName());
    if (eventActor != null) {
      jsonBuilder.put("eventActor", eventActor);
    }
    jsonBuilder.put("eventDate", eventDate.toString());
    return jsonBuilder.build();
  }

  /**
   * Creates a vCard address entry: array of strings specifying the components of the address.
   *
   * @see <a href="https://tools.ietf.org/html/rfc7095">
   *        RFC 7095: jCard: The JSON Format for vCard</a>
   */
  private static ImmutableList<Object> makeVCardAddressEntry(Address address) {
    if (address == null) {
      return null;
    }
    ImmutableList.Builder<Object> jsonBuilder = new ImmutableList.Builder<>();
    jsonBuilder.add(""); // PO box
    jsonBuilder.add(""); // extended address

    // The vCard spec allows several different ways to handle multiline street addresses. Per
    // Gustavo Lozano of ICANN, the one we should use is an embedded array of street address lines
    // if there is more than one line:
    //
    //   RFC7095 provides two examples of structured addresses, and one of the examples shows a
    //   street JSON element that contains several data elements. The example showing (see below)
    //   several data elements is the expected output when two or more <contact:street> elements
    //   exists in the contact object.
    //
    //   ["adr", {}, "text",
    //    [
    //    "", "",
    //    ["My Street", "Left Side", "Second Shack"],
    //    "Hometown", "PA", "18252", "U.S.A."
    //    ]
    //   ]
    ImmutableList<String> street = address.getStreet();
    if (street.isEmpty()) {
      jsonBuilder.add("");
    } else if (street.size() == 1) {
      jsonBuilder.add(street.get(0));
    } else {
      jsonBuilder.add(street);
    }
    jsonBuilder.add(nullToEmpty(address.getCity()));
    jsonBuilder.add(nullToEmpty(address.getState()));
    jsonBuilder.add(nullToEmpty(address.getZip()));
    jsonBuilder.add(new Locale("en", address.getCountryCode()).getDisplayCountry(new Locale("en")));
    return ImmutableList.<Object>of(
        "adr",
        ImmutableMap.of(),
        "text",
        jsonBuilder.build());
  }

  /** Creates a vCard phone number entry. */
  private static ImmutableList<Object> makePhoneEntry(
      ImmutableMap<String, ImmutableList<String>> type, String phoneNumber) {
    return ImmutableList.<Object>of("tel", type, "uri", phoneNumber);
  }

  /** Creates a phone string in URI format, as per the vCard spec. */
  private static String makePhoneString(ContactPhoneNumber phoneNumber) {
    String phoneString = String.format("tel:%s", phoneNumber.getPhoneNumber());
    if (phoneNumber.getExtension() != null) {
      phoneString = phoneString + ";ext=" + phoneNumber.getExtension();
    }
    return phoneString;
  }

  /**
   * Creates a string array of status values; the spec indicates that OK should be listed as
   * "active".
   */
  private static ImmutableList<String> makeStatusValueList(ImmutableSet<StatusValue> statusValues) {
    return FluentIterable
        .from(statusValues)
        .transform(Functions.forMap(statusToRdapStatusMap, RdapStatus.OBSCURED))
        .transform(new Function<RdapStatus, String>() {
          @Override
          public String apply(RdapStatus status) {
            return status.getDisplayName();
          }})
        .toSortedSet(Ordering.natural())
        .asList();
  }

  /**
   * Creates a self link as directed by the spec.
   *
   * @see <a href="https://tools.ietf.org/html/rfc7483">
   *        RFC 7483: JSON Responses for the Registration Data Access Protocol (RDAP)</a>
   */
  private static ImmutableMap<String, String> makeLink(
      String type, String name, @Nullable String linkBase) {
    String url;
    if (linkBase == null) {
      url = type + '/' + name;
    } else if (linkBase.endsWith("/")) {
      url = linkBase + type + '/' + name;
    } else {
      url = linkBase + '/' + type + '/' + name;
    }
    return ImmutableMap.of(
        "value", url,
        "rel", "self",
        "href", url,
        "type", "application/rdap+json");
  }

  /**
   * Creates a JSON error indication.
   *
   * @see <a href="https://tools.ietf.org/html/rfc7483">
   *        RFC 7483: JSON Responses for the Registration Data Access Protocol (RDAP)</a>
   */
  ImmutableMap<String, Object> makeError(int status, String title, String description) {
    return ImmutableMap.<String, Object>of(
        "rdapConformance", CONFORMANCE_LIST,
        "lang", "en",
        "errorCode", (long) status,
        "title", title,
        "description", ImmutableList.of(description));
  }

  private static boolean hasUnicodeComponents(String fullyQualifiedName) {
    return fullyQualifiedName.startsWith(ACE_PREFIX)
        || fullyQualifiedName.contains("." + ACE_PREFIX);
  }
}
