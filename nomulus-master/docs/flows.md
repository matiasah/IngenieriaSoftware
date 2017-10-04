# Nomulus EPP Command API Documentation

## ClaimsCheckFlow

### Description

An EPP flow that checks whether strings are trademarked.


### Errors

*   2002
    *   Command is not allowed in the current registry phase.
    *   Claims checks are not allowed during sunrise.
*   2004
    *   Domain name is under tld which doesn't exist.
*   2201
    *   Registrar is not authorized to access this TLD.
*   2304
    *   The claims period for this TLD has ended.
*   2306
    *   Too many resource checks requested in one check command.

## ContactCheckFlow

### Description

An EPP flow that checks whether a contact can be provisioned.

This flows can check the existence of multiple contacts simultaneously.


### Errors

*   2306
    *   Too many resource checks requested in one check command.

## ContactCreateFlow

### Description

An EPP flow that creates a new contact.


### Errors

*   2005
    *   Internationalized postal infos can only contain ASCII characters.
*   2302
    *   Resource with this id already exists.
*   2306
    *   Declining contact disclosure is disallowed by server policy.

## ContactDeleteFlow

### Description

An EPP flow that deletes a contact.

Contacts that are in use by any domain cannot be deleted. The flow may return
immediately if a quick smoke check determines that deletion is impossible due to
an existing reference. However, a successful delete will always be asynchronous,
as all existing domains must be checked for references to the host before the
deletion is allowed to proceed. A poll message will be written with the success
or failure message when the process is complete.


### Errors

*   2201
    *   The specified resource belongs to another client.
*   2303
    *   Resource with this id does not exist.
*   2304
    *   Resource status prohibits this operation.
*   2305
    *   Resource to be deleted has active incoming references.

## ContactInfoFlow

### Description

An EPP flow that returns information about a contact.

The response includes the contact's postal info, phone numbers, emails, the
authInfo which can be used to request a transfer and the details of the
contact's most recent transfer if it has ever been transferred. Any registrar
can see any contact's information, but the authInfo is only visible to the
registrar that owns the contact or to a registrar that already supplied it.


### Errors

*   2303
    *   Resource with this id does not exist.

## ContactTransferApproveFlow

### Description

An EPP flow that approves a pending transfer on a contact.

The "gaining" registrar requests a transfer from the "losing" (aka current)
registrar. The losing registrar has a "transfer" time period to respond (by
default five days) after which the transfer is automatically approved. Within
that window, this flow allows the losing client to explicitly approve the
transfer request, which then becomes effective immediately.


### Errors

*   2201
    *   The specified resource belongs to another client.
*   2202
    *   Authorization information for accessing resource is invalid.
*   2301
    *   The resource does not have a pending transfer.
*   2303
    *   Resource with this id does not exist.

## ContactTransferCancelFlow

### Description

An EPP flow that cancels a pending transfer on a contact.

The "gaining" registrar requests a transfer from the "losing" (aka current)
registrar. The losing registrar has a "transfer" time period to respond (by
default five days) after which the transfer is automatically approved. Within
that window, this flow allows the gaining client to withdraw the transfer
request.


### Errors

*   2201
    *   Registrar is not the initiator of this transfer.
*   2202
    *   Authorization information for accessing resource is invalid.
*   2301
    *   The resource does not have a pending transfer.
*   2303
    *   Resource with this id does not exist.

## ContactTransferQueryFlow

### Description

An EPP flow that queries a pending transfer on a contact.

The "gaining" registrar requests a transfer from the "losing" (aka current)
registrar. The losing registrar has a "transfer" time period to respond (by
default five days) after which the transfer is automatically approved. This flow
can be used by the gaining or losing registrars (or anyone with the correct
authId) to see the status of a transfer, which may still be pending or may have
been approved, rejected, cancelled or implicitly approved by virtue of the
transfer period expiring.


### Errors

*   2002
    *   Object has no transfer history.
*   2201
    *   Registrar is not authorized to view transfer status.
*   2202
    *   Authorization information for accessing resource is invalid.
*   2303
    *   Resource with this id does not exist.

## ContactTransferRejectFlow

### Description

An EPP flow that rejects a pending transfer on a contact.

The "gaining" registrar requests a transfer from the "losing" (aka current)
registrar. The losing registrar has a "transfer" time period to respond (by
default five days) after which the transfer is automatically approved. Within
that window, this flow allows the losing client to reject the transfer request.


