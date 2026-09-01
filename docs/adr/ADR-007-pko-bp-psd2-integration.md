# ADR-007: PKO BP PSD2 Account-Information Integration

**Date:** 2026-08-31
**Status:** Accepted

## Context

A meaningful share of real spending never produces a photo: BLIK purchases, and bank transfers
(e.g. paying a utility bill by transferring money to a family member) leave no receipt to
photograph, so today they're invisible to this app. PKO Bank Polski exposes a PSD2
account-information (AIS) API under the PolishAPI standard that can close this gap by importing
bank transactions directly.

**Access-path reality (confirmed against developers.pkobp.pl):**
- PKO's developer portal is a PSD2 TPP API (PolishAPI / OpenAPI, "OpenAPI_1.7").
- **Sandbox + full docs** are unlocked by ordinary self-registration (email/account, no license)
  — enough to build and fully test this integration.
- **Production/live account data** is restricted to certified TPP business partners: AISP
  registration with KNF (the Polish financial regulator) plus eIDAS QWAC/QSEAL certificates.
  This is a **separate business/legal step the user must pursue themselves** whenever they decide
  to go live — it is not, and cannot be, performed by any agent in this project. This ADR designs
  for that eventuality without blocking on it: everything below is buildable and testable today
  against the sandbox.
- The user has not yet registered or downloaded the real sandbox OpenAPI spec, so **exact
  endpoint/field names are unknown right now**. Every field name below is marked TBD where it
  isn't a PSD2/OAuth2 standard term — the adapter design exists specifically to absorb that
  unknown without destabilizing the rest of the app once the real spec arrives.

**Use case chosen by the user:** auto-import as receipts. Each new bank transaction becomes a
receipt-like entry automatically (counterparty, amount, date, free-text title) — not merely a
reconciliation aid the user has to act on by hand.

**Refinement from the user, after the first design pass (superseding an earlier three-way
match/no-match/ambiguous-match draft of this decision):**
1. The bank fetch runs **once daily**, on the same cadence as the existing photo-classification
   job — not continuous polling.
2. Dedup against an already-photographed receipt is a **silent discard**, not a second entry
   awaiting the user's confirmation. A confident match means the bank transaction simply never
   becomes a receipt. There is no "confirm this match" screen.
3. The classification-uncertainty case is separate from dedup entirely: a bank transaction that
   *doesn't* match a photo gets auto-categorized by the same Claude classification engine
   (whole-transaction, since there's no line-item detail to itemize). When Claude isn't confident
   in a category, it must not be forced to guess — it lands in a new manual-category review queue
   instead of `FAILED` (which CLAUDE.md reserves for genuinely unreadable content, not
   classification uncertainty).

## Decision

### 1. Integration approach: Ports & Adapters, built against the general PSD2/PolishAPI shape

The real PKO sandbox spec isn't in hand, but the *general* shape of any PSD2 AIS API is
standardized enough to build against: OAuth2 authorization-code consent flow with Strong
Customer Authentication (SCA), `GET /accounts`, `GET /accounts/{id}/transactions`, with
transaction fields resembling `bookingDate`, `amount`, `currency`,
`creditorName`/`debtorName`, `remittanceInformationUnstructured` (PolishAPI/Berlin-Group
convention — **TBD, confirm against the real PKO sandbox spec once registered**).

A backend-owned port interface insulates the rest of the system from PKO's actual wire format:

```java
// Illustrative — Backend owns the real interface, package, and method signatures.
public interface BankAccountInformationPort {
    ConsentAuthorization initiateConsent();                 // → redirect URL + opaque state
    void completeConsent(String authorizationCode, String state);
    void refreshAccessTokenIfNeeded();                      // transparent OAuth2 refresh-token use
    List<BankAccount> listAccounts();
    List<BankTransaction> listTransactions(String accountId, LocalDate from, LocalDate to);
}

// Provider-agnostic value shapes — never leak PKO's exact field names past the adapter.
record BankAccount(String externalAccountId, String maskedIban);
record BankTransaction(
    String externalTransactionId, LocalDate bookingDate,
    BigDecimal amount, String counterpartyName, String title
);
```

`PkoBpPsd2Adapter implements BankAccountInformationPort` is the concrete, PKO-specific
implementation Backend builds once real sandbox credentials and field names exist — until then it
can be a stub that throws `NotYetConfiguredException`. Nothing above the port (the sync job, the
dedup heuristic, the domain model) ever sees a PKO-specific field name.

**Why this pattern, not a simpler direct call:** without it, sandbox field names (once
discovered) would leak directly into sync-job/domain code, and reconciling against the real
production spec later would mean touching business logic instead of one adapter class. This is
exactly the kind of "external system's wire format is unstable/unknown" situation Ports &
Adapters exists for — not reached for reflexively; see the Pattern Evaluation table in
`docs/architecture/06-bank-integration.md`.

### 2. Aggregation strategy: direct-to-PKO now, not a licensed aggregator

Two ways to get PKO transaction data:
- **Direct-to-PKO** (chosen): the backend talks to PKO's own PSD2 API via the port above.
- **Licensed aggregator** (e.g. a multi-bank AISP that PKO is just one connector behind):
  already certified, so no KNF/eIDAS step for the user — but almost universally a paid service.

**Decision: direct-to-PKO.** Reasons:
- CLAUDE.md's hard constraint is that this app "pays for nothing extra" — a paid aggregator
  violates that outright; direct PSD2 access is free once the user completes their own TPP/AISP
  certification (a compliance cost, not a running cost).
- Only one bank is in scope right now — an aggregator's main selling point (one integration
  across many banks) buys nothing here. Building the multi-bank abstraction an aggregator vendor
  SDK would impose is YAGNI at this app's single-bank scale.
