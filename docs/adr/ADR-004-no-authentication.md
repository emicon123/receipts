# ADR-004: No Authentication (Single-User, Tailscale-Only Access)

**Date:** 2026-08-31
**Status:** Accepted

**Context:** investing-app started with "Tailscale VPN is the security boundary, no
application-level auth" and later added Google OAuth + backend JWTs once it was exposed via
Tailscale **Funnel** to the public internet (see that app's ADR-002/031/032 and
`01-system-context.md`). That change was driven specifically by moving off VPN-only access.

This app has no such driver: it is genuinely single-user (one person's personal receipts, not a
two-account household split like investing-app), and CLAUDE.md's hard constraints state
Tailscale VPN access explicitly, not Funnel/public-internet exposure. There is no second user to
allowlist and no plan to expose this app outside the tailnet.

**Decision:** No authentication scheme anywhere in the API (`security: []` in
`docs/openapi.yaml`, applied globally). No user table, no session/JWT machinery, no
`X-Service-Token` for the cron job either — investing-app needed that token specifically to
distinguish its host-cron news-ingest path from its normal Google-JWT bearer auth on every other
endpoint; this app has no auth scheme at all to distinguish the cron path *from*, so a token here
would be complexity with nothing to protect against that Tailscale itself doesn't already gate.

**Consequences:**
- Every endpoint, including the ones `classify-receipts.sh` calls, is reachable by anything on
  the tailnet with no further check. Acceptable because the tailnet **is** the trust boundary —
  the same assumption CLAUDE.md states outright.
- **If this app is ever exposed beyond the tailnet** (Funnel, port-forward, a second household
  member, etc.), this ADR must be revisited first — do not ship that change without adding an
  auth layer, following investing-app's own precedent for exactly this transition.
- No user-identity concept exists anywhere in the domain model — `receipts`/`receipt_line_items`
  have no `user_id` column. Adding multi-user support later would need a schema change, not just
  an auth-layer bolt-on.
