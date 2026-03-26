# Briar Contact Invitation Via Link

## Scope

This document covers only the contact invitation flow that starts from a Briar handshake link such
as `briar://abmp...`.

It does not cover:

- forum/blog sharing invitations
- private-group invitations
- introductions between existing contacts

## High-level summary

In this project, "invite a contact via a Briar link" really means:

1. one side generates a handshake link from its Briar handshake public key
2. the other side stores that link as a `PendingContact`
3. Briar starts background rendezvous polling for that pending contact
4. once both sides have added each other's links and a transport connects them, Briar performs:
   - a cryptographic handshake
   - a contact-info exchange
   - final contact creation

The important consequence is that `addContactByLink()` is not "create contact now". It is
"register a pending contact and let Briar finish the rest asynchronously".

## The link itself

Upstream Briar defines the handshake-link format in `briar/bramble-api/.../HandshakeLinkConstants.java`.

Current format facts:

- prefix: optional `briar://`
- payload: 53 lowercase base32 characters
- raw size before base32: 33 bytes
- byte 0: format version
- remaining bytes: agreement public key bytes
- current format version: `0`

Implementation details:

- `PendingContactFactoryImpl.createHandshakeLink()` builds the outgoing link.
- `PendingContactFactoryImpl.parseHandshakeLink()` parses the incoming link.
- parsing uses `LINK_REGEX.matcher(link).find()`, not a full-string match

That last point matters: Briar accepts a valid link even if it appears inside surrounding text,
for example `"before briar://... after"`. Both the runtime adapter and upstream parser behave this
way.

## Arise app call chain

The Arise-specific entry points are small wrappers around upstream Briar:

1. `app/.../BriarContactRepository.kt`
   - app-facing entry point
   - reads the current `BriarContactService` from `TransportRuntimeBridge`
   - refuses the operation if the runtime is not ready

2. `briar-runtime/.../RealBriarContactService.kt`
   - validates the link with `HandshakeLinkConstants.LINK_REGEX`
   - trims the alias
   - falls back to `"Contact"` if the alias is blank or null
   - calls `contactManager.addPendingContact(link, aliasToUse)`

3. upstream Briar `ContactManagerImpl.addPendingContact()`
   - parses the link
   - persists a pending contact
   - seeds pending transport keys
   - triggers the background rendezvous flow

There is also a debug-only automation hook:

- `app/src/debug/.../AddBriarContactReceiver.kt`
  - accepts an ADB broadcast with `link` and optional `alias`
  - forwards to `BriarContactRepository.addContactByLink()`

## Other entry points already present in the included Briar code

The composite `briar/` source tree already contains fuller link-invite surfaces than Arise
currently exposes.

### Upstream Briar Android flow

The included upstream Android app implements a complete link-exchange flow:

- `briar/briar-android/.../AddContactActivity.java`
  - accepts incoming links from `ACTION_SEND` and `ACTION_VIEW`
  - rejects opening your own link

- `briar/briar-android/.../LinkExchangeFragment.java`
  - loads and displays the local user's handshake link
  - lets the user copy/share it
  - validates the remote link with the same `LINK_REGEX`
  - reminds the user that both sides must enter each other's links

- `briar/briar-android/.../NicknameFragment.java`
  - collects the alias
  - calls `contactManager.addPendingContact(remoteHandshakeLink, nickname)`
  - handles duplicate-contact and duplicate-pending-contact cases in the UI

- `briar/briar-android/.../AddContactViewModel.java`
  - is the actual Android-level bridge into `ContactManager`
  - exposes the local handshake link
  - validates remote links
  - stores the pending contact

So if the goal is to understand the "full Briar product" behavior rather than only Arise's runtime
adapter, these classes are the canonical UI implementation.

### Upstream Briar headless API

The included headless server also exposes this flow:

- `GET /v1/contacts/add/link`
  - returns the local handshake link

- `GET /v1/contacts/add/pending`
  - lists pending contacts

- `POST /v1/contacts/add/pending`
  - adds a pending contact from a remote link + alias

