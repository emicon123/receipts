# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

Whenever doing any task leading to code changes:
- spawn an architect agent, update /docs/architecture
- if the task is big, create task.md file(s) in .claude/tasks
- spawn backend/frontend/devops agent(s) to handle the task (+ architecture context)

You can see more in ## Agent orchestration and ## Task workflow below.

## What this is

A personal receipt-tracking app. You take a photo of a shopping receipt with your phone
(a mobile web app, installed to the home screen), the photo uploads immediately, and once a
day the **Claude Code CLI itself** — not an embedded Anthropic SDK call — reads every new
receipt, itemizes it, assigns each product to one of a fixed set of spending categories, and
computes totals. Purpose: know exactly how much you spend per month in each category — not
just "how much did I spend," but "how much on healthy food vs junk food vs alcohol vs the kid
vs luxuries," etc.

This project was bootstrapped by copying the agent-orchestration pattern from the sibling
`investing-app` project (same author, same home-server setup) and adapting it to this app's
much smaller, single-feature domain.

## Categories (fixed — this is the canonical source of truth, do not invent new ones)

The user has given a final, closed list of 11 categories. Every agent (architect, backend,
frontend) must use these exact enum values and rules — this table is the single source of
truth; do not duplicate/redefine it elsewhere, reference it.

| Enum value | Polish label | Gloss | Classification rule |
|---|---|---|---|
| `ALKO` | Alko | Alcohol | Any alcoholic beverage. |
| `JEDZENIE_KONIECZNE` | Jedzenie konieczne | Healthy food | Nutritionally healthy items (vegetables, fruit, lean meat, dairy, eggs, whole grains). Judged by **nutritional quality**, not by whether it's a "staple." |
| `JEDZENIE_SREDNIE` | Jedzenie średnie | Neutral food | Normal groceries that are neither clearly healthy nor clearly unhealthy (pasta, sauces, bread, frozen dinners, cheese, etc.). |
| `JEDZENIE_PIERDOLOWATE` | Jedzenie pierdołowate | Unhealthy food | Chips, candy, soda, sweets, fried snacks, fast food — nutritionally poor by design. |
| `RZECZY_PALIWO_INNE_ROZNE` | Rzeczy/paliwo/inne/różne | General things, fuel, misc | Car gasoline (the **only** fuel type tracked — propane/heating/other fuel-like purchases fall in this same bucket, not split out), plus any other non-luxury, non-categorized purchase — everyday "stuff." |
| `RZECZY_LUKSUSOWE` | Rzeczy luksusowe | Luxury items | Non-essential purchases by **purpose**, regardless of price: hobby items, gadgets, indulgences — things you could live without. |
| `MYCIE_CHEMIA` | Mycie/chemia | Cleaning & chemicals | Cleaning products, detergents, household chemicals. |
| `ROZRYWKA_RESTAURACJE` | Rozrywka/restauracje | Entertainment & dining out | Restaurants, cafes, cinema, other paid entertainment. |
| `RACHUNKI` | Rachunki | Bills | Utility/subscription bills. These usually won't come from a shopping-receipt photo — see manual entry note below. |
| `BOBINEK` | Bobinek | Kid's stuff | Items for the user's child: diapers, formula, baby food, kids' clothing/toys. |
| `SUPLE` | Suple | Supplements | Vitamins, supplements, protein powder, etc. |

Notes for whoever builds the Claude classification prompt (backend) or category pickers (frontend):
- A single receipt almost always spans **multiple** categories — categorize per line item, not per receipt.
- Food-tier split (`JEDZENIE_*`) is a **health/nutrition judgment**, not a "how basic is this item" judgment.
- Luxury vs. general/misc (`RZECZY_LUKSUSOWE` vs `RZECZY_PALIWO_INNE_ROZNE`) is a **purpose** judgment (non-essential/hobby/indulgence vs. everyday necessity), not a price threshold.
- `RACHUNKI` implies the app needs a **manual entry path** (no photo) alongside camera capture — see the API's `/api/receipts/manual` endpoint in the architect's OpenAPI spec.

## Hard constraints (these shape every decision)

