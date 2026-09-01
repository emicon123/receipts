# Update tech stack to latest

User request: "Update tech stack to latest" → confirmed scope: **everything to latest**, including
major/breaking version bumps, across backend, frontend, and infra. Researched against live Maven
Central / npm registry / Docker Hub data on 2026-08-31 (do not trust cached knowledge of "latest" —
re-verify at execution time if this task sits for long before being picked up).

## Backend (Spring Boot / Java) — for the **backend** agent

| Dependency | Current | Target | Notes |
|---|---|---|---|
| `spring-boot-starter-parent` (and all `spring-boot-starter-*`, `spring-boot-testcontainers`, `spring-boot-maven-plugin`) | 3.5.16 | **4.1.1** | Major bump (Spring Framework 7). `4.2.0-M1` exists but is a milestone — do not use, 4.1.1 is latest GA. Consult the official Spring Boot 3→4 migration guide; expect config-property renames, possible removed deprecated APIs. Run the full Testcontainers-backed test suite after. |
| `org.flywaydb:flyway-core`, `flyway-database-postgresql` | 11.7.2 | **13.4.0** (verify — if Boot 4.1.1's BOM already manages a specific Flyway version, prefer that unless it's older than 13.4.0, in which case override explicitly) | Two-major jump; check Flyway's changelog for migration-callback API changes. |
| `org.springdoc:springdoc-openapi-starter-webmvc-ui` | 2.8.17 | **3.1.0** | springdoc v3 targets Spring Boot 4 / Spring Framework 7 — must move together with the Boot bump, not independently. |
| `org.mapstruct:mapstruct` | 1.6.3 | **no change** | Already latest stable (`1.7.0.Beta2` is a pre-release, skip it). |
| `java.version` | 25 | **no change** | Already the latest LTS (Adoptium: LTS releases are 8/11/17/21/25; 26 exists but is not LTS). |
| `org.postgresql:postgresql` (JDBC driver, unpinned, managed by parent BOM) | resolves via Boot 3.5.16's BOM | let it follow Boot 4.1.1's BOM | Don't hand-pin unless the BOM-resolved version is stale. |
| Testcontainers (unpinned, via `spring-boot-testcontainers`) | resolves via Boot 3.5.16's BOM | let it follow Boot 4.1.1's BOM | `testcontainers-bom` 2.0.5 exists upstream as a major bump; only hand-pin if Boot's BOM lags badly. |

Docker image (owned by DevOps, but backend agent should sanity-check compatibility):
`maven:3.9.16-eclipse-temurin-25-alpine` builder image is already latest-stable (a `4.0.0-rc-*` exists but is pre-release — skip). `eclipse-temurin:25.0.4_7-jre-alpine` runtime — DevOps should verify the latest `25.*-jre-alpine` patch tag at execution time.

## Frontend (React PWA) — for the **frontend** agent