### Errors

*   2201
    *   The specified resource belongs to another client.
*   2202
    *   Authorization information for accessing resource is invalid.
*   2301
    *   The resource does not have a pending transfer.
*   2303
    *   Resource with this id does not exist.

## ContactTransferRequestFlow

### Description

An EPP flow that requests a transfer on a contact.

The "gaining" registrar requests a transfer from the "losing" (aka current)
registrar. The losing registrar has a "transfer" time period to respond (by
default five days) after which the transfer is automatically approved. Within
that window, the transfer might be approved explicitly by the losing registrar
or rejected, and the gaining registrar can also cancel the transfer request.


### Errors

*   2002
    *   Registrar already sponsors the object of this transfer request.
*   2201
    *   Authorization info is required to request a transfer.
*   2202
    *   Authorization information for accessing resource is invalid.
*   2300
    *   The resource is already pending transfer.
*   2303
    *   Resource with this id does not exist.
*   2304
    *   Resource status prohibits this operation.

## ContactUpdateFlow

### Description

An EPP flow that updates a contact.


### Errors

*   2004
    *   The specified status value cannot be set by clients.
*   2005
    *   Internationalized postal infos can only contain ASCII characters.
*   2201
    *   The specified resource belongs to another client.
*   2303
    *   Resource with this id does not exist.
*   2304
    *   This resource has clientUpdateProhibited on it, and the update does not
        clear that status.
    *   Resource status prohibits this operation.
*   2306
    *   Cannot add and remove the same value.
    *   Declining contact disclosure is disallowed by server policy.

## DomainAllocateFlow

### Description

An EPP flow that allocates a new domain resource from a domain application.


### Errors

*   2004
    *   New registration period exceeds maximum number of years.
*   2201
    *   Only a superuser can allocate domains.
*   2302
    *   Resource with this id already exists.
*   2303
    *   Domain application with specific ROID does not exist.
*   2304
    *   Domain application already has a final status.
    *   Registrant is not whitelisted for this TLD.
    *   Nameservers are not whitelisted for this domain.
    *   Nameservers are not whitelisted for this TLD.
    *   Nameservers not specified for domain with nameserver-restricted
        reservation.
    *   Nameservers not specified for domain on TLD with nameserver whitelist.

## DomainApplicationCreateFlow

### Description

An EPP flow that creates a new application for a domain resource.


### Errors

*   2002
    *   A notice cannot be specified when using a signed mark.
    *   Sunrise applications are disallowed during landrush.
    *   Command is not allowed in the current registry phase.
*   2003
    *   Landrush applications are disallowed during sunrise.
    *   Fees must be explicitly acknowledged when performing any operations on a
        premium name.
    *   The provided mark does not match the desired domain label.
*   2004
    *   The acceptance time specified in the claim notice is more than 48 hours
        in the past.
    *   New registration period exceeds maximum number of years.
    *   The expiration time specified in the claim notice has elapsed.
    *   The fees passed in the transform command do not match the fees that will
        be charged.
    *   Domain label is not allowed by IDN table.
    *   The checksum in the specified TCNID does not validate.
    *   Domain name is under tld which doesn't exist.
*   2005
    *   Domain name must have exactly one part above the TLD.
    *   Domain name must not equal an existing multi-part TLD.
    *   The requested fee is expressed in a scale that is invalid for the given
        currency.
    *   The specified TCNID is invalid.
    *   Signed mark data is improperly encoded.
    *   Error while parsing encoded signed mark data.
*   2102
    *   The 'maxSigLife' setting is not supported.
    *   The 'grace-period', 'applied' and 'refundable' fields are disallowed by
        server policy.
*   2103
    *   Specified extension is not implemented.
*   2201
    *   Registrar is not authorized to access this TLD.
*   2202
    *   Invalid limited registration period token.
*   2302
    *   Resource with this id already exists.
    *   This name has already been claimed by a sunrise applicant.
*   2303
    *   Resource linked to this domain does not exist.
*   2304
    *   The claims period for this TLD has ended.
    *   Requested domain is reserved.
    *   Requested domain requires a claims notice.
    *   Nameservers are not whitelisted for this domain.
    *   Nameservers are not whitelisted for this TLD.
    *   Nameservers not specified for domain with nameserver-restricted
        reservation.
    *   Nameservers not specified for domain on TLD with nameserver whitelist.
    *   The requested domain name is on the premium price list, and this
        registrar has blocked premium registrations.
    *   Registrant is not whitelisted for this TLD.
    *   Requested domain does not require a claims notice.
