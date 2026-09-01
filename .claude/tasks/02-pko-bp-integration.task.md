# PKO BP account-transaction integration (prepare, sandbox-first)

Add PKO Bank Polski PSD2 account-information data as a second receipt source, alongside photo
capture. Purpose: a meaningful share of real spending (BLIK purchases, bank transfers — e.g. the
user pays electricity/water bills as a transfer to a family member) never produces a photo at
all, so it's invisible to the app today. Auto-importing bank transactions closes that gap.

## Access-path reality (confirmed against developers.pkobp.pl)

- PKO BP's developer portal is a **PSD2 TPP API** (PolishAPI / OpenAPI, "OpenAPI_1.7").
- **Sandbox + full docs**: unlocked by ordinary self-registration on the portal (email/account,
  no license). This is enough to build and fully test the integration.
- **Production/live account data**: restricted to certified TPP business partners (AISP
  registration with KNF, eIDAS QWAC/QSEAL certs). This is a separate business/legal step the user
  must pursue themselves when ready to go live — **not** part of this engineering task, and not
  something any agent here can do on the user's behalf. Design for it, don't block on it.
- The user has not yet registered/downloaded the real sandbox OpenAPI spec, so exact endpoint
  field names are unknown right now. Design against the general PSD2/PolishAPI AIS shape (OAuth2
  authorization-code consent flow with SCA, `GET /accounts`, `GET /accounts/{id}/transactions`,
  transaction fields ~ bookingDate, amount, currency, creditor/debtorName,
  remittanceInformationUnstructured) behind a clean adapter/port interface, so swapping in the
  real spec later is a contained, low-risk change.

## Use case (user-selected)

**Auto-import as receipts.** Each new bank transaction becomes a receipt-like entry
automatically (counterparty + amount + date + free-text title), not just a reconciliation aid.

Consequences for the domain model:
- Bank-imported entries have **no image** and **no itemized line items** — classification must
  work from transaction metadata alone (counterparty name, transfer title, amount), assigning
  the *whole* transaction to one category, not a per-product breakdown like photo receipts get.
- Need a `source` concept on receipts (`PHOTO` vs `BANK_IMPORT`) so UI/API can distinguish them.
- **Dedup is required**: a card purchase the user also photographs must not become two receipts
  (one from the photo, one from the matching bank transaction). Design a match/merge strategy
  (date + amount proximity, manual confirm on ambiguous matches) rather than silently doubling
  spend totals.
- PSD2 AIS consent has a real lifecycle (SCA re-auth typically required periodically, e.g. every
  ~90 days per RTS) — this is an operational constraint the sync job design must account for
  (consent-expired must degrade gracefully, not crash/lose data, same spirit as the existing
  classify-receipts retry-safety rules in CLAUDE.md).

## Scope

1. **Architect** (run first):
   - ADR: PKO BP PSD2 integration — access path (sandbox now / TPP-gated production later),
     chosen aggregation strategy (direct-to-PKO adapter vs. keeping room for a licensed
     aggregator later), consent/token lifecycle handling.
   - Domain model updates: receipt `source` (PHOTO/BANK_IMPORT), make image/line-items optional
     for bank-imported receipts, dedup/matching strategy and its data shape.
   - `docs/openapi.yaml` updates: new endpoints for initiating/completing PSD2 consent, a sync
     job entry point (mirrors the existing daily classify job shape), and how bank-imported
     receipts flow into the existing classification pipeline (whole-transaction classification,
     not per-line-item).
   - Update `docs/architecture/*` diagrams/docs and reconcile `infra/classify/prompt.md` if the
     classification contract changes shape for no-image entries.
   - Flag clearly in the ADR that going to production requires the user to independently complete
     TPP/AISP certification with KNF — out of scope for any agent to perform.
2. **Backend**: implement the domain/schema changes (Flyway migration), the PSD2 client behind an
   adapter interface (stubbed/sandbox-only for now — no production credentials exist yet), the
   sync job endpoints, and dedup logic. Testcontainers integration tests.
3. **Frontend**: surface receipt `source` (photo vs bank) in list/detail UI, a settings/connect
   screen for initiating the PSD2 consent flow, and surfacing dedup conflicts for manual review.
4. **DevOps**: only once Architect/Backend land — nothing here is deployable against real PKO
   data until the user has sandbox (or later, production) credentials, so this stays design/stub
   until credentials exist.

## Out of scope for this pass

- Actually registering on developers.pkobp.pl or obtaining any credentials — that's the user's
  own action (personal/business identity required).
- Production TPP/AISP certification — a business/legal step for the user, not engineering.
- Wiring real sandbox credentials into `.env` — do that once the user has registered and shares
  the sandbox OpenAPI spec so the adapter can be reconciled against real field names.
