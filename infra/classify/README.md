# Daily receipt classification — host cron job

See CLAUDE.md § Daily classification job, `docs/adr/ADR-002-claude-code-cli-classification-engine.md`,
and `docs/architecture/04-classification-flow.md` for the full rationale. This mirrors the
sibling `investing-app`'s `infra/news/news-research.sh` pattern exactly: a plain host crontab
entry, a static prompt template, an already-authenticated `claude` CLI call, and a wrapper
script that does every bit of backend HTTP I/O itself — Claude never touches the network.

## What this is

`classify-receipts.sh` runs on the Raspberry Pi **host**, not in Docker (the `claude` CLI lives
on the host — confirmed at `/home/wojtekrpi/.local/bin/claude`, `2.1.251`, already logged in).
Each run:

1. `GET {API_BASE}/receipts/pending`. If empty, logs and exits immediately — no `claude`
   invocation spent on an empty queue.
2. Downloads every pending receipt's image to a temp dir, appends an `id → path` manifest to
   `prompt.md`, and runs **one** `claude -p "<prompt>" --output-format json --allowedTools "Read"`
   call for the whole batch. A pending entry is `{id, source}` (`CAMERA`, or `IMAGE_IMPORT` for an
   image imported from the phone gallery/clipboard — ADR-014; a missing `source` is treated as
   `CAMERA`). Each image is saved under its **real** type, taken from the `Content-Type` of
   `GET /receipts/{id}/image` (parameters such as `;charset=…` stripped, case-insensitive):
   `image/jpeg` → `receipt-<id>.jpg`, `image/png` → `.png`, `image/webp` → `.webp` — the `Read`
   tool decodes by extension, so a PNG screenshot saved as `.jpg` would not be read correctly.
   Any other `Content-Type`, or a failed download, aborts the run before `claude` is called
   (logged as an `ERROR:`; nothing is submitted, so every receipt stays `PENDING`). The manifest
   line per image is exactly `- id=<id> path=<absolute temp path> source=<CAMERA|IMAGE_IMPORT>`,
   and each download logs `Downloaded receipt <id> (<source>, <ext>).`
3. On any failure (non-zero exit, or `is_error: true` in the JSON wrapper) — logs it and exits
   `0` **without** submitting anything. Every receipt in that run simply stays `PENDING` and is
   picked up automatically by the next day's 06:00 run — the same accepted, low-stakes gap
   investing-app's news job tolerates (see the crontab below).
4. On success, POSTs the parsed `{items, failures}` object straight to
   `POST {API_BASE}/receipts/classification-batch` and logs a one-line summary.

`API_BASE` defaults to `http://localhost:${BACKEND_PORT:-8080}/api` — **this talks to the
backend container directly** (its port is published to `127.0.0.1` only, see
`infra/compose.yml`), not through nginx. That's different from investing-app's news job, which
goes through nginx on port 80; it's necessary here because investing-app already occupies host
port 80 on this Pi, and there's no reason to add nginx's proxy hop for a job that only ever runs
on the same host as the backend anyway.

Files:

- `classify-receipts.sh` — the script described above (Architect-authored, DevOps-verified
  against `docs/openapi.yaml`; owned/maintained by DevOps going forward).
- `prompt.md` — static prompt template (Architect-owned content).
- `classify-receipts.log` — created on first run; gitignored (`infra/classify/*.log`).

## Prerequisites

- The stack is up: `docker compose -f infra/compose.yml --env-file .env up -d` from the repo
  root, so `GET http://localhost:${BACKEND_PORT}/api/receipts/pending` actually responds.
- The `claude` CLI is authenticated on this host (already true — see above). The script never
  attempts a login flow; if the CLI isn't authorized, `claude -p` simply fails and the run is a
  safe no-op (every pending receipt stays `PENDING` for the next slot).
- `curl` and `jq` on `PATH` (both used for the HTTP calls and JSON parsing).
  **Verified missing on this host as of this pass** — `command -v jq` finds nothing, and
  because the script runs under `set -euo pipefail`, the very first `jq` call kills it
  immediately (`jq: command not found`, exit 127) *before* the script's own `log()`/`fail()`
  helpers ever run — so a cron-triggered failure from this cause won't even appear in
  `classify-receipts.log`, only in whatever cron does with stdout/stderr (nothing, by default,
  unless `MAILTO` is set). **Install it before relying on this job at all:**
  ```bash
  sudo apt-get update && sudo apt-get install -y jq
  ```
