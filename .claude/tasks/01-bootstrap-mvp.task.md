# Bootstrap the MVP

Stand up the whole app end to end for the first time. Nothing exists yet except the
agent-orchestration meta-layer (`CLAUDE.md`, `.claude/agents/`, `infra/classify/`).

## Scope

1. **Architect** (run first — everything else depends on its output):
   - `docs/openapi.yaml`, `backend/.../V1__init.sql`, ADR-001 through ADR-006, `docs/architecture/*`
   - Verify `infra/classify/prompt.md` still matches the finalized schema/API (it was drafted
     ahead of the backend existing — reconcile field names if anything drifted)
2. **Backend**: implement the API against the Architect's spec — upload, manual entry, list/detail,
   classification-batch, correction, reprocess, spending summary/trend, categories. Testcontainers
   integration tests.
3. **Frontend**: capture screen (camera + preview/accept), receipts list/detail with correction,
   manual-entry form, monthly dashboard.
4. **DevOps**: `infra/compose.yml`, Nginx config, Dockerfiles, `.env.example`, verify
   `infra/classify/classify-receipts.sh` against the real API, crontab entries.

## Acceptance

- `docker compose up -d --build` brings up nginx + backend + db cleanly on this host.
- A photo taken through the PWA lands as a `PENDING` receipt.
- Running `infra/classify/classify-receipts.sh` by hand against a real pending receipt classifies
  it correctly and the dashboard reflects it.
- `mvn test` (Testcontainers) and `npm run build` both pass.

## Out of scope for this pass

- Real cron installation (script + crontab *text* is deliverable; actually running `crontab -e`
  on the host is a separate, explicit step — confirm with the user before installing it).
- Git init / first commit (repo isn't a git repo yet — ask before initializing).
