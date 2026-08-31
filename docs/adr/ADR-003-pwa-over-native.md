# ADR-003: PWA Over Native App for Camera Capture

**Date:** 2026-08-31
**Status:** Accepted

**Context:** The core interaction is: take a photo of a receipt with a phone, have it upload
immediately. This needs camera access from a phone. Options considered: a native/Expo mobile app
(the path investing-app eventually took for its own mobile client, per its
`11-mobile-architecture.md`), or a mobile-first web app installed to the home screen (PWA).

investing-app's mobile app exists because that app has real cross-device session/auth state and
enough screens to justify a dedicated client. This app has none of that pressure: one feature
(capture → list → correct → dashboard), single user, no auth, no app-store distribution need.

**Decision:** A single React 19 PWA (Vite), installable to the home screen, using the browser's
`<input type="file" accept="image/*" capture="environment">` / `getUserMedia` camera access. No
native app, no Expo/React Native client, no app-store submission.

**Consequences:**
- **No app-store review process, no separate build/signing pipeline** — the entire client ships
  as static files served by the same nginx container as everything else in `infra/compose.yml`.
- **Camera capture quality is bounded by what the mobile browser exposes** — acceptable here
  since the photo only needs to be legible enough for Claude to read line items off it, not
  professional-grade; a native camera API (manual focus/exposure control, RAW capture) buys
  nothing this task needs.
- **Offline behavior is minimal-effort at best** (a service worker can cache the app shell, but
  an upload fundamentally needs the network) — acceptable since the phone is expected to have
  connectivity (home Wi-Fi or mobile data) at the moment of use; not designing an offline
  upload queue for this pass.
- **One client, one contract.** Unlike investing-app (which had to design its OpenAPI/DTOs to
  serve both a web and a mobile client with different auth-storage mechanics), this app's API
  only ever has one consumer type — simpler contract, no client-differentiation concerns.
- If a native app is ever wanted later (e.g. for push notifications on a `FAILED` receipt, or
  offline capture), revisit alongside ADR-001 (monorepo layout assumes one client) — not
  designed for here per YAGNI.