- The sandbox is available *today*, free, via self-registration — Backend can build and fully
  test this without waiting on any paid signup.

**The trade-off this creates:** going live shifts the KNF/eIDAS certification burden onto the
user personally — a real business/legal undertaking a licensed aggregator would have absorbed.
The port interface exists precisely so this isn't a one-way door: if the user later decides that
burden isn't worth it, swapping `PkoBpPsd2Adapter` for an aggregator-backed adapter behind the
same `BankAccountInformationPort` is a contained change, not a rewrite of the sync job, the dedup
heuristic, or the domain model.

### 3. Consent/token lifecycle: degrade gracefully, never crash, never lose data

A `bank_connection` row (see `docs/architecture/06-bank-integration.md` for the full schema)
tracks OAuth2 state: `status` (`DISCONNECTED` → `PENDING_CONSENT` → `ACTIVE` → `EXPIRED` |
`ERROR`), access/refresh tokens, `consentExpiresAt`. PSD2's RTS requires periodic SCA
re-authentication of a standing consent — on the order of every ~90 days (**exact PKO figure
TBD** — sandbox docs will confirm once registered).

Applying the same retry-safety spirit CLAUDE.md already establishes for the classify job:

- **Short-lived access-token expiry** (routine, happens between most sync runs) is handled
  transparently — the sync job refreshes via the standard OAuth2 refresh-token grant before
  calling PKO, with no user involvement and no interruption to the run.
- **Refresh-token/consent expiry** (the ~90-day SCA boundary, or PKO revoking the consent) cannot
  be resolved automatically — SCA legally requires the account holder's active participation, so
  no retry loop can fix it. When the sync job hits this, it sets `bank_connection.status =
  'EXPIRED'`, records the error, and **makes zero writes to `receipts` or `bank_transaction_log`
  that run** — exactly the classify job's "any failure this run degrades to submit-nothing,
  retry-safe" shape (see `docs/architecture/04-classification-flow.md`'s retry sequence). The
  endpoint still returns `200` with `{ status: "EXPIRED", imported: 0, ... }` rather than an
  alarming 5xx, so a host-cron caller needs no special-cased error branching — same uniform
  "any failure → no-op" philosophy as `classify-receipts.sh`.
- Recovery is **only** through the user re-initiating consent from the PWA's settings screen
  (Frontend, follow-up work) — there is no automatic recovery path, by design, because none is
  legally possible.
