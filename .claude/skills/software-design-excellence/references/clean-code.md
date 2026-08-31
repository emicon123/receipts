# Clean Code — 12 Rules (roadmap.sh)

Apply to every file; prefer these over comments.

1. **Be consistent** — follow existing naming, package, and formatting conventions; mimic neighboring files.
2. **Meaningful names over comments** — `deployableCashPln` not `tmp1`; rename rather than comment.
3. **Indentation and code style** — Prettier / Checkstyle clean; no dead code.
4. **Keep methods/classes/files small** — one responsibility per method/class; extract if >20 lines or >1 reason to change. Controllers delegate to services.
5. **Pure functions where possible** — no hidden side effects; rebalancing math is pure `BigDecimal` logic.
6. **Minimize cyclomatic complexity** — early returns, guard clauses, Strategy over nested `if/else`.
7. **Avoid passing nulls/booleans** — use `Optional`, dedicated methods (`findByTicker` vs `find(ticker, true)`), or Value Objects.
8. **Keep framework code distant** — isolate Spring, JPA, Axios at edges; domain services stay framework-agnostic.
9. **Use correct constructs** — `record` for DTOs, `enum` for bounded sets, `BigDecimal` for money, `TIMESTAMPTZ`.
10. **Tests fast and independent** — unit tests pure; integration tests via Testcontainers, no shared mutable state.
11. **Organize by actor/feature** — package by domain (`rebalancing`, `positions`, `monthly`) not by layer alone where it aids cohesion.
12. **Command-Query Separation (CQS)** — commands mutate and return void/status; queries return data without side effects; `POST /rebalancing/calculate` is pure query.
13. **Keep it simple and refactor often** — YAGNI/KISS; extract only after second duplication (Rule of Three).

Anti-patterns: god class/service, anemic domain model, primitive obsession (use Value Objects), comment that explains *what* instead of *why*.