- `DELETE /v1/contacts/add/pending`
  - removes a pending contact

Those routes are registered in `briar/briar-headless/.../Router.kt` and implemented in
`briar/briar-headless/.../ContactControllerImpl.kt`.

This is useful context because Arise currently exposes only a narrow slice of what the embedded
Briar stack can already do.

## Runtime readiness requirements

Arise does not allow this flow unless the embedded Briar runtime is ready.

Readiness path:

- `TransportRuntimeBridgeImpl` starts the Briar runtime in `HYBRID` or `BRIAR_ONLY` mode.
- `BriarRuntimeManagerImpl` swaps in the real contact service once the runtime starts.
- `BriarComponentFactoryImpl` marks readiness only after Briar services are running and a local
  identity exists.
- `RealBriarContactService.isAvailable` is therefore the gate for calling `addContactByLink()`.

What this means operationally:

- if the runtime is stopped or not signed in, `BriarContactRepository.addContactByLink()` throws
  `IllegalStateException("Briar runtime is not ready")`
- callers should treat runtime readiness as a hard precondition

## Upstream Briar flow in detail

### 1. Link generation on the inviting side

The device that wants to be added generates its own handshake link through:

- `ContactManager.getHandshakeLink()`

That method:

- loads the local handshake key pair from `IdentityManager`
- passes the public key into `PendingContactFactoryImpl.createHandshakeLink()`
- returns `briar://` + base32(version byte + public key)

Arise currently does not expose this operation through `briar-runtime` or the app UI. The project
only implements the "consume somebody else's link" side.

If the project later needs "show my Briar link", the upstream code already exists. The cleanest
extension point is a new runtime method that delegates to `ContactManager.getHandshakeLink()`.

### 2. Adding a link creates a pending contact

`RealBriarContactService.addContactByLink()` is deliberately thin. The real work starts in
`ContactManagerImpl.addPendingContact()`.

That method:

1. calls `PendingContactFactoryImpl.createPendingContact(link, alias)`
2. derives a `PendingContactId` by hashing the remote handshake public key with
   `ID_LABEL = "org.briarproject.bramble/HANDSHAKE_KEY_ID"`
3. stores the pending contact in the database
4. registers pending transport keys through `KeyManager.addPendingContact(...)`

Duplicate protection happens inside `DatabaseComponentImpl.addPendingContact()`:

- if the same handshake public key already belongs to a real contact, Briar throws
  `ContactExistsException`
- if the same pending contact already exists, Briar throws `PendingContactExistsException`

So a successful `addContactByLink()` only guarantees:

- the link parsed
- the public key was valid
- the pending contact was stored

It does not guarantee:

- the remote side is online
- the remote side has added your link
- a real contact already exists

### 3. Pending-contact events and states

Once the pending contact is stored, Briar starts driving it through a small state machine.

Relevant events:

- `PendingContactAddedEvent`
- `PendingContactStateChangedEvent`
- `PendingContactRemovedEvent`
- `ContactAddedEvent`

Relevant states from `PendingContactState`:

- `WAITING_FOR_CONNECTION`
- `OFFLINE`
- `CONNECTING`
- `ADDING_CONTACT`
- `FAILED`

Actual state transitions are driven mainly by `RendezvousPollerImpl`:

- when a pending contact is added, Briar derives rendezvous material from both parties' static
  handshake keys
- it creates rendezvous endpoints on transports that support this
- if it has at least one endpoint, it broadcasts `WAITING_FOR_CONNECTION`
- if it has no endpoints, it broadcasts `OFFLINE`
- when a rendezvous connection opens, it broadcasts `ADDING_CONTACT`
- if rendezvous expires, it broadcasts `FAILED`

Important nuance:

- the core rendezvous flow does not appear to broadcast `CONNECTING`
- the included upstream Briar Android UI derives a `CONNECTING` display state from recent
  rendezvous polling activity while the underlying core state is still `WAITING_FOR_CONNECTION`

- `ContactManagerImpl.getPendingContacts()` defaults a pending contact to
  `WAITING_FOR_CONNECTION` if no state event has been seen yet

