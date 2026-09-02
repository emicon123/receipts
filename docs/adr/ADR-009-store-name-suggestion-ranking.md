# ADR-009: Ranking Algorithm for Store-Name Autocomplete Suggestions

**Date:** 2026-09-02
**Status:** Accepted

**Context:** The manual-entry form's "Sklep / dostawca (opcjonalnie)" field is becoming a
combobox — still free-text (unconstrained, optional, any typed value stays acceptable) — with
autocomplete suggestions drawn from `receipts.store_name` values the user has actually entered
before, across both `CAMERA` and `MANUAL` receipts (they share the one column). No such endpoint
existed; it needed a full contract, not just a route name, because "most used / last used" is
ambiguous on its own — several distinct algorithms all satisfy that phrase:

1. **Frequency-only:** order by usage count descending. Simple, but a store visited many times
   last year and never since could permanently outrank one the user just started frequenting.
2. **Recency-only:** order by most recent `capturedAt` descending. Simple, but a single one-off
   store (typed once, by typo or a rare purchase) would temporarily crowd out genuinely frequent
   stores until enough time passes — not "most used" at all, despite the requirement naming both.
3. **Frequency primary, recency as tiebreak.** Answers "which stores does this user actually
   shop at repeatedly" first (the stronger, more stable signal for a dropdown he'll scan while
   typing), while still surfacing "last used" as a secondary ordering — the tiebreak this app
   needs is exactly what happens after a landslide of `Lidl`/`Kaufland`-tier regulars: newer
   entries at the same frequency don't get stuck behind older ones just by insertion order.
4. **Weighted score (e.g. `count * recency_decay`)** — genuinely combines both signals
   continuously, but needs a decay function and a constant to tune; overkill for a personal,
   single-user dataset where "obviously frequent" stores dominate by a wide margin.

There was also a real ambiguity on **dedup**: camera-OCR-derived store names and manually-typed
ones will not always match byte-for-byte (`"Lidl"` vs. `"lidl "` vs. `" LIDL"`), and without a
decision here, near-duplicate suggestions would clutter a small dropdown meant to be scanned at a
glance.

**Decision:**
- **Ranking:** option 3 — group by normalized name, order by `COUNT(*) DESC` (usage) then
  `MAX(captured_at) DESC` (recency) as the tiebreak. Rejected (1) alone for going stale over
  time with no recency signal at all; rejected (2) alone because "most used" was an explicit,
  named half of the requirement, not something the ranking could silently drop in favor of pure
  recency; rejected (4) as unjustified complexity (YAGNI) for a dataset in the low thousands of
  rows where a two-key sort already produces an obviously-sensible order.
- **Dedup:** case-insensitive, trimmed (`lower(trim(store_name))`) as the grouping key. Within a
  group, display the exact-cased variant that occurs most often (tie-broken by its own most
  recent use) — this keeps casing sane without hard-coding a "prefer title case" rule that
  wouldn't generalize to every real store name.
- **Cap:** top 20 results, unpaginated — matches the existing precedent (`/receipts/pending`,
  `/categories`) for small, inherently-bounded collections in this app.

Full query shape recorded in `docs/architecture/02-domain-model-and-schema.md` § Store-Name
Suggestions (not duplicated here) and the endpoint contract in `docs/openapi.yaml`
(`GET /receipts/store-names`).

**Consequences:**
- No new index (`receipts.store_name` stays unindexed) — consistent with this app's existing
  YAGNI stance on `receipt_line_items.category` at this data scale (see
  `02-domain-model-and-schema.md` § Indexes). Revisit only if the dataset's scale assumption
  changes materially.
- A store used exactly once will still appear (there is no "must be used more than once" floor)
  as long as it ranks in the top 20 by the count/recency ordering above — intentional, since
  excluding it entirely would make the very first correction (typing a new store's name once)
  permanently invisible to autocomplete until a second, redundant entry.
- Once `BANK_IMPORT` lands (ADR-007), its counterparty names flow into this same endpoint
  automatically, with no query change — `store_name` is deliberately shared across sources (see
  `06-bank-integration.md`), and this endpoint was written with no `source` filter from the
  start.
