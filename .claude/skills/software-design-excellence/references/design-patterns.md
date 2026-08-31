# Design Patterns — GoF + PoSA (roadmap.sh)

Evaluate each; adopt only with justification. YAGNI/KISS is tie-breaker.

## Creational

- **Factory Method** — create `MarketDataClient` by exchange; use when creation logic varies. Alt: plain constructor if single path.
- **Abstract Factory** — families of related objects (e.g., `ExchangeClientFactory` for price+fx clients). Only if multiple families.
- **Builder** — complex object construction (`RebalancingRequest` with many optionals, JPA entities in tests). Lombok `@Builder` for Java.
- **Prototype** — clone expensive objects; rare in this app.
- **Singleton** — avoid; use Spring singleton beans instead. Never manual `static` singletons.

## Structural

- **Adapter** — wrap Yahoo Finance / NBP / Binance to uniform `PriceProvider`; most used in this codebase.
- **Bridge** — decouple abstraction from implementation (e.g., `Chart` abstraction vs `Recharts`/`Victory` impl) — web vs mobile.
- **Composite** — treat leaf/composite uniformly (portfolio → positions, monthly entries → income/expense groups).
- **Decorator** — add behavior without subclassing (caching decorator for `PriceProvider`, logging decorator).
- **Facade** — `RebalancingFacade` composing `PortfolioService + FxService + CalculationService` for controller simplicity.
- **Flyweight** — share immutable `Currency`/`Money` instances; consider for large position sets.
- **Proxy** — lazy loading, access control, caching; Spring AOP proxies for `@Transactional`, `@Async`.

## Behavioral

- **Chain of Responsibility** — validation pipeline: `ValidationHandler[]` for incoming DTOs vs Spring `Validator`.
- **Command** — `Sprzedaj` (sell) action, deposit record; encapsulate request as object for undo/queue.
- **Iterator** — expose `portfolio.positions()` without exposing internal list.
- **Mediator** — centralize `RebalancingMediator` if services become chatty; else avoid.
- **Memento** — snapshot `CashPositions` for history; rarely needed.
- **Observer** — price refresh notifies portfolio; use Spring `ApplicationEvent` or React Query invalidation, not manual observer.
- **State** — purchase-session lifecycle (`DRAFT→CONFIRMED→SETTLED`); `stateDiagram-v2` required if used.
- **Strategy** — interchangeable algorithms: rebalancing strategies (proportional DCA vs regression-weighted), `FxRateStrategy` (NBP vs Yahoo).
- **Template Method** — skeleton in abstract service, steps overriden (e.g., `AbstractMarketClient.fetch()`); consider composition first.
- **Visitor** — traverse position tree for reporting; use only if double-dispatch needed.

## PoSA (Pattern-Oriented Software Architecture)

- **Layers**, **Pipes and Filters**, **Blackboard**, **Broker**, **MVC/MVP/MVVM**, **Microkernel** — see architecture.md for system-level mapping.
- Prefer Layers + MVC for investing-app; introduce microkernel/messaging only when distribution needed (not for single-user Pi).

## Selection Heuristic

1. Is there varying behavior/family/steps? If no → no pattern.
2. If yes, which pattern names the variation most directly? Adopt one.
3. Could a simple `if/else` or composition replace it without duplication? If yes → skip pattern.
4. Record adopted/rejected + why.
