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

manifest=""
while IFS= read -r id; do
  img="${TMP_DIR}/receipt-${id}.jpg"
  curl -sf "${API_BASE}/receipts/${id}/image" -o "${img}" || fail "could not download image for receipt ${id}"
  manifest="${manifest}
- id=${id} path=${img}"
done < <(echo "${pending_json}" | jq -r '.data[].id')

full_prompt="$(cat "${PROMPT_FILE}")
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

curl -sf -X POST "${API_BASE}/receipts/classification-batch" \
  -H "Content-Type: application/json" \
  -d "${batch}" || fail "backend rejected the classification batch: ${batch}"

processed="$(echo "${batch}" | jq '.items | length')"
failed="$(echo "${batch}" | jq '.failures | length')"
log "Submitted batch: ${processed} processed, ${failed} failed, out of ${count} pending."
