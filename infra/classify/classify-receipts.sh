#!/usr/bin/env bash
# Daily receipt classification job. Mirrors investing-app's infra/news/news-research.sh pattern:
# a plain host cron job, an already-authenticated `claude` CLI invocation, a static prompt
# template, and the wrapper script (not Claude) doing all backend HTTP I/O.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
API_BASE="${API_BASE:-http://localhost:${BACKEND_PORT:-8080}/api}"
CLAUDE_BIN="${CLAUDE_BIN:-/home/wojtekrpi/.local/bin/claude}"
PROMPT_FILE="${SCRIPT_DIR}/prompt.md"
LOG_FILE="${SCRIPT_DIR}/classify-receipts.log"
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "${TMP_DIR}"' EXIT

log() { echo "[$(date -Iseconds)] $*" >> "${LOG_FILE}"; }
fail() { log "ERROR: $*"; exit 1; }

# ADR-008: defensive parsing of Claude's `.result` field. `claude -p ... --output-format json`
# does not reliably return raw JSON in `.result` — it has come back wrapped in a markdown code
# fence (```json ... ```), and once with a stray prose preamble before the fence (narrating a
# denied out-of-allowlist tool call). Implements the ADR's contract exactly:
#   a. strip a leading fence line (exactly ``` or ```json) and a trailing ``` fence line, if present
#   b. from what remains, take the substring from the first '{' to the last '}' inclusive —
#      this discards any preamble/postamble prose regardless of its wording
#   c. validate the result parses as JSON before it's ever treated as the batch payload
# Prints the validated JSON to stdout and returns 0 on success. Returns 1 (no output) on any
# extraction/validation failure, WITHOUT ever tripping `set -e` itself — callers must check the
# return status explicitly (e.g. `batch="$(extract_batch_json "${raw}")" || { ...; exit 0; }`,
# same style as the other `set -e`-safe call sites in this script) and, per the ADR, treat a
# failure here exactly like the existing `is_error: true` path: log it and leave every receipt
# in the run PENDING — never `fail()`, never a partial/best-effort submission.
extract_batch_json() {
  local raw="$1"
  local stripped
  local extracted

  stripped="$(printf '%s' "${raw}" | sed -e '1{/^```json$/d;/^```$/d}' -e '${/^```$/d}')"

  extracted="$(printf '%s' "${stripped}" | grep -zoP '(?s)\{.*\}' 2>/dev/null | tr -d '\0')" || true
  if [ -z "${extracted}" ]; then
    return 1
  fi

  if ! printf '%s' "${extracted}" | jq empty >/dev/null 2>&1; then
    return 1
  fi

  printf '%s' "${extracted}"
}

pending_json="$(curl -sf "${API_BASE}/receipts/pending")" || fail "could not reach backend"
count="$(echo "${pending_json}" | jq '.data | length')"

if [ "${count}" -eq 0 ]; then
  log "No pending receipts — nothing to do."
  exit 0
fi

log "Found ${count} pending receipt(s) — downloading images."

# ADR-010 §5 points 1-3: cross-batch subcategory/subSubcategory label consistency. Only reached
# on a non-empty run (same "don't do extra work on an empty queue" guard as the claude invocation
# itself). Same reachability-failure-aborts-the-run handling as the /receipts/pending call above.
log "Fetching known subcategory/subSubcategory labels for cross-batch consistency."
labels_json="$(curl -sf "${API_BASE}/receipts/subcategory-labels")" || fail "could not reach backend"

# ADR-010 §5 point 4: render the SubcategoryLabelsResponse into the exact nested-bullet manifest
# text prompt.md's "Known labels from previous runs" subsection expects — one top-level bullet
# per category, one nested bullet per subcategory listing its subSubcategories comma-joined (or
# nothing after the subcategory name if that list is empty). `data` may be `[]`, in which case the
# manifest is instead the single literal fallback line, matched verbatim to what prompt.md itself
# already says this fallback means — don't reword it.
known_labels_manifest="$(echo "${labels_json}" | jq -r '
  if (.data | length) == 0 then
    "(none recorded yet — use your own best judgment for every subcategory/subSubcategory.)"
  else
    (.data[] | "- " + .category,
      (.subcategories[] | "  - " + .subcategory +
        (if (.subSubcategories | length) > 0
         then ": " + (.subSubcategories | join(", "))
         else "" end)))
  end
')"

# ADR-010 §5 point 5: splice (not append) the manifest into prompt.md's mid-file, exactly-once
# {{KNOWN_LABELS_MANIFEST}} placeholder. This is a multi-line replacement, so it deliberately does
# NOT use a single-line `sed s/.../.../` — that would need the replacement text escaped for the
# delimiter and can't cleanly substitute embedded newlines. Instead awk does a full-file, literal
# ($0 == token, no regex) line match and prints the rendered manifest in place of that one line.
# The manifest text is passed via ENVIRON rather than an awk -v assignment specifically to avoid
# -v's backslash-escape processing mangling any label text that happens to contain a backslash.
prompt_with_labels="$(MANIFEST_TEXT="${known_labels_manifest}" awk -v token='{{KNOWN_LABELS_MANIFEST}}' '
  $0 == token { print ENVIRON["MANIFEST_TEXT"]; next }
  { print }
' "${PROMPT_FILE}")"