*   2306
    *   Domain names can only contain a-z, 0-9, '.' and '-'.
    *   Periods for domain registrations must be specified in years.
    *   Encoded signed marks must use base64 encoding.
    *   The requested fees cannot be provided in the requested currency.
    *   Non-IDN domain names cannot contain hyphens in the third or fourth
        position.
    *   Domain labels cannot be longer than 63 characters.
    *   More than one contact for a given role is not allowed.
    *   No part of a domain name can be empty.
    *   Domain name starts with xn-- but is not a valid IDN.
    *   The specified trademark validator is not supported.
    *   Declared launch extension phase does not match the current registry
        phase.
    *   Domain labels cannot begin with a dash.
    *   Missing type attribute for contact.
    *   Signed marks must be encoded.
    *   Certificate used in signed mark signature has expired.
    *   Certificate parsing error, or possibly a bad provider or algorithm.
    *   Certificate used in signed mark signature has expired.
    *   Certificate used in signed mark signature was revoked by ICANN.
    *   Invalid signature on a signed mark.
    *   Signed mark data is revoked.
    *   Invalid signature on a signed mark.
    *   Too many DS records set on a domain.
    *   Too many nameservers set on this domain.
    *   Only one signed mark is allowed per application.
    *   Domain labels cannot end with a dash.
    *   Only encoded signed marks are supported.

## DomainApplicationDeleteFlow

### Description

An EPP flow that deletes a domain application.


### Errors

*   2002
    *   Command is not allowed in the current registry phase.
*   2103
    *   Specified extension is not implemented.
*   2201
    *   The specified resource belongs to another client.
    *   Registrar is not authorized to access this TLD.
*   2303
    *   Resource with this id does not exist.
*   2304
    *   A sunrise application cannot be deleted during landrush.
*   2306
    *   Application referenced does not match specified domain name.
    *   Declared launch extension phase does not match the current registry
        phase.

## DomainApplicationInfoFlow

### Description

An EPP flow that returns information about a domain application.

Only the registrar that owns the application can see its info. The flow can
optionally include delegated hosts in its response.


### Errors

*   2003
    *   Application id is required.
*   2201
    *   The specified resource belongs to another client.
*   2303
    *   Resource with this id does not exist.
*   2306
    *   Application referenced does not match specified domain name.
    *   Declared launch extension phase does not match phase of the application.

## DomainApplicationUpdateFlow

### Description

An EPP flow that updates a domain application.

Updates can change contacts, nameservers and delegation signer data of an
application. Updates cannot change the domain name that is being applied for.


### Errors

*   2003
    *   At least one of 'add' or 'rem' is required on a secDNS update.
    *   Admin contact is required.
    *   Technical contact is required.
*   2004
    *   The specified status value cannot be set by clients.
    *   The fees passed in the transform command do not match the fees that will
        be charged.
*   2102
    *   Changing 'maxSigLife' is not supported.
    *   The 'urgent' attribute is not supported.
*   2103
    *   Specified extension is not implemented.
*   2201
    *   The specified resource belongs to another client.
    *   Registrar is not authorized to access this TLD.
*   2303
    *   Resource with this id does not exist.
    *   Resource linked to this domain does not exist.
*   2304
    *   This resource has clientUpdateProhibited on it, and the update does not
        clear that status.
    *   Resource status prohibits this operation.
    *   Nameservers are not whitelisted for this TLD.
    *   Nameservers not specified for domain on TLD with nameserver whitelist.
    *   Nameservers are not whitelisted for this domain.
    *   Nameservers not specified for domain with nameserver-restricted
        reservation.
    *   Registrant is not whitelisted for this TLD.
    *   Application status prohibits this domain update.
*   2306
    *   Cannot add and remove the same value.
    *   Application referenced does not match specified domain name.
    *   More than one contact for a given role is not allowed.
    *   Missing type attribute for contact.
    *   The secDNS:all element must have value 'true' if present.
    *   Too many DS records set on a domain.
    *   Too many nameservers set on this domain.

## DomainCheckFlow

### Description

An EPP flow that checks whether a domain can be provisioned.

This flow also supports the EPP fee extension and can return pricing
information.


### Errors

*   2002
    *   Command is not allowed in the current registry phase.
