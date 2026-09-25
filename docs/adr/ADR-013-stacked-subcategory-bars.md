# ADR-013: Stacked Subcategory Bars — Per-Bar Top-N Shading, Client-Side Bucketing, Lazy Aggregate Endpoint

**Date:** 2026-09-25
**Status:** Accepted

**Context:** The Wydatki dashboard shows one single-colour bar per category
(`GET /spending/summary`). The user wants a "Szczegóły" mode where each bar is split into
segments by subcategory (the free-text `subcategory` column from ADR-010), next to the existing
"Podsumowanie" mode, which stays unchanged. Live data constrains the design:

- ~30 distinct subcategory labels in total, up to 13 within one category.
- The same label appears under several categories ("Nabiał", "Napoje").
- Historical line items have `subcategory = NULL` (ADR-010: no backfill).
- Labels vary in casing/whitespace ("Słodycze" vs "słodycze ").

Four questions needed answers: how to colour segments, how many segments per bar, where the
bucketing happens, and how the data gets fetched.

**Decision:**

### 1. No global colour per subcategory label, and no global legend

A stable colour per label would need ~30 distinguishable colours. That is far past what a
categorical palette can keep legible on a phone. It would also paint the same label in the same
colour across unrelated categories ("Napoje" under `JEDZENIE_PIERDOLOWATE` and under
`JEDZENIE_SREDNIE`), which suggests a cross-category link the chart doesn't mean. Colour is
therefore **per bar and by rank**, not by label: segments are read through the tooltip (name,
amount, share of the category), not through a legend.

### 2. Top 3 shaded segments plus a grey "Reszta" segment per bar

Per category bar, going left to right:
- The **top 3 labeled subcategories by amount** are drawn in shades of one hue
  (`--chart-series-1`), darkest for the largest.
- **One neutral grey "Reszta"** segment holds everything else: every lower-ranked labeled
  subcategory plus the category's `unlabeledAmount` (NULL/blank subcategory).
- **Edge rule:** if the remainder is exactly one labeled subcategory and `unlabeledAmount == 0`,
  that subcategory is drawn as a 4th shade instead of a one-item "Reszta". A bar therefore has at
  most 4 segments, and "Reszta" only appears when it groups at least two things or includes
  unlabeled spend.
- Unlabeled spend is **never** its own coloured segment. It is always part of "Reszta", because it
  is a catch-all bucket, not a grouping the user can compare against the others. The drill-down's
  "Inne" bucket is sorted last for the same reason.
- Segments sum exactly to the category total, so the bar length matches "Podsumowanie" mode.
- Clicking any segment or bar still opens the existing drill-down
  (`/dashboard/category/:category?year&month`, ADR-010). The full per-subcategory picture lives
  there.

### 3. Server returns the full sorted list; the client does top-N and "Reszta"

`GET /spending/subcategory-summary?year&month` returns, for all 11 categories (zero-filled, in
canonical order), `totalAmount`, `unlabeledAmount`, and the **full** `subcategories[]` list of
`{subcategory, amount}` sorted by amount descending. The server applies no top-N cut and emits no
"Reszta" entry.

| Option | Verdict | Why |
|---|---|---|
| Server buckets (returns top-N + "Reszta") | rejected | N is a presentation choice tied to bar width and legibility, and the 3-or-4 edge rule is a rendering rule. Putting it in the API couples the contract to one chart. The accessible table fallback also needs every subcategory, which a pre-bucketed response would hide. |
| Server returns full sums, client buckets | **adopted** | Bucketing at most ~13 entries per bar is trivial client work. The payload is tiny (~30 rows a month). The server keeps doing the one thing only it can do well: exact `NUMERIC` sums and the normalization rule. |

`unlabeledAmount` is a **dedicated field, not a `subcategory: null` entry** in the list. This
keeps `subcategories[].subcategory` a non-nullable string in both Java and TypeScript, leaves no
question about where a null entry would sort, and hands the client exactly the value it adds to
"Reszta".

**Normalization** reuses ADR-010 §5 as written: group by `lower(trim(subcategory))` and show the
most-used exact-cased variant, tie-broken by recency. The only difference is scope. Casing is
picked within the month's slice rather than across all history, which keeps the endpoint to one
aggregate query; the cost is that a rare month might show a lower-cased variant. Backend extracts
the shared normalize/casing logic out of `SubcategoryLabelGrouper` into one package-private helper
so the rule lives in one place (see `02-domain-model-and-schema.md` § Subcategory Spending
Breakdown).

`subSubcategory` is not aggregated here (YAGNI). `/spending/line-items` stays the drill-down for
that level.

### 4. A new, lazily fetched aggregate endpoint

| Option | Verdict | Why |
|---|---|---|
| Nest the breakdown in `GET /spending/summary` | rejected | Same reasoning as ADR-010 §3. The summary is the eager call on every dashboard visit and must stay a fixed 11-row aggregate, while most visits never open "Szczegóły". |
| Call `/spending/line-items` for all 11 categories | rejected | 11 round-trips that ship every line item of the month just to draw segment widths. |
| New `GET /spending/subcategory-summary`, fetched only when "Szczegóły" is selected | **adopted** | One small call, only when needed. `/spending/summary` stays untouched. |

The new endpoint has exactly the same scope as `/spending/summary` (`PROCESSED` receipts, same
UTC month window). Per category, `totalAmount == unlabeledAmount + Σ subcategories[].amount`,
and that equals the summary's amount. Backend integration tests check this equality directly.

**Consequences:**
- `docs/openapi.yaml` gains `GET /spending/subcategory-summary` and the schemas
  `SubcategoryAmount`, `CategorySubcategoryBreakdown`, `SpendingSubcategorySummaryData` and
  `SpendingSubcategorySummaryResponse`. `/spending/summary` is unchanged. No schema migration.
- No per-label colour means a label can't be tracked across bars by colour. The tooltip and the
  drill-down cover that, and the table fallback lists every subcategory in text.
- Rank-based shading means "darkest" in one bar and "darkest" in another are different
  subcategories. This is intentional (colour encodes rank within a bar, not identity), and the
  tooltip always names the segment.
- Known minor divergence: the existing drill-down (`CategoryLineItemGroups.tsx`) groups by
  `trim()` only, not `lower(trim())`, so a casing split that the stacked bar merges can still
  show as two groups in the drill-down. Aligning it is a cheap, optional frontend follow-up. It
  isn't required for this feature's correctness.
- If per-label colour identity is ever wanted (e.g. after labels are curated into a small closed
  list, per ADR-010's closing note), that is a new ADR revisiting §1.
