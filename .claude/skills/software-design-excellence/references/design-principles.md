# Design Principles (roadmap.sh)

## SOLID — apply to every class

- **S — Single Responsibility** — one reason to change; split `RebalancingService` (calc) from `MarketDataService` (fetch).
- **O — Open/Closed** — open for extension (Strategy for rebalancing variants), closed for modification; add new `PriceProvider` without editing consumers.
- **L — Liskov Substitution** — subtypes substitutable; `IkePosition extends Position` must not break `totalValue()`.
- **I — Interface Segregation** — small, focused interfaces; `PriceReader` vs `PriceWriter` not `GodMarketService`.
- **D — Dependency Inversion** — high-level depends on abstractions; services depend on `FxRateProvider` interface, injected via constructor.

## Other Principles

- **DRY** — extract on third duplication, not first; share via `shared-ts` for cross-client logic (e.g., `deployable = cash - 0.05*portfolio`).
- **YAGNI** — build only what current task needs; no speculative `AbstractFactory` for future exchanges.
- **KISS** — simplest design that satisfies spec; prefer plain service over pattern if no variation expected.
- **Composition over Inheritance** — compose `RebalancingService` with `WeightCalculator` rather than inheriting.
- **Encapsulate What Varies** — isolate volatile parts (Yahoo API, NBP rates) behind `client/` adapters.
- **Program Against Abstractions** — depend on `Repository` interface, not `JpaRepository` directly in services where testability matters.
- **Hollywood Principle** — “don’t call us, we’ll call you” — Spring calls `@Service`/`@Async`, not manual threads.
- **Law of Demeter** — `position.value(rate)` not `position.getMarketData().getPrice().getValue()`; one dot per line ideal.
- **Tell, Don’t Ask** — `portfolio.rebalance(cash)` not `if (portfolio.getStatus()==X) portfolio.setY()`.

## Applying as Tie-Breaker

When two designs both satisfy SOLID, choose the one that is simpler (KISS) and adds less speculative code (YAGNI). Document trade-off in pattern evaluation table.

## Quick Self-Test

- Does each class have one responsibility? Would you describe it without “and”?
- Can you add a new variant without editing existing classes?
- Are dependencies injected as interfaces via constructor?
- Is any chain `a.getB().getC().do()` present?