- **Runs on the same Raspberry Pi home server as `investing-app`**, via Docker, reachable from
  anywhere over the existing Tailscale VPN. Reuse that infra pattern (see DevOps agent).
- **Cost-free, same as `investing-app`** — there is no Anthropic API key or per-token billing
  anywhere in this app. Classification rides on the user's existing Claude Pro/Max subscription
  via the Claude Code CLI (see below), so it costs nothing beyond what's already paid for. Every
  tool/library/service must be free/open-source, and the design should still keep usage cheap
  (one batched invocation daily, don't reclassify unnecessarily) since Claude Code's usage limit
  is a shared, finite resource with the user's own interactive sessions.
- **Client is a mobile-first PWA** — no native app, no app store. Camera access via the browser.
- **Single user, no auth** — Tailscale-only access, same as `investing-app`.
- **Analysis is a once-daily batch job, not real-time.** Uploading a receipt just stores the
  photo (status `PENDING`); a scheduled job classifies everything pending. This controls
  Anthropic usage and keeps the upload path fast and simple.
- **The classifier is the Claude Code CLI itself**, invoked headlessly (`claude -p ...`) — see
  `## Daily classification job` below. It is already installed and authenticated on this
  Raspberry Pi (this very session runs on it), so there is nothing to provision beyond a cron
  entry and the wrapper script + prompt template it runs.

## Agent orchestration

The main Claude Code session is the orchestrator. It reads this file, then spawns specialist
agents (subagents cannot spawn further subagents — one level only). Each agent starts with
fresh context and receives this file plus a task summary.

Which agent owns which work:

| Work | Agent |
|---|---|
| System design, OpenAPI contract, DB schema, ADRs, the content of `infra/classify/prompt.md` | **Architect** |
| Java/Spring Boot API, JPA, Flyway migrations, MapStruct, the endpoints the classification job calls, JUnit + Testcontainers | **Backend** |
| React PWA — camera capture, receipt list/detail, monthly category dashboard | **Frontend** |
| Docker, docker-compose, Nginx, RPi deployment, `.env`, `infra/classify/classify-receipts.sh` + its cron schedule | **DevOps** |

Unlike `investing-app`, there is no separate "mobile" agent (Frontend covers the phone camera
capture directly as a PWA) and no "frontend-web" vs. "mobile" split — there's only one client.

**Sequencing gate:** the Architect must produce the OpenAPI spec and DB schema *before* Backend
or Frontend start — those are the shared contracts every other agent consumes.

## Daily classification job

This mirrors a pattern already proven in the sibling `investing-app` (its nightly asset-news
job — plain host crontab, static prompt template, wrapper script does all backend I/O, no
Anthropic API key). Same shape here, at `infra/classify/`:

1. **`infra/classify/classify-receipts.sh`** (host cron, not a container — see DevOps) first
   calls `GET /api/receipts/pending`. **If it's empty, the script exits immediately** without
   invoking `claude` at all — no point spending a CLI invocation on an empty queue.
2. If there are pending receipts, the script downloads each one's image to a temp file, appends
   an `id → local path` manifest to the static template at `infra/classify/prompt.md`, and runs
   **one single** `claude -p "<prompt>" --output-format json --allowedTools "Read"` invocation
   covering the *whole* batch — not one invocation per receipt. `--allowedTools "Read"` is
   deliberately narrow: Claude only reads the downloaded images and emits JSON; it never calls
   the backend itself — the script does that, both before (fetch pending, download images) and
   after (submit the result).
3. Claude's reply (parsed from the JSON output's `.result` field) is a single object:
   `{ items: [...], failures: [...] }`, one entry per receipt id it was given. The script POSTs
   this as-is to `POST /api/receipts/classification-batch`.
4. **Retry logic for usage-limit exhaustion.** If the `claude` invocation fails, or returns
   `is_error: true`, the script logs it and exits **without submitting anything** — every
   receipt in that run simply stays `PENDING` (nothing marked it otherwise), so the next
   scheduled slot picks the whole batch back up automatically. No special-cased "is this
   specifically a quota error" detection is needed — *any* failure this run degrades to the same
   safe no-op-and-retry-later behavior.
