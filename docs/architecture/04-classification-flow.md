# 04 — Capture → Upload → Classify → Correct Flow

> **Audience:** All agents — this is the whole point of the app end to end.
>
> **Design-only addition (ADR-007):** the daily classification batch below is shown for the
> original `CAMERA`-only shape and remains accurate for it unchanged. Once bank import lands, the
> same batch also carries `BANK_IMPORT` ids (inline transaction text instead of an image
> download) and Claude's reply gains a third, `uncertainCategory[]` outcome — see
> `06-bank-integration.md`'s "Classification: Whole-Transaction, No Image" section for that
> extension; it is not duplicated into the diagrams below.

---

## Happy Path: Capture Through Correction

```mermaid
sequenceDiagram
    actor User
    participant PWA as React PWA (phone)
    participant Backend
    participant DB as PostgreSQL
    participant Cron as classify-receipts.sh (host cron)
    participant Claude as claude CLI (headless, --allowedTools Read)

    User->>PWA: Take photo of receipt
    PWA->>Backend: POST /api/receipts (multipart image)
    Backend->>DB: INSERT receipts (status=PENDING, source=CAMERA)
    Backend-->>PWA: 201 { data: ReceiptSummary(status=PENDING) }
    PWA-->>User: "Uploaded — categorized in the next daily run"

    Note over Cron: Scheduled run — 06:00 primary, or a same-day safety-net slot
    Cron->>Backend: GET /api/receipts/pending
    Backend->>DB: SELECT WHERE status='PENDING'
    Backend-->>Cron: 200 { data: [{id}, ...] } — pure read, no status change

    loop for each pending id
        Cron->>Backend: GET /api/receipts/{id}/image
        Backend-->>Cron: image bytes (downloaded to a local temp file)
    end

    Cron->>Claude: claude -p "<prompt.md + id→path manifest>"<br/>--output-format json --allowedTools Read
    Claude->>Claude: Read each image, extract + categorize line items
    Claude-->>Cron: {"items": [...], "failures": [...]}

    Cron->>Backend: POST /api/receipts/classification-batch
    activate Backend
    Backend->>DB: per items[] entry: PENDING→PROCESSING→PROCESSED<br/>(replace uncorrected line items, recompute total_amount)
    Backend->>DB: per failures[] entry: PENDING→PROCESSING→FAILED (failure_reason)
    Backend->>DB: any entry with an invalid category: routed to FAILED too<br/>(server-side reject, never silently coerced)
    deactivate Backend
    Backend-->>Cron: 200 { data: { processed, failed, skipped } }

    User->>PWA: Open receipt list later
    PWA->>Backend: GET /api/receipts?status=PROCESSED
    Backend-->>PWA: 200 { data: [...], page: {...} }
    User->>PWA: Open one receipt, sees a mis-categorized item
    PWA->>Backend: GET /api/receipts/{id}
    Backend-->>PWA: 200 { data: ReceiptDetail }
    User->>PWA: Edit the line item's category
    PWA->>Backend: PUT /api/receipts/{id}/line-items/{itemId}
    Backend->>DB: UPDATE line item (corrected=true), recompute total_amount
    Backend-->>PWA: 200 { data: LineItem }
```

Key contract points visible in this flow:
- Upload never blocks on classification — `POST /receipts` returns as soon as the file is
  stored, always `PENDING`, no model call on the request path.
- `GET /receipts/pending` is a pure read (see `03-receipt-lifecycle.md` for why this matters).
- Exactly **one** `claude -p` invocation per cron run, covering the whole batch — not one per
  receipt (ADR-002's cost/usage-limit rationale).
- Claude never calls the backend. Every HTTP call in this diagram to/from `Backend` originates
  from either the PWA or the wrapper script — `--allowedTools "Read"` gives Claude nothing else.
- A correction (`PUT .../line-items/{itemId}`) never re-invokes the classifier and never changes
  `receipts.status` — it's a pure data edit plus a `total_amount` recompute.

---

## Retry on Usage-Limit Exhaustion

```mermaid
sequenceDiagram
    actor Cron as classify-receipts.sh — 06:00 primary run
    participant Backend
    participant DB as PostgreSQL
    participant Claude as claude CLI (headless)

    Cron->>Backend: GET /api/receipts/pending
    Backend-->>Cron: 200 { data: [{id:1},{id:2},{id:3}] }
    Cron->>Backend: GET /api/receipts/{id}/image (x3)
    Backend-->>Cron: image bytes

    Cron->>Claude: claude -p "<prompt>" --allowedTools Read
    Claude--xCron: usage limit exhausted<br/>(non-zero exit, or is_error:true in the JSON wrapper)

    Note over Cron,DB: Script logs the failure and exits WITHOUT calling<br/>classification-batch. No endpoint ever touched<br/>receipts 1-3's status — they are still PENDING,<br/>exactly as GET /pending left them.

    Note over Cron: Later, same day — a safety-net slot (e.g. 12:00)
    Cron->>Backend: GET /api/receipts/pending
    Backend-->>Cron: 200 { data: [{id:1},{id:2},{id:3},{id:4}] }<br/>(id 4 = uploaded between the two runs)
    Cron->>Backend: GET /api/receipts/{id}/image (x4)
    Backend-->>Cron: image bytes
    Cron->>Claude: claude -p "<prompt>" --allowedTools Read
    Claude-->>Cron: {"items":[...], "failures":[...]}  (limit reset, succeeds)
    Cron->>Backend: POST /api/receipts/classification-batch
    Backend->>DB: all 4 receipts transitioned PROCESSED/FAILED
    Backend-->>Cron: 200 { data: {...} }
```

Why this is safe with **no special-cased quota detection**: the script's failure handling is
uniform for *any* `claude` invocation failure, not specifically a quota error (CLAUDE.md is
explicit about this). Because `GET /pending` never mutates state and the script only ever writes
to the backend via one call — `classification-batch`, made strictly after a successful `claude`
run — there is no intermediate state to unwind on failure. The next scheduled slot's
`GET /pending` naturally re-includes every receipt from the failed run, plus anything uploaded
since, with zero bookkeeping required in the script itself.

The primary run is at 06:00 rather than overnight because losing a whole day of classification
is higher-stakes for this app's core purpose than investing-app's nightly news job losing one
night — hence the extra same-day safety-net slots, each a cheap no-op unless the primary run
actually failed (an empty `GET /pending` response makes the script exit immediately, per
CLAUDE.md § Daily classification job step 1 — no `claude` invocation spent on an empty queue).