*   2004
    *   Domain label is not allowed by IDN table.
    *   Domain name is under tld which doesn't exist.
*   2005
    *   Domain name must have exactly one part above the TLD.
    *   Domain name must not equal an existing multi-part TLD.
*   2201
    *   Registrar is not authorized to access this TLD.
*   2306
    *   Too many resource checks requested in one check command.
    *   Domain names can only contain a-z, 0-9, '.' and '-'.
    *   Periods for domain registrations must be specified in years.
    *   The requested fees cannot be provided in the requested currency.
    *   Non-IDN domain names cannot contain hyphens in the third or fourth
        position.
    *   Domain labels cannot be longer than 63 characters.
    *   No part of a domain name can be empty.
    *   Fee checks for command phases and subphases are not supported.
    *   Domain name starts with xn-- but is not a valid IDN.
    *   Domain labels cannot begin with a dash.
    *   Restores always renew a domain for one year.
    *   Domain labels cannot end with a dash.
    *   Transfers always renew a domain for one year.
    *   Unknown fee command name.
    *   By server policy, fee check names must be listed in the availability
        check.

## DomainCreateFlow

### Description

An EPP flow that creates a new domain resource.


### Errors

*   2002
    *   Service extension(s) must be declared at login.
    *   The current registry phase does not allow for general registrations.
*   2003
    *   Fees must be explicitly acknowledged when performing any operations on a
        premium name.
    *   Admin contact is required.
    *   Registrant is required.
    *   Technical contact is required.
*   2004
    *   The acceptance time specified in the claim notice is more than 48 hours
        in the past.
    *   New registration period exceeds maximum number of years.
    *   The expiration time specified in the claim notice has elapsed.
    *   The fees passed in the transform command do not match the fees that will
        be charged.
    *   Domain label is not allowed by IDN table.
    *   The checksum in the specified TCNID does not validate.
    *   Domain name is under tld which doesn't exist.
*   2005
    *   Domain name must have exactly one part above the TLD.
    *   Domain name must not equal an existing multi-part TLD.
    *   The requested fee is expressed in a scale that is invalid for the given
        currency.
    *   The specified TCNID is invalid.
*   2102
    *   The 'maxSigLife' setting is not supported.
    *   The 'grace-period', 'applied' and 'refundable' fields are disallowed by
        server policy.
*   2103
    *   Specified extension is not implemented.
*   2201
    *   Only a tool can pass a metadata extension.
    *   Registrar is not authorized to access this TLD.
*   2202
    *   Invalid limited registration period token.
*   2302
    *   Resource with this id already exists.
*   2303
    *   Resource linked to this domain does not exist.
*   2304
    *   The claims period for this TLD has ended.
    *   Requested domain does not have nameserver-restricted reservation for a
        TLD that requires such a reservation to create domains.
    *   Requested domain is reserved.
    *   Linked resource in pending delete prohibits operation.
    *   Requested domain requires a claims notice.
    *   Nameservers are not whitelisted for this domain.
    *   Nameservers are not whitelisted for this TLD.
    *   Nameservers not specified for domain with nameserver-restricted
        reservation.
    *   Nameservers not specified for domain on TLD with nameserver whitelist.
    *   The requested domain name is on the premium price list, and this
        registrar has blocked premium registrations.
    *   Registrant is not whitelisted for this TLD.
    *   Requested domain does not require a claims notice.
    *   There is an open application for this domain.
*   2306
    *   Domain names can only contain a-z, 0-9, '.' and '-'.
    *   Periods for domain registrations must be specified in years.
    *   The requested fees cannot be provided in the requested currency.
    *   Non-IDN domain names cannot contain hyphens in the third or fourth
        position.
    *   Domain labels cannot be longer than 63 characters.
    *   More than one contact for a given role is not allowed.
    *   No part of a domain name can be empty.
    *   Domain name starts with xn-- but is not a valid IDN.
    *   The specified trademark validator is not supported.
    *   Domain labels cannot begin with a dash.
    *   Missing type attribute for contact.
    *   Too many DS records set on a domain.
    *   Too many nameservers set on this domain.
    *   Domain labels cannot end with a dash.
    *   Only encoded signed marks are supported.

## DomainDeleteFlow

### Description

An EPP flow that deletes a domain.


### Errors

*   2002
    *   Command is not allowed in the current registry phase.
