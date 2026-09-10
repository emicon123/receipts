# ADR-010: Free-Text Line-Item Subcategories + a Separate Drill-Down Endpoint

**Date:** 2026-09-10
**Status:** Accepted

**Context:** The Wydatki (Expenses) dashboard's `CategoryBreakdownChart` shows one bar per fixed
category (e.g. "Jedzenie pierdołowate") with a single monthly total. The user wants to click a
bar and see what actually makes up that total — e.g. within "Jedzenie pierdołowate": a
"Słodycze" group, further split into "żelki" / "batony" / "czekolady". This needs two new
pieces of data per line item (a `subcategory` and a finer `subSubcategory`) and a way to fetch
them scoped to one category/month, on click, without slowing down the dashboard's normal load.

Three questions needed resolving before Backend/Frontend could start:
1. Are `subcategory`/`subSubcategory` a fixed, closed list like `category` (ADR-005), or
   free text?
2. Do existing line items get backfilled, and are the new columns nullable?
3. Does the drill-down data ride on `GET /spending/summary`, or live on its own endpoint?

**Decision:**

### 1. Free text, not a fixed enum

`receipt_line_items` gains two nullable `VARCHAR(100)` columns — `subcategory`,
`sub_subcategory` — with **no enum type, no lookup table, no `CHECK` constraint**, added in
`V2__line_item_subcategories.sql`. Claude assigns both per line item during the daily
classification batch (`infra/classify/prompt.md`), and is asked only to *reuse* a label
consistently within one batch (e.g. always "Słodycze", never also "Słodkości" for the same
concept) — this is prompt-level advisory guidance, not a server-side validated rule.

This is a deliberate departure from ADR-005's fixed `spend_category_enum`, not a reconsideration
of it: `category`'s 11 values are a **user-given, final, closed list** — exactly the case ADR-005
argued a DB enum fits. `subcategory`/`subSubcategory` are the opposite shape: an open-ended,
classifier-generated taxonomy that's expected to grow organically as new products get
photographed (a fixed enum would need a migration every time a new snack brand appears; a
lookup table would need the CRUD machinery ADR-005 already rejected for the *closed* list — both
are worse fits here, not better ones, for an *open* list). A free-text column is also what
CLAUDE.md's quality gate already implies isn't required here: that gate demands strict
enum-validation specifically for `category` ("Claude must never invent a category outside the
fixed 11... validate its output... flag any mismatch") — it says nothing about subcategories,
because there is no closed list to invent outside of.

**Consequence:** there is no server-side rejection path for a weird or inconsistent
`subcategory`/`subSubcategory` value, unlike an out-of-enum `category` (which routes the whole
receipt to `FAILED`). A batch entry with a wildly inconsistent label still succeeds — the worst
case is a slightly messier drill-down grouping for that receipt, not a failed receipt. This
trade-off is intentional: forcing validation onto an intentionally-open field would just move the
"invent a category" failure mode from *rejecting* good data to *rejecting* an otherwise-fine
receipt over a label nobody asked to be strict.

### 2. Nullable columns, no backfill

Both columns are `NULL`-able with no default beyond `NULL`, and no migration or job backfills
existing rows. Every line item created before `V2` (and any classification-batch/manual entry
that simply omits the fields, e.g. a receipt reprocessed with an older prompt version before this
change fully deploys) stays `NULL` indefinitely unless that specific receipt is reprocessed.
This was scoped out explicitly by the user up front — no bulk reclassification of historical
receipts — and matches how this app has always treated the classifier's output: additive,
forward-only, never a batch job over old data. The frontend drill-down view is responsible for a
graceful "Inne"/ungrouped fallback bucket for `NULL` entries rather than expecting every line
item to have both fields.

### 3. A new, separate, lazily-fetched endpoint — not a nested field on `/spending/summary`

`GET /spending/line-items?year=&month=&category=` is a new endpoint, not an optional nested
breakdown added to `GET /spending/summary`'s response. It returns an unpaginated flat list of
that category/month's line items (`productName`, `amount`, `quantity`, `subcategory`,
`subSubcategory`, plus `receiptId`/`storeName`/`capturedAt` denormalized from the parent receipt
for display and for linking back to `/receipts/:id`) — reusing the existing `LineItem` schema via
an `allOf` composition (`SpendingLineItem`), the same pattern `ReceiptDetail` already uses to
extend `ReceiptSummary`, rather than inventing a third parallel line-item shape.