- A **per-account transient error** (one account's transaction fetch fails, others succeed) does
  not abort the whole run — same "never crash the whole batch over one bad item" gate CLAUDE.md
  applies to `classification-batch`. The failing account is logged and skipped; the run continues
  and reports partial success.
- **No exponential-backoff/circuit-breaker library** (e.g. Resilience4j) — rejected as YAGNI. The
  sync job runs once daily with no concurrent callers to protect from cascading retries; "log the
  failure, make no writes, let the next scheduled run try again" already gets the same effect with
  zero added dependency, exactly like the classify job's own reasoning in ADR-002.

### 4. Dedup: silent discard on a confident match, not a review queue

A card purchase the user separately photographs must not become two receipts. Per the user's
explicit refinement, this is a **binary decision, not a three-way match/no-match/ambiguous
state machine**, and a confident match is simply **dropped** — no second receipt, no "confirm
this match" UI.

**Heuristic:** a bank transaction confidently matches an existing itemized receipt (`source IN
(CAMERA, MANUAL)`, `status = PROCESSED` — only a processed receipt's `total_amount` is trustworthy
enough to compare) when:
- `total_amount` equals the bank transaction's amount exactly, **and**
- `captured_at` falls within a 2-day window of the transaction's booking date (covers typical
  1–2 business day card-settlement lag; BLIK usually posts same-day), **and**
- exactly **one** such receipt exists.

Zero or multiple equally-good candidates is treated as **no match** — the transaction is
imported as its own receipt rather than silently discarded. This is a deliberate asymmetry: a
missed dedup (an occasional un-caught duplicate) is visible and self-correcting — the user
deletes it via the already-existing `DELETE /receipts/{id}` — whereas a wrong discard would be
silent, permanent data loss with no recovery path at all. When forced to choose, err toward
keeping data, not toward hiding it.

**Auditability without a review UI:** every bank transaction the sync job sees — imported or
discarded — is recorded in a new `bank_transaction_log` table (one row per
`external_transaction_id`, `outcome` = `IMPORTED` | `DISCARDED_DUPLICATE`, plus the raw
counterparty/title/amount/date and, for a discard, which receipt it matched). This is the
"auditable/debuggable" requirement's answer: a developer can inspect *why* a transaction was
dropped without the app needing a dedicated review screen. No REST endpoint exposes this log in
this pass — it's a Postgres-level debugging aid, not a user-facing feature; add a read endpoint
later only if it turns out to be actually needed (explicit YAGNI call, not an oversight).

**Ordering hazard and its fix:** because the bank fetch and the photo classification job both run
once daily, a photo taken the same day its matching transaction posts might still be `PENDING`
(not yet classified, `total_amount` still 0) at the moment the sync job's dedup check runs against
it — which would miss the match and import a duplicate. Two mitigations, together:
1. **Schedule the classify job before the bank-sync job** in the daily cron order (see
   `docs/architecture/06-bank-integration.md`) so same-day photos are `PROCESSED` first in the
   common case.
2. **A second, symmetric trigger**: whenever an itemized receipt (CAMERA or MANUAL) newly becomes
   `PROCESSED` — via `classification-batch` or `POST /receipts/manual` — the same heuristic runs
   in the other direction, against not-yet-discarded `BANK_IMPORT` receipts. A late-discovered
   match causes the backend to `DELETE` the standalone bank-import receipt it had earlier created
   and retroactively flips its `bank_transaction_log` row to `DISCARDED_DUPLICATE`. This closes
   the loop regardless of which side (photo or bank transaction) arrives first, reusing the exact
   same match function from both call sites (DRY).

Matching is scoped to **cross-source only** (bank vs. photo/manual) — deduplicating two photos of
the same receipt, or two bank transactions against each other, is a separate, unaddressed
problem, explicitly out of scope here.

### 5. Classification-uncertainty review queue: a new terminal status, not a flag

Per-line-item photo classification keeps its existing "always guess, a human corrects afterward"
policy unchanged (CLAUDE.md, ADR-002/ADR-005) — one wrong guess among many line items is low-
stakes and already has a correction UI. Whole-transaction bank classification is different: it's
a single category covering the transaction's entire amount, there's no "browse many items and
spot the odd one out" moment, and the user explicitly asked that an unconfident guess not be
forced through silently.