*   2201
    *   The specified resource belongs to another client.
    *   Only a tool can pass a metadata extension.
    *   Registrar is not authorized to access this TLD.
*   2303
    *   Resource with this id does not exist.
*   2304
    *   Resource status prohibits this operation.
*   2305
    *   Domain to be deleted has subordinate hosts.

## DomainInfoFlow

### Description

An EPP flow that returns information about a domain.

The registrar that owns the domain, and any registrar presenting a valid
authInfo for the domain, will get a rich result with all of the domain's fields.
All other requests will be answered with a minimal result containing only basic
information about the domain.


### Errors

*   2202
    *   Authorization information for accessing resource is invalid.
*   2303
    *   Resource with this id does not exist.
*   2306
    *   Periods for domain registrations must be specified in years.
    *   The requested fees cannot be provided in the requested currency.
    *   Fee checks for command phases and subphases are not supported.
    *   Restores always renew a domain for one year.
    *   Transfers always renew a domain for one year.

## DomainRenewFlow

### Description

An EPP flow that renews a domain.

Registrars can use this flow to manually extend the length of a registration,
instead of relying on domain auto-renewal (where the registry performs an
automatic one-year renewal at the instant a domain would expire).

ICANN prohibits any registration from being longer than ten years so if the
request would result in a registration greater than ten years long it will fail.
In practice this means it's impossible to request a ten year renewal, since that
will always cause the new registration to be longer than 10 years unless it
comes in at the exact millisecond that the domain would have expired.


### Errors

*   2003
    *   Fees must be explicitly acknowledged when performing any operations on a
        premium name.
*   2004
    *   New registration period exceeds maximum number of years.
    *   The fees passed in the transform command do not match the fees that will
        be charged.
    *   The current expiration date is incorrect.
*   2005
    *   The requested fee is expressed in a scale that is invalid for the given
        currency.
*   2102
    *   The 'grace-period', 'applied' and 'refundable' fields are disallowed by
        server policy.
*   2201
    *   The specified resource belongs to another client.
    *   Registrar is not authorized to access this TLD.
*   2303
    *   Resource with this id does not exist.
*   2304
    *   Resource status prohibits this operation.
*   2306
    *   Periods for domain registrations must be specified in years.
    *   The requested fees cannot be provided in the requested currency.

## DomainRestoreRequestFlow

### Description

An EPP flow that requests that a domain in the redemption grace period be
restored.

When a domain is deleted it is removed from DNS immediately and marked as
pending delete, but is not actually soft deleted. There is a period (by default
30 days) during which it can be restored by the original owner. When that period
expires there is a second period (by default 5 days) during which the domain
cannot be restored. After that period anyone can re-register this name.

This flow is called a restore "request" because technically it is only supposed
to signal that the registrar requests the restore, which the registry can choose
to process or not based on a restore report that is submitted through an out of
band process and details the request. However, in practice this flow does the
restore immediately. This is allowable because all of the fields on a restore
report are optional or have default values, and so by policy when the request
comes in we consider it to have been accompanied by a default-initialized report
which we auto-approve.

Restores cost a fixed restore fee plus a one year renewal fee for the domain.
The domain is restored to a single year expiration starting at the restore time,
regardless of what the original expiration time was.


### Errors

*   2002
    *   Restore command cannot have other changes specified.
*   2003
    *   Fees must be explicitly acknowledged when performing any operations on a
        premium name.
*   2004
    *   The fees passed in the transform command do not match the fees that will
        be charged.
*   2005
    *   The requested fee is expressed in a scale that is invalid for the given
        currency.
*   2102
    *   The 'grace-period', 'applied' and 'refundable' fields are disallowed by
        server policy.
*   2103
    *   Specified extension is not implemented.
*   2201
    *   The specified resource belongs to another client.
    *   Registrar is not authorized to access this TLD.
*   2303
    *   Resource with this id does not exist.
*   2304
    *   Requested domain is reserved.
    *   The requested domain name is on the premium price list, and this
        registrar has blocked premium registrations.
    *   Domain is not eligible for restore.
*   2306
    *   The requested fees cannot be provided in the requested currency.

## DomainTransferApproveFlow

### Description

An EPP flow that approves a pending transfer on a domain.

The "gaining" registrar requests a transfer from the "losing" (aka current)
registrar. The losing registrar has a "transfer" time period to respond (by
default five days) after which the transfer is automatically approved. Within
that window, this flow allows the losing client to explicitly approve the
transfer request, which then becomes effective immediately.

