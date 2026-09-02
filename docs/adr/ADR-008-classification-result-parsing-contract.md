# ADR-008: Defensive Parsing of Claude's `.result` Output in classify-receipts.sh

**Date:** 2026-09-02
**Status:** Accepted

**Context:** Every scheduled `classify-receipts.sh` run has failed since 2026-09-01 14:00 — 4
consecutive runs, evidenced in `infra/classify/classify-receipts.log`. Root cause: `claude -p ...
--output-format json`'s `.result` field is not reliably raw JSON, despite `prompt.md`'s "Output
format" section instructing "Respond with ONLY a single raw JSON object — no markdown code
fences, no prose before or after it." Observed non-compliant shapes in the log:

1. Markdown-fenced: ` ```json\n{...}\n``` `.
2. Fenced JSON preceded by an apology preamble ("Apologies — that tool call was a mistake and
   unrelated to this task. Here is the classification output as plain JSON, per the required
   format:") — evidence Claude attempted some tool call outside the `--allowedTools "Read"`
   allowlist, had it denied, narrated about that denial in-band, then produced the JSON anyway.

`classify-receipts.sh` currently does zero defensive parsing — it takes `jq -r '.result'` and
POSTs that string verbatim as the `classification-batch` request body. The backend correctly
rejects a non-JSON body, which correctly leaves the affected receipts `PENDING` per the existing
safe-retry design (no data was lost) — but classification made zero net progress across 4
consecutive scheduled runs, and a real receipt sat `PENDING` for nearly a day before this was
caught.

A prompt can request a specific output shape, but nothing invoked as `claude -p` can *force*
compliance from the model — prompt wording is inherently best-effort. The only place correctness
can actually be guaranteed is the wrapper script, which fully controls the string it hands to the
backend and can simply refuse to submit anything it cannot first validate as parseable JSON.

**Decision:** Harden both layers, with explicit, unequal weight:

1. **Primary fix — defensive parsing in `classify-receipts.sh`** (DevOps-owned file; this ADR is
   the contract DevOps implements against, unchanged by anyone else). Before treating `.result`
   as the batch payload, the script must:
   a. Strip a leading fence line if present — a line consisting of exactly ` ``` ` or
      ` ```json ` — and a trailing ` ``` ` fence line, if present.
   b. From what remains, take the substring from the first `{` to the last `}` inclusive. This
      discards any preamble/postamble prose regardless of its exact wording, without needing to
      special-case "apology," "explanation," or any other specific phrasing.
   c. Validate that the extracted substring is parseable JSON (`jq empty` or equivalent) before
      treating it as the batch and POSTing it to `/api/receipts/classification-batch`.
   d. If extraction or validation fails at any point, treat it **exactly** like the existing
      `is_error: true` path: log the failure (including the raw `.result`, for later debugging)
      and exit leaving every receipt in that run `PENDING` for the next scheduled slot. Never
      crash the whole batch over this, and never construct or submit a partial/best-effort
      payload — an extraction failure is a full no-op for that run, same as a quota failure.
2. **Secondary mitigation — `prompt.md` wording strengthened** (`infra/classify/prompt.md`, done
   alongside this ADR). The "ONLY raw JSON, no fences, no prose" instruction now opens the prompt
   (previously it appeared only in the "Output format" section near the end — effectively
   last-read and easiest to drift from under load), is paired with an explicit instruction not to
   attempt any tool beyond `Read` and not to narrate or apologize if a tool call is denied, and is
   reinforced with a concrete "what NOT to output" example reproducing both shapes actually seen
   in production. This is expected to reduce how often non-compliant output occurs; it cannot and
   does not guarantee compliance, so it is not a substitute for (1).

**Consequences:**
- `classify-receipts.sh` must implement steps 1a–1d exactly as specified above. No change to any
  endpoint or request/response shape: `POST /api/receipts/classification-batch` still expects the
  same `{items, failures}` (or, once bank import lands, `{items, uncertainCategory, failures}`)
  body — only now the script guarantees what it sends is actually that shape before sending it.
- The `FAILED` vs. stays-`PENDING` distinction is unaffected and must stay unaffected: a
  result-parsing failure is a script-side degrade, handled identically to `is_error: true` — it
  must never set a receipt to `FAILED`, and must never be confused with Claude's own
  `failures[]`/`uncertainCategory[]` reporting (both of which require successfully parsed JSON to
  even interpret, so they're only meaningful once step 1c has already succeeded).
- Prompt strengthening is a one-time, low-cost change with no guaranteed effect on model output.
  Future non-compliant shapes (different wording, different fence style) are expected to still
  occur occasionally. The wrapper script's defensive parsing — not the prompt wording — is the
  actual correctness guarantee going forward; a future recurrence should prompt a check of the
  script's extraction logic first, not another round of prompt tweaking alone.
- No backend change was needed or made. `classification-batch`'s contract (reject a non-JSON or
  invalid-shape body) was already correct — the bug was entirely upstream of it, in what the
  script was willing to send.
