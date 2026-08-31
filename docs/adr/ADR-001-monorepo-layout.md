# ADR-001: Monorepo Layout

**Date:** 2026-08-31
**Status:** Accepted

**Context:** The app has exactly one backend, one client, and one small infra job — a much
narrower footprint than the sibling `investing-app` (which has a web client, a mobile client,
and a shared TypeScript package split out for both). That app's monorepo shape (`shared/` npm
workspace, per-client `frontend-web`/`frontend-mobile`, split-source-tree OpenAPI with a bundler
step) exists specifically to serve two clients sharing types/config. This app has none of that —
one client, one small OpenAPI file, nothing to share.

**Decision:** Flat monorepo, no npm workspaces, no shared package, no OpenAPI bundler:

```
receipts/
├── backend/    # Spring Boot (Maven)
├── frontend/   # React PWA (Vite)
├── infra/
│   ├── classify/   # prompt.md + classify-receipts.sh — daily CLI job (host cron)
│   ├── nginx/
│   └── compose.yml, .env template
└── docs/       # openapi.yaml (single file), adr/, architecture/
```

**Consequences:**
- `docs/openapi.yaml` is hand-edited directly — no `docs/openapi/paths/*.yaml` source tree, no
  `redocly bundle` build step. If a second client is ever added, revisit this (and the
  single-client PWA-only decision in ADR-003) together.
- No shared-types package: the two ends of the API contract (Java DTOs, TypeScript types) are
  kept in sync by hand against `docs/openapi.yaml`, same as investing-app did before it grew a
  second client — acceptable at this scale, revisit if it becomes a source of drift.