**Decision:** a new `receipt_status_enum` value, `NEEDS_CATEGORY_REVIEW`, reachable only by
`BANK_IMPORT` receipts (enforced by a `CHECK` constraint, symmetric to how `NEEDS_CATEGORY_REVIEW`
is unreachable for `CAMERA`/`MANUAL`). Not a boolean flag layered onto `PROCESSED`, because
`PROCESSED` currently means "has valid line items and a trustworthy `total_amount`" everywhere
else in this app (list filters, spend aggregates, the dedup heuristic above) — overloading it
with an incomplete-but-marked-processed hybrid would break that invariant for every consumer, not
just this one new case. A dedicated status is the smaller, more consistent change, and it plugs
directly into the state-machine-driven design the rest of the schema already uses.

`classify-receipts.sh`'s single daily `claude -p` invocation now needs a **three-way** decision
for a `BANK_IMPORT` id (unchanged two-way `items`/`failures` for `CAMERA` ids): confident category
→ `items[]` (as before, exactly one `lineItems` entry) → `PROCESSED`; genuinely unreadable/corrupt
transaction data → `failures[]` (as before, rare for bank data) → `FAILED`; not confident enough
to pick one of the 11 categories → a new `uncertainCategory[]` array → `NEEDS_CATEGORY_REVIEW`.
See `docs/openapi.yaml`'s `ClassificationBatchRequest`/`ClassificationBatchResult` and
`infra/classify/prompt.md`'s bank-transaction section for the exact contract.

Resolution is a dedicated `PUT /receipts/{id}/category` (Frontend surfaces this as a new
"needs a category" review panel/section — explicitly not just a badge in the existing list, per
the user's ask) — not `reprocess`: re-running Claude against the exact same transaction text
would reach the same "not confident" conclusion again, so the only real resolution is a human
picking the category directly. The resulting single line item is created with `corrected = true`
immediately (it's first-party human input, same protection a hand-correction gets) so a
theoretical future reprocess can never silently overwrite it.

## Consequences

- **Production is gated on the user's own KNF/eIDAS certification**, entirely outside this
  project's or any agent's ability to perform — everything here targets the free sandbox until
  that happens. `bank_connection.status` starting at `DISCONNECTED` and requiring an explicit
  consent flow makes "not yet connected" the natural, harmless default rather than something that
  needs code to special-case.
- **No Java code, real Flyway migration, or wired-up frontend exists yet.** This ADR and
  `docs/architecture/06-bank-integration.md` are the contract; Backend/Frontend/DevOps implement
  against it once this lands (see that task's Scope sections 2–4).
- **Real PKO field names are still unknown.** Every schema/DTO shape above uses generic
  PSD2/OAuth2-standard naming, not confirmed PKO wire fields — reconciling the adapter against the
  real sandbox spec, once the user registers and shares it, is expected future work, contained to
  `PkoBpPsd2Adapter` by construction.
- **Stored tokens are a materially bigger blast radius than anything else in this app.** Unlike
  receipt photos or spend data, a leaked PKO access/refresh token grants a third party read access
  to the user's real bank account. `docs/openapi.yaml` never returns raw token values from any
  endpoint (`GET /bank/connection` exposes only status/expiry/timestamps). Encryption-at-rest for
  the `bank_connection` token columns is recommended (e.g. a JPA `AttributeConverter`) but left as
  a Backend implementation choice, not mandated here — this app's perimeter security still rests
  entirely on the Tailscale boundary (ADR-004), same trust model as everything else in it; tokens
  simply deserve more caution within that model given the stakes of a leak.
- **The OAuth2 redirect URI must be Tailscale-reachable** (the user's browser, not a server,
  navigates back from PKO to this app) — registering it with PKO's developer portal is a DevOps/
  deployment detail to account for once sandbox credentials exist, not a design change.
- **`total_amount` can visibly change after the fact** when a late-arriving photo retroactively
  discards an already-`PROCESSED` bank-import receipt from a monthly total. Accepted and expected
  for a personal app prioritizing "never double-count" over "numbers never move once shown."
- **The 2-day match window and exact-amount rule are a starting heuristic**, not empirically
  tuned — refine once real PKO transaction data is observed (e.g. currency-conversion fees on a
  rare foreign purchase could break the exact-amount assumption; out of scope here, worth a
  Backend note).