- If `.env`'s `BACKEND_PORT` is ever changed from the default `8080`, either export
  `BACKEND_PORT` in cron's environment (see the crontab block below) or edit the crontab entries
  to set it inline — the script does **not** source `.env`, unlike investing-app's news job
  sourcing `infra/.env` for its ingest token (this app has no service token to read — ADR-004).

## Testing manually

```bash
/home/wojtekrpi/Claude-Code/receipts/infra/classify/classify-receipts.sh
tail -f /home/wojtekrpi/Claude-Code/receipts/infra/classify/classify-receipts.log
```

With no pending receipts, expect a single "No pending receipts — nothing to do." log line and
exit `0` with no `claude` invocation. With at least one `PENDING` receipt (upload one through the
PWA first), expect one `Downloaded receipt <id> (<source>, <ext>).` line per image, one
`claude -p` call, and either a submitted-batch summary line or a "leaving all N receipt(s)
PENDING" line on failure.

To check the image-import path (ADR-014) end to end — an imported PNG must be downloaded as
`.png` and tagged `source=IMAGE_IMPORT` — upload a PNG the way the PWA's gallery/paste action
does (the server reads the part's content type, so set it explicitly), then run the script:

```bash
curl -sf -F 'image=@/path/to/screenshot.png;type=image/png' \
  http://localhost:8080/api/receipts/image-import          # 201, source IMAGE_IMPORT, status PENDING
curl -s -o /dev/null -D - http://localhost:8080/api/receipts/<id>/image | grep -i '^content-type'
                                                           # expect: content-type: image/png

/home/wojtekrpi/Claude-Code/receipts/infra/classify/classify-receipts.sh
grep 'Downloaded receipt' /home/wojtekrpi/Claude-Code/receipts/infra/classify/classify-receipts.log | tail
                                                           # expect: Downloaded receipt <id> (IMAGE_IMPORT, png).
```

The temp dir is removed when the script exits, so to see the actual file name and manifest line
(`path=…/receipt-<id>.png source=IMAGE_IMPORT`) run it once under `bash -x` and filter the trace
(`… 2>&1 | grep -E '^\+ img='` shows the `.png` path). If the `Content-Type` is anything other
than JPEG/PNG/WebP, the run stops with `ERROR: unsupported image Content-Type …` and nothing is
submitted. Note that a run with a pending receipt makes a real `claude` call and spends part of
the Claude usage limit; to exercise the script without that, point `CLAUDE_BIN` (overridable,
like `API_BASE`) at a stub that prints a `{"is_error":false,"result":"{…}"}` envelope.

## Installing the crontab entries

**Not installed by this pass** — deliberately out of scope (see `.claude/tasks/01-bootstrap-mvp.task.md`
§ Out of scope). Install it yourself once a manual run above has succeeded end to end:

```bash
crontab -e
```

Add this single line — one run per day at 06:00, exactly like investing-app's nightly news job
(its entry is `0 23 * * *`), with no same-day retry slots (CLAUDE.md § Daily classification job;
the user asked for the single 06:00 run, so earlier iterations' extra 10:00/14:00/18:00/22:00
safety-net slots were dropped). If a run fails, every receipt in it simply stays `PENDING` and the
next day's 06:00 run picks it up:

```cron
0 6 * * * /home/wojtekrpi/Claude-Code/receipts/infra/classify/classify-receipts.sh
```

No `>> ... 2>&1` redirect on this line, unlike investing-app's nightly entry — this script
already logs every step internally via its own `log()`/`fail()` helpers to
`classify-receipts.log`; adding a crontab-level redirect on top would just duplicate every line
into the same file. (investing-app's entry does redirect because that's its *only* place
anything gets logged for a shell-level failure before its own logging kicks in; either
convention is fine, just don't do both.)

This line runs in the cron daemon's system-configured timezone (same caveat as investing-app's
news job) — confirm with `date` on the host that it already reports the intended local time
before relying on "06:00" meaning what you expect.

## Known limitations / gotchas

- If the Pi is rebooted or powered off at 06:00 on a given day, that day's classification is
  simply skipped — no backfill. The next day's 06:00 run picks up everything still `PENDING`,
  including anything from the missed day.
- `claude -p` CLI flags or `--allowedTools` availability can change between Claude Code
  releases; this script is isolated from the backend so fixing it never requires a backend
  redeploy.
- Unlike investing-app's `X-Service-Token` header on its news-ingest path, no endpoint here
  requires any auth header — ADR-004 (no auth scheme at all, Tailscale-perimeter only).