When the transfer was requested, poll messages and billing events were saved to
Datastore with timestamps such that they only would become active when the
transfer period passed. In this flow, those speculative objects are deleted and
replaced with new ones with the correct approval time.


### Errors

*   2201
    *   The specified resource belongs to another client.
    *   Registrar is not authorized to access this TLD.
*   2202
    *   Authorization information for accessing resource is invalid.
*   2301
    *   The resource does not have a pending transfer.
*   2303
    *   Resource with this id does not exist.

## DomainTransferCancelFlow

### Description

An EPP flow that cancels a pending transfer on a domain.

The "gaining" registrar requests a transfer from the "losing" (aka current)
registrar. The losing registrar has a "transfer" time period to respond (by
default five days) after which the transfer is automatically approved. Within
that window, this flow allows the gaining client to withdraw the transfer
request.

When the transfer was requested, poll messages and billing events were saved to
Datastore with timestamps such that they only would become active when the
transfer period passed. In this flow, those speculative objects are deleted.


### Errors

*   2201
    *   Registrar is not the initiator of this transfer.
    *   Registrar is not authorized to access this TLD.
*   2202
    *   Authorization information for accessing resource is invalid.
*   2301
    *   The resource does not have a pending transfer.
*   2303
    *   Resource with this id does not exist.

## DomainTransferQueryFlow

### Description

An EPP flow that queries a pending transfer on a domain.

The "gaining" registrar requests a transfer from the "losing" (aka current)
registrar. The losing registrar has a "transfer" time period to respond (by
default five days) after which the transfer is automatically approved. This flow
can be used by the gaining or losing registrars (or anyone with the correct
authId) to see the status of a transfer, which may still be pending or may have
been approved, rejected, cancelled or implicitly approved by virtue of the
transfer period expiring.


### Errors

*   2002
    *   Object has no transfer history.
*   2201
    *   Registrar is not authorized to view transfer status.
*   2202
    *   Authorization information for accessing resource is invalid.
*   2303
    *   Resource with this id does not exist.

## DomainTransferRejectFlow

### Description

An EPP flow that rejects a pending transfer on a domain.

The "gaining" registrar requests a transfer from the "losing" (aka current)
registrar. The losing registrar has a "transfer" time period to respond (by
default five days) after which the transfer is automatically approved. Within
that window, this flow allows the losing client to reject the transfer request.

When the transfer was requested, poll messages and billing events were saved to
Datastore with timestamps such that they only would become active when the
transfer period passed. In this flow, those speculative objects are deleted.


### Errors

*   2201
    *   The specified resource belongs to another client.
    *   Registrar is not authorized to access this TLD.
*   2202
    *   Authorization information for accessing resource is invalid.
*   2301
    *   The resource does not have a pending transfer.
*   2303
    *   Resource with this id does not exist.

## DomainTransferRequestFlow

### Description

An EPP flow that requests a transfer on a domain.

The "gaining" registrar requests a transfer from the "losing" (aka current)
registrar. The losing registrar has a "transfer" time period to respond (by
default five days) after which the transfer is automatically approved. Within
that window, the transfer might be approved explicitly by the losing registrar
or rejected, and the gaining registrar can also cancel the transfer request.

When a transfer is requested, poll messages and billing events are saved to
Datastore with timestamps such that they only become active when the
server-approval period passes. Keys to these speculative objects are saved in
the domain's transfer data, and on explicit approval, rejection or cancellation
of the request, they will be deleted (and in the approval case, replaced with
new ones with the correct approval time).


### Errors

*   2002
    *   Registrar already sponsors the object of this transfer request.
*   2003
    *   Fees must be explicitly acknowledged when performing any operations on a
        premium name.
*   2004
    *   The fees passed in the transform command do not match the fees that will
        be charged.
*   2005
    *   The requested fee is expressed in a scale that is invalid for the given
        currency.
*   2102
    *   The 'grace-period', 'applied' and 'refundable' fields are disallowed by
        server policy.
*   2201
    *   Authorization info is required to request a transfer.
    *   Registrar is not authorized to access this TLD.
*   2202
    *   Authorization information for accessing resource is invalid.
*   2300
    *   The resource is already pending transfer.
*   2303
    *   Resource with this id does not exist.