| Dependency | Current | Target | Notes |
|---|---|---|---|
| `typescript` | 5.9.3 | **7.0.2** | New Go-based native compiler rewrite (not a normal semver major) — verify `tsc -b` / project-references config still works; some legacy `tsconfig.json` options may be unsupported. |
| `vite` | 6.4.3 | **8.2.2** | Two-major jump. |
| `@vitejs/plugin-react` | 4.5.1 | **6.1.1** | Bump together with vite for compat. |
| `recharts` | 2.15.4 | **3.10.1** | Used in `CategoryBreakdownChart.tsx` / `CategoryTrendGrid.tsx` — check recharts v3 migration notes for prop/API renames before assuming charts still render. |
| `zod` | 3.25.76 | **4.5.4** | Used for validation schemas — zod v4 changed error-customization API and some string-format helpers (e.g. `.email()` deprecated in favor of `z.email()`). Audit every schema. |
| `lucide-react` | 0.545.0 | **1.38.0** | First stable major — check icon import names haven't changed for any icons actually used in this codebase. |
| `@tanstack/react-query` | 5.90.3 | 5.102.8 | Safe minor bump. |
| `@radix-ui/react-dialog` | 1.1.15 | 1.1.23 | Safe. |
| `@radix-ui/react-label` | 2.1.7 | 2.1.15 | Safe. |
| `@radix-ui/react-select` | 2.2.6 | 2.3.7 | Safe. |
| `@radix-ui/react-slot` | 1.2.3 | 1.3.3 | Safe. |
| `@radix-ui/react-tabs` | 1.1.13 | 1.1.21 | Safe. |
| `oxlint` | 1.79.0 | 1.80.0 | Safe. |
| `@types/react-dom` | 19.2.4 | 19.2.5 | Safe. |
| `@types/node` | 24.13.3 | **latest 24.x line, NOT 26.x** | Must track whichever Node major DevOps puts in the Docker builder image (see below — recommend staying on the Node 24 LTS line, not 26, which isn't LTS yet). Do not blindly take npm's `latest` dist-tag (26.4.0) for this one. |
| `react`, `react-dom`, `react-router-dom`, `axios`, `date-fns`, `tailwind-merge`, `class-variance-authority`, `clsx`, `tailwindcss`, `@tailwindcss/vite`, `vite-plugin-pwa`, `@types/react` | — | **no change** | Already latest as of research date. |

After bumping: run `npm run build` (`tsc -b && vite build`) and `npm run lint`, fix all breaking-change fallout (especially zod schemas and recharts chart components), and manually sanity-check the dev server renders the dashboard/capture/receipts flows.

## Infra (DevOps agent)

- **`db` image**: `postgres:17.11-alpine` → `postgres:18.6-alpine`. **This is a major PostgreSQL engine bump with an incompatible on-disk data format** — the existing `db-data` named volume (real receipt data, ADR-006 says images/data are retained indefinitely) cannot just have its image tag swapped. Do NOT write a change that silently starts postgres:18 against the old 17 volume. Instead: write a documented, explicit migration runbook/script (e.g. `infra/scripts/migrate-postgres-18.sh` + a short doc) that: (1) `pg_dump`s the running PG17 database to a file, (2) stands up a fresh PG18 container against a **new** volume, (3) restores the dump into it, (4) only after verification, cuts the compose file over to the new volume/image. This migration must be run manually by the user against the real Pi — do not execute it against production yourself.
- **`backend` builder stage** (`infra/docker/backend/Dockerfile`): `maven:3.9.16-eclipse-temurin-25-alpine` unchanged (latest stable). `eclipse-temurin:25.0.4_7-jre-alpine` runtime stage — verify/bump to the latest `25.*-jre-alpine` patch tag.
- **`nginx` builder stage** (`infra/docker/nginx/Dockerfile`): `node:22.23.2-alpine` → **`node:24.20.0-alpine`** (Node 24 is the current LTS line; Node 26 exists on Docker Hub but is not yet LTS-designated as of this research date — do not jump to 26).
- **`nginx` runtime image**: `nginx:1.31.4-alpine` — already latest, no change.
- Rebuild and smoke-test both images locally (`docker compose --env-file ../.env build` for backend+nginx) after the bumps; do not redeploy to the live Pi without the user's go-ahead given the DB migration is involved.

## Architect

- Write a new ADR (`docs/adr/ADR-008-...md`) documenting this tech-stack refresh: what moved to a new major version and why, the Spring Boot 3→4 and Postgres 17→18 migration approach (explicitly note Postgres needs the manual dump/restore runbook above, not an in-place image swap), and any other cross-cutting rationale. Check `docs/architecture/*.md` for any hardcoded version references that need updating.
- This precedes backend/frontend/devops work per the sequencing gate in `CLAUDE.md`.

## Out of scope / do not do

- Do not commit or push any changes — leave the working tree for the user to review.
- Do not redeploy the live app or run the Postgres migration script against the real Pi.
