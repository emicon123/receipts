# Programming Paradigms & OOP (roadmap.sh)

## Paradigms

- **Structured** — sequence, selection, iteration; avoid `goto`-like jumps.
- **Functional** — pure functions, immutability, composition; use in rebalancing calc, currency conversion, shared-ts utils.
- **Object-Oriented** — model investing domain as objects with behavior, not just data holders.
- Choose paradigm per module: functional for calculations, OOP for domain entities/services.

## OOP Core

- **Abstraction / Encapsulation** — expose intent, hide internals; entities expose behavior (`position.currentValue(rate)` not public fields).
- **Inheritance vs Composition** — prefer composition; use inheritance only for true is-a with Liskov compliance.
- **Polymorphism** — rely on interfaces (`MarketDataClient`) not `if (exchange==XTB)`.
- **Interfaces / Abstract Classes / Concrete Classes** — program against abstractions; depend on `PriceProvider` not `YahooClient`.
- **Scope/Visibility** — smallest visibility possible; `private` by default, `package-private` for testing seams only.
- **Class Variants** — Value Objects (`Money`), Entities (`Position` with identity), DTOs (`PositionResponse`), Aggregates.

## Domain Modeling (investing-app specifics)

- **Model-Driven Design** — ubiquitous language: `Position`, `Deposit`, `Rebalancing`, `DeployableCash`.
- **Domain Language** — use Polish domain terms where spec does (`Faktura`), but code in English; map via docs.
- **Anemic Models** — avoid entities with only getters/setters; move behavior into domain services where entities would become anemic.
- **Layered Architecture** — `Controller → Service → Repository` for backend; `routes → components → hooks → api-client` for frontend. No business logic in controllers/components.
- **Rich vs Anemic** — for complex invariants (e.g., `targetAllocationPct 0..100` + `regressionWeight` required together) enforce in entity/service, not only in DB CHECK.

Checklist: does the model speak the domain, are boundaries explicit, is there a place for each invariant?