## 4. Both sides must add each other's links

This is a bilateral handshake flow, not a one-sided invite acceptance.

For contact creation to complete:

- Alice must add Bob's link
- Bob must add Alice's link

That requirement is visible in `ContactExchangeIntegrationTest`:

- both test devices add each other as pending contacts first
- only then can rendezvous, handshake, and contact exchange succeed

If only one side adds the link, Briar can store a pending contact, but it cannot complete contact
creation because the other side has not published matching rendezvous state for that pending
contact.

## 5. Handshake over the rendezvous connection

After rendezvous produces a duplex transport connection, `ConnectionManagerImpl` routes it into:

- `OutgoingHandshakeConnection`, or
- `IncomingHandshakeConnection`

Those classes:

1. allocate stream contexts for the pending contact
2. run `HandshakeManager.handshake(...)`
3. pass the resulting master key into `ContactExchangeManager.exchangeContacts(...)`

`HandshakeManagerImpl` performs the cryptographic handshake:

- determines Alice/Bob role from the two static handshake keys
- exchanges ephemeral public keys
- derives a shared master key
- exchanges proofs of ownership
- verifies the remote proof

If anything is malformed or inconsistent, the handshake fails and the connection is torn down.

## 6. Contact exchange and final contact creation

After the handshake succeeds, `ContactExchangeManagerImpl.exchangeContacts(...)`:

1. exchanges each side's `Author` object and transport properties
2. signs that contact info with the derived master key
3. verifies the remote signature
4. calls `addContact(...)`

When this was a pending-contact flow, `addContact(...)` eventually calls:

- `ContactManagerImpl.addContact(txn, pendingContactId, ...)`

That final step:

- removes the pending contact
- preserves the alias stored on the pending contact
- stores the remote handshake public key on the real contact
- derives and stores normal contact transport keys
- emits `ContactAddedEvent`

One important detail from `IncomingHandshakeConnection` and `OutgoingHandshakeConnection`:

- they pass `verified = false`

So contacts created via remote Briar links are not treated as in-person verified contacts.

## What Arise exposes after contact creation

Arise currently exposes only confirmed contacts, not pending contacts.

`RealBriarContactService` keeps a simple `contacts` flow and refreshes it on:

- `ContactAddedEvent`
- `ContactRemovedEvent`
- `PendingContactRemovedEvent`

It does not expose:

- `PendingContactAddedEvent`
- `PendingContactStateChangedEvent`
- the pending-contact list

This has two practical consequences:

1. after calling `addContactByLink()`, the caller gets no pending-contact model back
2. `observeContacts()` remains empty until the background flow completes and a real contact is
   created

So in Arise, the visible success signal is the appearance of a confirmed contact in
`observeContacts()`, not the return of `addContactByLink()` itself.

## How this should be used in this project

### Current supported usage

Use the flow like this:

1. make sure the transport mode has started Briar (`HYBRID` or `BRIAR_ONLY`)
2. make sure the user is signed in to the embedded Briar runtime
3. obtain the remote user's Briar link out of band
4. call `BriarContactRepository.addContactByLink(link, alias)`
5. wait for the contact to appear through `BriarContactService.observeContacts()`

### What callers should assume

Callers should assume:

- the operation is asynchronous after the initial DB write
- the remote side must also add our link
- network/transport availability determines how long completion takes
- immediate return from `addContactByLink()` means only "pending contact stored"

### What callers should not assume

Callers should not assume:

- the contact exists immediately
- there will be pending-state updates through the current Arise contact API
- the app can currently generate and display its own Briar link

## Failure modes you should handle

### Arise/runtime-level failures

- runtime not ready
  - thrown as `IllegalStateException`
  - source: repository/service availability checks

- syntactically invalid link
  - thrown as `IllegalArgumentException("Invalid Briar handshake link")`
  - source: `RealBriarContactService`

### Upstream Briar failures

- unsupported link version
  - thrown by `PendingContactFactoryImpl.parseHandshakeLink()`

- invalid public key inside an otherwise syntactically valid link
  - thrown during parser/key validation

