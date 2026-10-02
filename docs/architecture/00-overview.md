# 00 — Architecture Overview

> **Audience:** All agents, and anyone landing in `docs/architecture/` cold. Start here.

This directory is the living, authoritative record of the system's design — not a snapshot from
whenever a feature shipped. Diagrams and decisions get folded in and kept current (see
`architect.md` § Documentation conventions); nothing here is meant to go stale.

## What the system is

A personal receipt-tracking PWA. A photo of a receipt — or a pasted/picked screenshot of a digital
receipt or order summary — uploads immediately; once a day the Claude Code CLI itself reads every
pending image, itemizes it, and assigns each line item to one of a fixed 11-value spending
category enum (see `CLAUDE.md § Categories`). Runs single-user on a home Raspberry Pi behind
Tailscale — no auth, no per-token billing.

## Map of this directory

| Doc | Covers | Status |
|---|---|---|
| [01 — System Context](01-system-context.md) | What the running system looks like, how its pieces connect, what a user can do | Implemented |
| [02 — Domain Model and Schema](02-domain-model-and-schema.md) | DB schema, entities, `classDiagram` for domain/service structure, incl. the `CAMERA`/`SCREENSHOT` image-upload paths | Implemented (`SCREENSHOT` source: **contract done, implementation pending** — Flyway `V3`/`V4`) |
| [03 — Receipt Status Lifecycle](03-receipt-lifecycle.md) | `receipt_status_enum` state machine (`CAMERA`/`SCREENSHOT`/`MANUAL` sources) | Implemented (`SCREENSHOT`: contract done, implementation pending) |
| [04 — Capture → Upload → Classify → Correct Flow](04-classification-flow.md) | End-to-end sequence, incl. the PWA capture entry points (camera / paste / pick), the daily batch job and quota-retry path | Implemented (screenshot entry points + `source=`/extension handling: contract done, implementation pending) |
| [05 — API Contract Summary](05-api-contract.md) | Navigable summary of `docs/openapi.yaml` (the spec is the source of truth) | Implemented (`POST /receipts/screenshot`: contract done, implementation pending) |
| [06 — PKO BP Bank-Transaction Integration](06-bank-integration.md) | Second receipt source (`BANK_IMPORT`), superset of 02/03/04 for that source | **Design-only** — not yet implemented; see `docs/adr/ADR-007-pko-bp-psd2-integration.md` |

Other design records, outside this directory:
- `docs/openapi.yaml` — the API contract (source of truth; `05` is a summary of it)
- `docs/adr/` — Architecture Decision Records; a scratchpad for reasoning at decision time,
  consumed into the docs above once settled (see `architect.md`)

## How this stays coherent as features land

A feature that extends an **existing** concern updates that concern's doc in place — e.g. new
entities go into `02`'s `classDiagram`, a new status value goes into `03`'s state diagram — rather
than redrawing a competing copy elsewhere. A feature that is still **design-only** (not yet built)
instead gets its own numbered doc plus a short forward-pointer note in whichever existing docs it
will eventually touch, exactly as `06` does today in `03` and `04`. Once that work is actually
implemented, its diagrams fold into the existing docs and the forward-pointer notes come out.