Two shapes were considered:

| Option | Verdict | Why |
|---|---|---|
| Nest a nullable `lineItems[]` (or 11 pre-grouped breakdowns) inside `SpendingSummaryData` | **rejected** | `GET /spending/summary` is called **eagerly on every dashboard visit** and must stay a cheap, fixed-size 11-row aggregate (CLAUDE.md's "keep it fast" spirit for the dashboard's primary load). Line items for 11 categories × a month's receipts is a materially larger payload for data the user views for, at most, one category per click — most of it would be fetched and thrown away unread on every single dashboard load. |
| New `GET /spending/line-items?year&month&category`, fetched only on click | **adopted** | Matches the task's own framing exactly: "drill-down data should be fetched lazily only when the user clicks a category, not eagerly with the summary." Scoping by `category` server-side (rather than returning all categories' line items and filtering client-side) also keeps even the *lazy* fetch itself minimal — one category/month slice, not a whole month's line items. |

Pattern/principle note (per the `software-design-excellence` skill's §1 — recorded briefly here
since this task is additive and doesn't warrant a new diagram; see
`02-domain-model-and-schema.md`'s evaluation table for the two rows this decision added): this is
a resource-shape (API design) call, not a GoF pattern choice — no new creational/structural/
behavioral pattern is introduced. `SpendingLineItem`'s `allOf` reuse of `LineItem` is the same
schema-composition approach already used for `ReceiptDetail`, applied consistently rather than
invented fresh.

### 4. `corrected`'s sticky-edit protection is explicitly NOT extended to these fields (this task)

`receipt_line_items.corrected` continues to guard only `productName`/`category`/`amount`/
`quantity` — the fields `PUT /receipts/{id}/line-items/{itemId}` can actually change today. This
task adds **no edit UI** for `subcategory`/`subSubcategory` (read-only, classifier-supplied
display data only, per the task's explicit scope), so a reprocess is free to overwrite either
field on every line item, including one the user has separately hand-corrected via the existing
fields. This is a deliberate scope decision, not an oversight: extending `corrected`'s protection
to two fields nobody can yet edit by hand would be speculative (YAGNI) — there is no data loss
risk to guard against until a future task actually adds that edit UI, at which point extending
`corrected` (or introducing a second, finer-grained correction flag) becomes a live decision to
make then, not now.

### 5. Cross-batch label consistency: `GET /receipts/subcategory-labels` fed back into the prompt

**Refinement, added 2026-09-10 — same decision, not a new ADR.** Point 1 above asked Claude to
"reuse a label consistently within one batch." That guidance has a hole: `classify-receipts.sh`
invokes a fresh, stateless `claude -p` process on every scheduled run, with no memory of any
prior run. "Within one batch" consistency does nothing to stop Claude labeling a gummy-bear line
item "Batony" today and "Batony czekoladowe" next week — there is no persisted vocabulary for the
next day's invocation to check itself against, so nothing actually prevents that drift over time,
only within a single day's run.

This is the identical problem `/receipts/store-names` (ADR-009) already solves for `store_name`
drift — Claude/the user typing `"Lidl"` one day and `"lidl "` another — just for a different
field, and for a different *kind* of reader (a prompt fed to Claude, not a combobox scanned by a
human). Rather than invent a new mechanism, this refinement mirrors ADR-009's shape:

- **New endpoint, `GET /receipts/subcategory-labels`** — not an extension of `/receipts/store-names`
  into some generic "known values of field X" endpoint. The response needed
  (`category → subcategory → subSubcategory[]`, category-scoped, since "Batony" only means
  anything under `JEDZENIE_PIERDOLOWATE`) is a different, hierarchical shape from store-names'
  flat ranked list, for a consumer (the prompt-builder script) that will never share a call site
  with the PWA's combobox. Full contract in `docs/openapi.yaml`; query shape in
  `docs/architecture/02-domain-model-and-schema.md` § Known Subcategory/Sub-Subcategory Labels.
- **Same ranking/dedup algorithm as ADR-009**, applied at both the `subcategory` and
  `subSubcategory` levels: normalize via `lower(trim(...))` for grouping, rank by usage count
  descending then most-recent `capturedAt` descending, display the most-frequent exact-cased
  variant per group. Reused verbatim rather than reinvented.
- **Different cap than store-names' top-20.** Top 30 `subSubcategories` per subcategory group —
  store-names' cap of 20 exists because a human scans a dropdown; this list is read by Claude, so
  the constraint is prompt-size growth, not UI legibility. No cap on the number of distinct
  `subcategories` per category (expected to stay single-digit at this app's scale).
- **`infra/classify/prompt.md` gains a `{{KNOWN_LABELS_MANIFEST}}` placeholder**, in a new "Known
  labels from previous runs" subsection, with instructions to reuse an existing label verbatim
  whenever it reasonably fits and to check the list before inventing a new one. This is still
  advisory, not enforced — consistent with the rest of this ADR, nothing downstream rejects a
  batch over a label that ignores the known list.

**This still requires a DevOps-owned change this ADR does not implement.** Spelled out precisely
here because `infra/classify/classify-receipts.sh` is DevOps-owned, not Architect-owned, and the
next agent to touch it needs an unambiguous spec, not just "fetch labels somehow":

1. **When:** immediately after the existing pending-count check — i.e. only on a run where
   `GET /receipts/pending` returned at least one receipt (same "don't spend effort on an empty
   queue" rule the job already follows for the `claude` invocation itself). This is a new step
   between "found N pending receipts" and the existing image-download loop.
2. **What to fetch:** one `GET {API_BASE}/receipts/subcategory-labels` call, same base URL and
   same reachability-failure handling as the existing `GET {API_BASE}/receipts/pending` call
   immediately above it in the script (i.e. `curl -sf ... || fail "..."` — a failure here means
   the backend that just answered `/receipts/pending` a moment earlier is now unreachable, which
   is a script/infra problem worth aborting the run over via the existing `fail()` path, not a
   content-quality problem to silently degrade past).
3. **Response shape to expect:** `SubcategoryLabelsResponse` (`docs/openapi.yaml`) —
   `{ data: [ { category, subcategories: [ { subcategory, subSubcategories: [string, ...] } ] } ], meta }`.
   `data` may be `[]` (e.g. the very first-ever classification run).
4. **Transform into the exact manifest text `prompt.md` expects** — nested Markdown bullets, one
   top-level bullet per category, one nested bullet per subcategory listing its
   `subSubcategories` comma-joined (or nothing after the colon-less subcategory name if that
   list is empty):
   ```
   - JEDZENIE_PIERDOLOWATE
     - Słodycze: żelki, batony, czekolady
     - Chipsy: chipsy ziemniaczane
   - ALKO
     - Piwo: piwo jasne, piwo bezalkoholowe
   - RACHUNKI
     - Prąd i gaz
   ```
   If `data` is empty, the manifest text is instead the single literal line
   `(none recorded yet — use your own best judgment for every subcategory/subSubcategory.)`
   (this exact line is echoed in `prompt.md`'s own explanation of what an empty section means, so
   don't rephrase it). A `jq` filter producing the non-empty case, illustrative rather than
   mandated (mirrors ADR-009's own "this is the shape, not a mandated literal query" disclaimer):
   ```
   jq -r '.data[] | "- " + .category,
     (.subcategories[] | "  - " + .subcategory +
       (if (.subSubcategories | length) > 0
        then ": " + (.subSubcategories | join(", "))
        else "" end))'
   ```
5. **Splice into `prompt.md`, not append.** Unlike the existing `id → path` receipts manifest
   (appended after the whole static file, since "## Receipts to classify" is already the file's
   last section), the known-labels manifest replaces a literal placeholder token,
   `{{KNOWN_LABELS_MANIFEST}}`, that appears exactly once, mid-file, in `prompt.md`. Because the
   replacement text spans multiple lines and may itself contain characters that collide with a
   naive `sed s/.../.../` delimiter, do the substitution with a technique that treats the token as
   a literal string over the whole file (e.g. split on the placeholder line via `awk`/`csplit` and
   concatenate head + rendered manifest + tail, or a `perl -0777` slurp-mode substitution) — not a
   single-line `sed` expression. This produces the prompt text *before* the script's existing
   final step of appending the receipts manifest, so `full_prompt` ends up carrying both dynamic
   sections: known-labels spliced mid-file, `id → path` lines appended at the end, unchanged from
   today.
6. **No other behavior changes.** Everything else about the job — one `claude -p` invocation per
   run, `--allowedTools "Read"` only, the `is_error`/ADR-008 defensive-parsing failure handling,
   "any failure this run leaves every receipt PENDING" — is unaffected. This is purely an
   additional read (step 2) and an additional template substitution (step 5) before the existing
   `claude` invocation.

**Consequences:**
- Flyway migration `V2__line_item_subcategories.sql`: two nullable `VARCHAR(100)` columns, no
  enum, no `CHECK`, no backfill, no new index (see `02-domain-model-and-schema.md` § Indexes for
  why — the one query that reads them is already scoped to a small month+category slice).
- `infra/classify/prompt.md` extended: every line item (photo receipts and, design-only per
  ADR-007, bank transactions) now also gets `subcategory`/`subSubcategory`, with explicit
  guidance to reuse exact labels within a batch and an "always guess, never omit" policy matching
  `category`'s — but framed as advisory, since nothing downstream validates it. Further extended
  (point 5 above) with a `{{KNOWN_LABELS_MANIFEST}}` placeholder so that guidance also holds
  *across* runs, not just within one.
- `LineItemInput` (shared by `ManualReceiptRequest` and `ClassificationBatchItem`) gains both
  fields as optional/nullable — a batch or manual entry omitting them must not fail validation.
  `LineItemCorrectionRequest` is **not** changed in this task (see point 4 above).
- `docs/openapi.yaml` gains `GET /spending/line-items`, `SpendingLineItem`,
  `SpendingLineItemsResponse`, and extends `LineItem`/`LineItemInput`. The cross-batch refinement
  further adds `GET /receipts/subcategory-labels`, `SubcategoryLabelGroup`,
  `CategorySubcategoryLabels`, `SubcategoryLabelsResponse`.
- **`infra/classify/classify-receipts.sh` needs a new fetch-and-splice step (point 1-5 above) —
  not implemented by this ADR.** This is a required follow-up for DevOps before the cross-batch
  consistency mechanism actually takes effect; until that script change lands, `prompt.md`'s
  `{{KNOWN_LABELS_MANIFEST}}` placeholder would be sent to Claude literally unreplaced, which is
  harmless (Claude would just see an odd token and fall back to its own judgment) but defeats the
  point of this refinement.
- If this list of subcategories/sub-subcategories ever needs to become closed, curated, or
  user-editable (e.g. the user starts wanting a dropdown instead of freeform classifier output),
  that is a new ADR revisiting this one — not an in-place amendment, per the same reasoning
  ADR-005 already established for why the *main* category is a migration-gated enum: a closed
  list is a bigger commitment than an open one and deserves its own decision record when/if it
  actually happens.