5. **`FAILED`** (set via the `failures` array in the batch submission) is reserved for genuine
   content problems Claude itself reports — a blurry/unreadable photo, a receipt it can't parse
   — never for a script/CLI-level failure, which instead just leaves the receipt `PENDING`.

**Deliberate divergence from investing-app's news job:** that job runs once nightly with no
retry — a missed night is an accepted, low-stakes gap ("tomorrow's news is still there"). Losing
a day on a receipt is higher-stakes for this app's whole purpose, so — per explicit request —
DevOps schedules a primary run at **06:00** plus a few extra same-day slots purely as a safety
net; each extra slot is a cheap no-op unless the primary run actually failed.

**No service/M2M auth token** on these endpoints, unlike investing-app's `X-Service-Token` on its
news-ingest path: that token exists there to distinguish the cron path from the app's normal
Google-JWT bearer auth. This app has no auth scheme at all (Tailscale-perimeter only), so there's
nothing to distinguish it from — adding a token here would be complexity with no matching need.

Because the job talks to the backend over its normal REST API (not the database directly), the
same validation/business rules (category enum, totals) apply whether a receipt was uploaded via
camera or entered manually.

## Quality gates (no agent skips these)

- All DB schema changes go through **Flyway** versioned SQL files. No manual DDL, no `ddl-auto: update`.
- Backend integration tests use **Testcontainers** against real PostgreSQL — do not mock the database.
- Keep JPA entities out of the API layer — map to DTOs (MapStruct).
- Secrets (DB credentials) live in `.env` / Spring profiles, never in code. There is no
  `ANTHROPIC_API_KEY` to manage — the classifier runs as the already-authenticated Claude Code
  CLI, not an API client.
- The classification-batch endpoint must be **idempotent/retry-safe**: submitting the same
  batch twice (or reprocessing a receipt) replaces its line items rather than duplicating them,
  and never touches a line item the user has already hand-corrected.
- A receipt upload must never be lost. Distinguish failure causes precisely:
  - **Usage-limit/quota exhaustion** → leave `PENDING`, retry on the next scheduled slot.
  - **Genuine content failure** (unreadable photo, unparseable receipt) → mark `FAILED`, surface
    for manual review/manual entry.
  - Never crash the whole batch over one bad receipt — continue processing the rest of the queue.
- Claude must never invent a category outside the fixed 11 — validate its output against the
  enum and flag (don't silently coerce) any mismatch for manual review.

## Task workflow

Work items live in `.claude/tasks/` as `<task-name>.task.md` files. The next item to tackle is
the first file in that directory. When starting a session:

1. List `.claude/tasks/` and read the first task file.
2. Execute it — spawn the appropriate specialist agent(s) per the orchestration table above.
3. On completion, delete the task file (or move it to `.claude/tasks/done/` if history is useful).

Add new tasks by dropping a `<name>.task.md` file into `.claude/tasks/`.

If the task included frontend or backend changes, test and redeploy the app (Docker).

## Git

This directory is not yet a git repository. Once it is: use **Conventional Commits**
(`feat:`, `fix:`, `chore:`, etc.) and feature branches, same convention as `investing-app`.

## Project structure

Monorepo, much flatter than `investing-app` since there's a single client and no shared-ts
package (nothing to share across clients):

```
receipts/
├── backend/    # Spring Boot (Maven)
├── frontend/   # React PWA (Vite)
├── infra/
│   ├── classify/   # prompt.md + classify-receipts.sh — the daily CLI job (host cron, not a container)
│   ├── nginx/
│   └── compose.yml, .env template
└── docs/       # openapi.yaml, adr/, architecture/
```

## Rebuilding and restarting the app

(Once `infra/compose.yml` exists — see the DevOps agent for the full setup.)

```bash
cd infra
docker compose build backend && docker compose up -d backend   # after Java changes
docker compose build nginx && docker compose up -d nginx       # after frontend changes
docker compose up -d --build                                   # restart everything
docker logs -f receipts-backend-1                               # tail backend logs
```