*   2304
    *   Resource status prohibits this operation.
    *   Superuser extensions cannot be used during autorenew grace periods.
    *   Domain transfer period cannot be zero when using the fee transfer
        extension.
    *   The requested domain name is on the premium price list, and this
        registrar has blocked premium registrations.
*   2306
    *   Domain transfer period must be one year.
    *   Domain transfer period must be zero or one year when using the superuser
        EPP extension.
    *   Periods for domain registrations must be specified in years.
    *   The requested fees cannot be provided in the requested currency.

## DomainUpdateFlow

### Description

An EPP flow that updates a domain.

Updates can change contacts, nameservers and delegation signer data of a domain.
Updates cannot change the domain's name.

Some status values (those of the form "serverSomethingProhibited") can only be
applied by the superuser. As such, adding or removing these statuses incurs a
billing event. There will be only one charge per update, even if several such
statuses are updated at once.

If a domain was created during the sunrise or landrush phases of a TLD, is still
within the sunrushAddGracePeriod and has not yet been delegated in DNS, then it
will not yet have been billed for. Any update that causes the name to be
delegated (such * as adding nameservers or removing a hold status) will cause
the domain to convert to a normal create and be billed for accordingly.


### Errors

*   2003
    *   At least one of 'add' or 'rem' is required on a secDNS update.
    *   Fees must be explicitly acknowledged when performing an operation which
        is not free.
    *   Admin contact is required.
    *   Technical contact is required.
*   2004
    *   The specified status value cannot be set by clients.
    *   The fees passed in the transform command do not match the fees that will
        be charged.
*   2102
    *   Changing 'maxSigLife' is not supported.
    *   The 'urgent' attribute is not supported.
*   2103
    *   Specified extension is not implemented.
*   2201
    *   The specified resource belongs to another client.
    *   Only a tool can pass a metadata extension.
    *   Registrar is not authorized to access this TLD.
*   2303
    *   Resource with this id does not exist.
    *   Resource linked to this domain does not exist.
*   2304
    *   This resource has clientUpdateProhibited on it, and the update does not
        clear that status.
    *   Resource status prohibits this operation.
    *   Linked resource in pending delete prohibits operation.
    *   Nameservers are not whitelisted for this TLD.
    *   Nameservers not specified for domain on TLD with nameserver whitelist.
    *   Nameservers are not whitelisted for this domain.
    *   Nameservers not specified for domain with nameserver-restricted
        reservation.
    *   Requested domain does not have nameserver-restricted reservation for a
        TLD that requires such a reservation to create domains.
    *   Registrant is not whitelisted for this TLD.
*   2306
    *   Cannot add and remove the same value.
    *   More than one contact for a given role is not allowed.
    *   Missing type attribute for contact.
    *   The secDNS:all element must have value 'true' if present.
    *   Too many DS records set on a domain.
    *   Too many nameservers set on this domain.

## HelloFlow

### Description

A flow for an Epp "hello".


### Errors


## HostCheckFlow

### Description

An EPP flow that checks whether a host can be provisioned.

This flows can check the existence of multiple hosts simultaneously.


### Errors

*   2306
    *   Too many resource checks requested in one check command.

## HostCreateFlow

### Description

An EPP flow that creates a new host.

Hosts can be "external", or "internal" (also known as "in bailiwick"). Internal
hosts are those that are under a top level domain within this registry, and
external hosts are all other hosts. Internal hosts must have at least one ip
address associated with them, whereas external hosts cannot have any. This flow
allows creating a host name, and if necessary enqueues tasks to update DNS.


### Errors

*   2003
    *   Subordinate hosts must have an ip address.
*   2004
    *   IP address version mismatch.
    *   Host names are limited to 253 characters.
    *   External hosts must not have ip addresses.
*   2005
    *   Invalid host name.
    *   Host names must be in lower-case.
    *   Host names must be in normalized format.
    *   Host names must be puny-coded.
*   2302
    *   Resource with this id already exists.
*   2303
    *   Superordinate domain for this hostname does not exist.
*   2304
    *   Superordinate domain for this hostname is in pending delete.
*   2306
    *   Host names must be at least two levels below the public suffix.

## HostDeleteFlow

### Description

An EPP flow that deletes a host.

Hosts that are in use by any domain cannot be deleted. The flow may return
immediately if a quick smoke check determines that deletion is impossible due to
an existing reference. However, a successful delete will always be asynchronous,
as all existing domains must be checked for references to the host before the
deletion is allowed to proceed. A poll message will be written with the success
or failure message when the process is complete.