# ADR-014: a pending receipt is an image from the camera (CAMERA) or one imported from the gallery /
# clipboard (IMAGE_IMPORT — often a PNG screenshot, or a HEIC-converted JPEG). The image's real type
# is only known once the response arrives, so each image is downloaded to a neutral name first and
# then renamed to receipt-<id>.<ext> from the response's Content-Type — the Read tool decodes by file
# extension, so a PNG saved as `.jpg` would not be read correctly. Any download failure or an
# unsupported Content-Type is handled exactly like the pre-existing "could not download" case: fail()
# before the claude call, so nothing is submitted and every receipt in the run stays PENDING.
# `source` comes from GET /receipts/pending's `{id, source}` entries (absent on an older backend
# -> CAMERA, which is also how prompt.md treats a path= line with no source=).
manifest=""
while IFS=$'\t' read -r id receipt_source; do
  download="${TMP_DIR}/receipt-${id}.download"
  content_type="$(curl -sf -o "${download}" -w '%{content_type}' "${API_BASE}/receipts/${id}/image")" \
    || fail "could not download image for receipt ${id}"

  # Normalize the Content-Type before matching: drop any ";charset=..." style parameter,
  # lower-case it, and strip stray whitespace.
  mime="${content_type%%;*}"
  mime="${mime,,}"
  mime="${mime//[[:space:]]/}"
  case "${mime}" in
    image/jpeg) ext="jpg" ;;
    image/png)  ext="png" ;;
    image/webp) ext="webp" ;;
    *) fail "unsupported image Content-Type '${content_type}' for receipt ${id} (expected image/jpeg, image/png or image/webp)" ;;
  esac

  img="${TMP_DIR}/receipt-${id}.${ext}"
  mv "${download}" "${img}"
  log "Downloaded receipt ${id} (${receipt_source}, ${ext})."
  manifest="${manifest}
- id=${id} path=${img} source=${receipt_source}"
done < <(echo "${pending_json}" | jq -r '.data[] | [.id, ((.source // "") | if . == "" then "CAMERA" else . end)] | @tsv')

full_prompt="${prompt_with_labels}
${manifest}"

# --allowedTools "Read" only: Claude's job here is purely to read the downloaded images and
# emit JSON. All backend calls (before and after) are done by this script, not by Claude, so it
# never needs Bash/network tool access.
result_json="$("${CLAUDE_BIN}" -p "${full_prompt}" --output-format json --allowedTools "Read" 2>>"${LOG_FILE}")" \
  || { log "claude invocation failed (possibly a usage-limit exhaustion) — leaving all ${count} receipt(s) PENDING for the next scheduled slot"; exit 0; }

is_error="$(echo "${result_json}" | jq -r '.is_error')"
if [ "${is_error}" = "true" ]; then
  log "Claude reported an error — leaving all ${count} receipt(s) PENDING for the next scheduled slot: $(echo "${result_json}" | jq -r '.result' | head -c 300)"
  exit 0   # not a script failure — just no progress this run; a later cron slot retries
fi

raw_result="$(echo "${result_json}" | jq -r '.result')"

# ADR-008: `.result` is not reliably raw JSON (markdown fences, occasional stray prose) — extract
# and validate defensively rather than trusting it verbatim. A failure here is not a script/infra
# error, so it's handled exactly like the `is_error: true` case above: log and leave every receipt
# in this run PENDING for the next scheduled slot, never `fail()`, never a partial submission.
batch="$(extract_batch_json "${raw_result}")" \
  || { log "Could not extract valid JSON from Claude's result — leaving all ${count} receipt(s) PENDING for the next scheduled slot. Raw result: ${raw_result}"; exit 0; }

# ADR-011: independent, script-side sanity check — for each items[] entry carrying a `total`
# (the receipt's own printed grand total, verification-only), compare it against
# sum(lineItems[].amount) using the same ±0.05 zł tolerance as Claude's own in-prompt self-check.
# Cents (integers) are used for the comparison rather than raw zł floats to avoid false positives
# from floating-point rounding right at the tolerance boundary. This is a diagnostic safety net,
# not a gate: a mismatch only logs a WARNING (id, computed sum, printed total, delta) and never
# blocks, fails, or marks anything FAILED — the batch is still submitted as-is regardless.
while IFS=$'\t' read -r mismatch_id mismatch_sum mismatch_total mismatch_delta; do
  log "WARNING: receipt ${mismatch_id} line-item sum (${mismatch_sum}) does not match its printed total (${mismatch_total}) — delta ${mismatch_delta} zł exceeds the ±0.05 zł tolerance (ADR-011)"
done < <(echo "${batch}" | jq -r '
  (.items // [])[]
  | select(has("total"))
  | . as $item
  | ((($item.lineItems // []) | map(.amount) | add) // 0) as $sum
  | (($sum * 100) | round) as $sum_cents
  | (($item.total * 100) | round) as $total_cents
  | ($sum_cents - $total_cents) as $diff_cents
  | (if $diff_cents < 0 then -$diff_cents else $diff_cents end) as $delta_cents
  | select($delta_cents > 5)
  | [$item.receiptId, $sum, $item.total, ($delta_cents / 100)] | @tsv
')

# ADR-011: `total` is verification-only and has no slot in ClassificationBatchItem — this app's
# Jackson config has no fail-on-unknown-properties override, so its default
# (FAIL_ON_UNKNOWN_PROPERTIES=true) would 400 the entire batch if `total` were left in. Strip it
# from every items[] entry now, after the sanity check above, before the POST below.
batch="$(echo "${batch}" | jq '.items = ((.items // []) | map(del(.total)))')"

curl -sf -X POST "${API_BASE}/receipts/classification-batch" \
  -H "Content-Type: application/json" \
  -d "${batch}" || fail "backend rejected the classification batch: ${batch}"

processed="$(echo "${batch}" | jq '.items | length')"
failed="$(echo "${batch}" | jq '.failures | length')"
log "Submitted batch: ${processed} processed, ${failed} failed, out of ${count} pending."
