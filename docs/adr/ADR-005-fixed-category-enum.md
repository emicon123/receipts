# ADR-005: Fixed `spend_category_enum` vs. a Dynamic Category Lookup Table

**Date:** 2026-08-31
**Status:** Accepted

**Context:** Every line item needs a spending category. Three shapes were available for "a
closed-ish list of categories":

1. **No structure** — a free-text `category VARCHAR` column, categories emerge from usage.
2. **Fixed DB enum** — `CREATE TYPE spend_category_enum AS ENUM (...)`, 11 hard-coded values.
3. **Dynamic lookup table** — a `categories` table with CRUD endpoints, FK from line items.

The user gave a final, closed list of exactly 11 categories (CLAUDE.md § Categories), each with
specific, non-obvious classification rules (e.g. the `JEDZENIE_*` split is a nutrition judgment,
not a price/staple judgment; `RZECZY_LUKSUSOWE` vs `RZECZY_PALIWO_INNE_ROZNE` is a purpose
judgment). This isn't a list expected to grow via user action — it's a fixed taxonomy the
classification prompt and the frontend both need to agree on byte-for-byte.

**Decision:** A fixed PostgreSQL `spend_category_enum` (option 2). No free-text category column,
no `categories` table, no `POST/PUT/DELETE /categories` endpoints — `GET /categories` is the only
category endpoint, and it returns a static, hard-coded list (Polish label + gloss) that lives in
backend code, not a DB table.

**Why not free-text:** loses the ability to validate Claude's classifier output against a known
set at all — CLAUDE.md's quality gate ("Claude must never invent a category outside the fixed
11 — validate its output against the enum and flag... any mismatch") requires *something*
authoritative to validate against; a free-text column can't reject an invented 12th category.

**Why not a dynamic table:** would need CRUD endpoints, a frontend management screen, and
referential-integrity handling for edits/deletes — all to manage a list the user has already said
is closed. Building CRUD for a list that never actually gets edited is the textbook YAGNI case.

**Consequences:**
- Adding/renaming/removing a category requires a new Flyway migration (`ALTER TYPE ... ADD
  VALUE`, or a full type rebuild for a rename/removal — Postgres enums are append-mostly-friendly
  but awkward to shrink) plus updates to `CLAUDE.md`, `infra/classify/prompt.md`, and the backend
  category list — three places, kept in sync by convention, not by a single source of runtime
  truth. Acceptable given how rarely this list is expected to change (the user called it final).
- The classifier's output is validated server-side at `POST /receipts/classification-batch` time;
  an invalid category value never reaches this enum column at all — the owning receipt is routed
  to `FAILED` instead (see `docs/architecture/03-receipt-lifecycle.md`), so the enum itself stays
  strict (`NOT NULL`, no sentinel "unknown" value) rather than growing an escape hatch.