### Errors

*   2005
    *   Host names must be in lower-case.
    *   Host names must be in normalized format.
    *   Host names must be puny-coded.
*   2201
    *   The specified resource belongs to another client.
*   2303
    *   Resource with this id does not exist.
*   2304
    *   Resource status prohibits this operation.
*   2305
    *   Resource to be deleted has active incoming references.

## HostInfoFlow

### Description

An EPP flow that returns information about a host.

The returned information included IP addresses, if any, and details of the
host's most recent transfer if it has ever been transferred. Any registrar can
see the information for any host.


### Errors

*   2005
    *   Host names must be in lower-case.
    *   Host names must be in normalized format.
    *   Host names must be puny-coded.
*   2303
    *   Resource with this id does not exist.

## HostUpdateFlow

### Description

An EPP flow that updates a host.

Hosts can be "external", or "internal" (also known as "in bailiwick"). Internal
hosts are those that are under a top level domain within this registry, and
external hosts are all other hosts. Internal hosts must have at least one IP
address associated with them, whereas external hosts cannot have any.

This flow allows changing a host name, and adding or removing IP addresses to
hosts. When a host is renamed from internal to external all IP addresses must be
simultaneously removed, and when it is renamed from external to internal at
least one must be added. If the host is renamed or IP addresses are added, tasks
are enqueued to update DNS accordingly.


### Errors

*   2004
    *   The specified status value cannot be set by clients.
    *   Host names are limited to 253 characters.
    *   Cannot add IP addresses to an external host.
    *   Host rename from subordinate to external must also remove all IP
        addresses.
*   2005
    *   Host names must be in lower-case.
    *   Host names must be in normalized format.
    *   Host names must be puny-coded.
    *   Invalid host name.
*   2201
    *   The specified resource belongs to another client.
    *   Domain for host is sponsored by another registrar.
*   2302
    *   Host with specified name already exists.
*   2303
    *   Resource with this id does not exist.
    *   Superordinate domain for this hostname does not exist.
*   2304
    *   This resource has clientUpdateProhibited on it, and the update does not
        clear that status.
    *   Resource status prohibits this operation.
    *   Superordinate domain for this hostname is in pending delete.
    *   Cannot remove all IP addresses from a subordinate host.
    *   Cannot rename an external host.
*   2306
    *   Cannot add and remove the same value.
    *   Host names must be at least two levels below the public suffix.

## LoginFlow

### Description

An EPP flow for login.


### Errors

*   2002
    *   Registrar is already logged in.
*   2100
    *   Specified protocol version is not implemented.
*   2102
    *   In-band password changes are not supported.
*   2103
    *   Specified extension is not implemented.
*   2200
    *   GAE user id is not allowed to login as requested registrar.
    *   User is not logged in as a GAE user.
    *   Registrar certificate does not match stored certificate.
    *   Registrar IP address is not in stored whitelist.
    *   Registrar certificate not present.
    *   SNI header is required.
    *   Registrar password is incorrect.
    *   Registrar with this client ID could not be found.
*   2201
    *   Registrar account is not active.
*   2306
    *   Specified language is not supported.
*   2307
    *   Specified object service is not implemented.
*   2501
    *   Registrar login failed too many times.

## LogoutFlow

### Description

An EPP flow for logout.


### Errors

*   2002
    *   Registrar is not logged in.

## PollAckFlow

### Description

An EPP flow for acknowledging {@link PollMessage}s.

Registrars refer to poll messages using an externally visible id generated by
{@link PollMessageExternalKeyConverter}. One-time poll messages are deleted from
Datastore once they are ACKed, whereas autorenew poll messages are simply marked
as read, and won't be delivered again until the next year of their recurrence.


### Errors

*   2003
    *   Message id is required.
*   2005
    *   Message id is invalid.
*   2201
    *   Registrar is not authorized to ack this message.
*   2303
    *   Message with this id does not exist.

## PollRequestFlow

### Description

An EPP flow for requesting {@link PollMessage}s.

This flow uses an eventually consistent Datastore query to return the oldest
poll message for the registrar, as well as the total number of pending messages.
Note that poll messages whose event time is in the future (i.e. they are
speculative and could still be changed or rescinded) are ignored. The externally
visible id for the poll message that the registrar sees is generated by {@link
PollMessageExternalKeyConverter}.


### Errors

*   2005
    *   Unexpected message id.

