# ADR-002: Claude Code CLI (Headless, Subscription Auth) as the Classification Engine

**Date:** 2026-08-31
**Status:** Accepted

**Context:** Every receipt needs its line items read off a photo and categorized into one of 11
fixed spending categories — a vision + reasoning task. Two ways to get a model to do this:

1. **Embedded Anthropic API/SDK integration** — the backend holds an `ANTHROPIC_API_KEY`, calls
   the Messages API directly (`anthropic-sdk-java` or a raw HTTP client) with the receipt image
   as a base64/`image` content block, per receipt or per batch.
2. **Headless Claude Code CLI** — a host-level cron job shells out to `claude -p "<prompt>"
   --allowedTools "Read"`, riding the user's existing Claude Pro/Max subscription. No API key,
   no per-token billing, no HTTP client code in the backend at all.

This app has a hard constraint (CLAUDE.md § Hard constraints): **cost-free, same as
investing-app** — no Anthropic API key or per-token billing anywhere. investing-app already
proved this exact pattern works for its nightly news-research job (`infra/news/`): a plain host
crontab, a static prompt template, a wrapper script doing all backend I/O, `claude` itself never
touching the network.

**Decision:** Classification is a headless Claude Code CLI invocation, not an embedded SDK
integration — one batched `claude -p` call per cron run (`infra/classify/classify-receipts.sh`,
DevOps-owned), covering every `PENDING` receipt in that run, never one invocation per receipt.

**Consequences:**
- **Cost:** $0 marginal cost. The trade-off is a shared, finite resource — Claude Code's usage
  limit is shared with the user's own interactive coding sessions on the same subscription. This
  is why the job batches into one invocation per run instead of one per receipt (N receipts would
  cost N invocations' worth of usage against a limit that's not priced per-token to begin with),
  and why it runs once a day plus a few cheap safety-net slots rather than on every upload.
- **Reliability:** no API-level rate-limit/pricing model to design around (explicitly out of
  scope per CLAUDE.md's Gotchas) — instead the design accounts for Claude Code's own
  usage-limit semantics: an exhausted limit makes the `claude` invocation fail or return
  `is_error: true`, and the script's response is simply "submit nothing, retry next scheduled
  slot" (see `docs/architecture/04-classification-flow.md`). No exponential backoff, no
  API-error-code branching — the failure mode collapses to one case.
- **Latency/coupling:** classification is strictly batch, once (or a few times) daily, never
  real-time — a receipt upload is fast and simple (store the file, return `PENDING`) precisely
  because it never waits on a model call. A user photographing a receipt does not see its
  category same-day necessarily; they see it the next scheduled slot after upload.
- **No vision HTTP client to write.** Backend's job is two plain REST endpoints
  (`GET /receipts/pending`, `POST /receipts/classification-batch`) plus a byte-serving endpoint
  (`GET /receipts/{id}/image`) for the script to download from — not an Anthropic SDK dependency,
  not image-encoding-for-API-payload logic, not a `WebClient`/`RestClient` bean for an external
  AI provider.
- **Operational dependency:** the Raspberry Pi host must have an authenticated `claude` CLI
  session with an active Pro/Max subscription. If that subscription lapses or the CLI's auth
  token expires, classification silently stops working (receipts keep accumulating as
  `PENDING`, indistinguishable at the DB level from a quota-exhaustion retry) until someone
  notices and re-authenticates. No monitoring/alerting for this is designed here — acceptable
  for a personal, single-user app; DevOps may want a log-based check later.
