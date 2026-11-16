# Receipts

A self-hosted, single-user receipt tracker that answers "how much do I spend each month, and on
what?" — not just in total, but per category: healthy vs. neutral vs. junk food, alcohol, kid's
stuff, luxuries, bills and more.

Snap a receipt with your phone (an installable PWA) or import a photo or screenshot from the
gallery, the clipboard or a drag-and-drop. Once a day a host cron job runs the **Claude Code CLI**
over every new receipt: it itemises the receipt, assigns each line to one of eleven fixed
categories, checks that the line totals add up to the printed total and posts the result back to
the API. The web app then shows monthly spending per category and subcategory.

The UI is in Polish; code and docs are in English.

## Features

- **Capture:** camera upload from a mobile PWA, or image import (gallery, clipboard, drop) with
  client-side normalisation of the image.
- **AI classification without an SDK:** the daily job (`infra/classify/classify-receipts.sh`)
  drives the Claude Code CLI with a versioned prompt and a strict, validated JSON result contract
  (ADR-002, ADR-008).
- **Line-total self-check:** the classifier re-reads the image when the extracted items do not sum
  to the printed total within ±0.05 zł, which catches missed quantity multipliers (ADR-011).
- **Fixed category model:** eleven categories with subcategories, defined in one place (ADR-005,
  ADR-010).
- **Reports:** monthly totals, stacked subcategory bars and category drill-down; store-name
  suggestions ranked by usage (ADR-009).
- **Image retention policy** for stored photos (ADR-006).

## Tech stack

| Layer | Technology |
| --- | --- |
| Backend | Java 25, Spring Boot 4 (Web, Data JPA, Validation), MapStruct, Lombok |
| Database | PostgreSQL, Flyway migrations |
| Frontend | React 19, TypeScript, Vite, TanStack Query, Tailwind CSS 4, PWA |
| Classification | Claude Code CLI run by host cron, no API keys in the app |
| Infrastructure | Docker Compose on a Raspberry Pi, nginx, reached over Tailscale (served under a `/paragony/` path prefix) |
| Tests | JUnit 5, Spring Boot Testcontainers (real PostgreSQL), Vitest |

## Repository layout

```
backend/         Spring Boot API (pl.receipts), Flyway migrations in src/main/resources/db/migration
frontend/        React PWA
docs/            ADRs (docs/adr) and architecture notes (docs/architecture)
infra/           compose.yml, Dockerfiles, nginx, and the classification cron job (infra/classify)
```

## Running

```bash
cp .env.example .env                       # database credentials, ports, storage path
docker compose -f infra/compose.yml --env-file .env up -d --build
```

The classification job needs the Claude Code CLI installed and logged in on the host; see
[`infra/classify/README.md`](infra/classify/README.md) for the cron setup.

Development:

```bash
(cd backend && ./mvnw test)                # needs Docker for Testcontainers
(cd frontend && npm install && npm test)
```

## Security

There is no application-level authentication by design (ADR-004): the app is single-user and
meant to be reachable only on a private Tailscale network. The backend port is bound to the
host's loopback; the app's nginx is the only entry point. Do not expose it to the public internet
as is.

## How it was built

Developed with Claude Code in an orchestrated workflow (architect agent → backend, frontend and
DevOps agents), described in `CLAUDE.md`.
