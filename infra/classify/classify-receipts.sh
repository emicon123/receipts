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

batch="$(echo "${result_json}" | jq -r '.result')"

curl -sf -X POST "${API_BASE}/receipts/classification-batch" \
  -H "Content-Type: application/json" \
  -d "${batch}" || fail "backend rejected the classification batch: ${batch}"

processed="$(echo "${batch}" | jq '.items | length')"
failed="$(echo "${batch}" | jq '.failures | length')"
log "Submitted batch: ${processed} processed, ${failed} failed, out of ${count} pending."