- duplicate existing contact
  - `ContactExistsException`

- duplicate pending contact
  - `PendingContactExistsException`

- rendezvous timeout
  - pending contact eventually reaches `FAILED`

Arise currently does not translate the upstream duplicate exceptions into user-facing error types.
If `contactManager.addPendingContact(...)` throws a `DbException`, it will bubble out of the
runtime service and repository.

## Project-specific quirks and gaps

### 1. Arise only supports link consumption today

There is no Arise runtime API to retrieve the local user's handshake link, even though upstream
Briar supports it via `ContactManager.getHandshakeLink()`.

### 2. Pending contacts are invisible to the app layer

The current runtime adapter exposes only confirmed contacts. If the product needs:

- "pending invitation sent"
- "waiting for connection"
- "failed to connect"
- "retry/remove pending invite"

then `briar-runtime` needs a second surface that exposes:

- `ContactManager.getPendingContacts()`
- `PendingContactStateChangedEvent`
- explicit remove/retry operations

### 3. Repository default alias is surprising

`BriarContactRepository.addContactByLink()` currently declares:

- `alias: String? = "test"`

That means callers who omit the alias get `"test"` rather than the runtime service's own fallback
of `"Contact"`. This is not a Briar-core behavior; it is an Arise-specific wrapper behavior.

### 4. Link validation is syntactic first, cryptographic second

Arise first checks the regex in `RealBriarContactService`, but full validation does not happen
until upstream Briar parses the link and reconstructs the remote public key.

## Recommended next steps if you want a complete UX

If the goal is a full in-app Briar contact-invite experience, the missing pieces are:

1. expose `getHandshakeLink()` from `briar-runtime`
2. expose pending contacts and pending-contact states
3. map Briar duplicate/invalid-link exceptions to user-facing result types
4. decide whether `addContactByLink()` should return a richer result than `Unit`
5. remove or change the repository default alias `"test"`

## Useful source files

Arise integration:

- `app/src/main/java/com/example/rise/transport/briar/BriarContactRepository.kt`
- `briar-runtime/src/main/java/com/example/rise/briar/runtime/BriarContactService.kt`
- `briar-runtime/src/main/java/com/example/rise/briar/runtime/RealBriarContactService.kt`
- `app/src/debug/java/com/example/rise/debug/AddBriarContactReceiver.kt`

Upstream Briar contact-link flow:

- `briar/bramble-api/src/main/java/org/briarproject/bramble/api/contact/HandshakeLinkConstants.java`
- `briar/bramble-api/src/main/java/org/briarproject/bramble/api/contact/ContactManager.java`
- `briar/bramble-core/src/main/java/org/briarproject/bramble/contact/PendingContactFactoryImpl.java`
- `briar/bramble-core/src/main/java/org/briarproject/bramble/contact/ContactManagerImpl.java`
- `briar/bramble-core/src/main/java/org/briarproject/bramble/rendezvous/RendezvousPollerImpl.java`
- `briar/bramble-core/src/main/java/org/briarproject/bramble/contact/HandshakeManagerImpl.java`
- `briar/bramble-core/src/main/java/org/briarproject/bramble/contact/ContactExchangeManagerImpl.java`
- `briar/bramble-core/src/main/java/org/briarproject/bramble/connection/IncomingHandshakeConnection.java`
- `briar/bramble-core/src/main/java/org/briarproject/bramble/connection/OutgoingHandshakeConnection.java`

Tests worth reading:

- `briar-runtime/src/test/java/com/example/rise/briar/runtime/RealBriarContactServiceTest.kt`
- `app/src/test/java/com/example/rise/transport/briar/BriarContactRepositoryTest.kt`
- `briar/bramble-core/src/test/java/org/briarproject/bramble/contact/PendingContactFactoryImplTest.java`
- `briar/bramble-core/src/test/java/org/briarproject/bramble/contact/ContactExchangeIntegrationTest.java`
- `briar/briar-headless/src/test/java/org/briarproject/briar/headless/contact/ContactControllerTest.kt`
