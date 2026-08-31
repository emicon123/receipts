# Architecture — Principles, Styles, Patterns, Enterprise (roadmap.sh)

## Architectural Principles

- **Component Principles** — REP/CCP/CRP: components cohesive, reusable, closed for change.
- **Policy vs Detail** — business policy (rebalancing math, Belka tax) independent of details (JPA, Yahoo client).
- **Coupling & Cohesion** — high cohesion inside (`rebalancing` package), low coupling between (depend on interfaces). Afferent/efferent coupling visible in `classDiagram`.
- **Boundaries** — explicit boundaries: `backend` ↔ `shared-ts` ↔ `frontend-web`/`mobile` ↔ external APIs (Yahoo/NBP). Cross via DTOs + `client/` adapters only.

## Architectural Styles (select one per subsystem)

- **Layered / Monolithic** — default for investing-app on Pi; simplest, single deployable. Use for backend.
- **Component-Based** — UI composition (`shadcn/ui` + feature components); mobile/web share via `shared-ts`.
- **Client-Server** — SPA/Native ↔ REST API; stateless, bearer-free (Tailscale-only per ADR-004).
- **Event-Driven / Publish-Subscribe / Messaging** — async price refresh (`@Async` + `regression-weight-executor`); avoid full broker (Rabbit/Kafka) on Pi — overkill.
- **Distributed / Peer-to-Peer** — not needed (single-node Pi).
- **Structural** — hexagonal/clean layering inside backend if complexity grows.

## Architectural Patterns

- **Domain-Driven Design** — bounded contexts: `Portfolio` (positions, rebalancing), `Ledger` (monthly entries, deposits, tax), `MarketData` (prices, fx). Share `Money` value object.
- **Model-View-Controller (MVC)** — Spring MVC controllers + React views; controllers thin.
- **Microservices / SOA / Serverless / Microkernel / Blackboard** — do NOT adopt for single-user Pi; would violate KISS/YAGNI. Document rejection explicitly.
- **CQRS** — consider query (`GET /positions` with computed fields) vs command (`POST /deposits`) separation within same service; full CQRS with separate stores is YAGNI.
- **Event Sourcing / Message Queues & Streams** — not needed; Flyway-migrated tables are source of truth.
- **Repository / DTO / Mapper** — enterprise patterns below.

## Enterprise Patterns

Adopt via Spring idioms; map to code locations:

- **DTOs & Mappers** — MapStruct `PositionMapper`; keep entities out of API layer.
- **Repositories** — `JpaRepository<Position, Long>`; isolate queries.
- **Domain Models vs Transaction Script** — rich model for portfolio math; Transaction Script for simple CRUD (e.g., `inflation`).
- **Entities vs Value Objects** — `Position` is Entity (id); `Money(amount,currency)` is Value Object (immutable).
- **ORMs** — JPA/Hibernate; no `ddl-auto`, Flyway is truth.
- **Identity Map** — JPA persistence context; beware within long `@Async` tasks.
- **Usecases / Commands & Queries** — `RebalancingService.calculate(cash)` is usecase; `InvestmentQueryService` for reads.
- **Transaction Script** — simple scripts for ETL (`migration/` Sheets→DB).

## Investing-App Mapping

| Concern | Style/Pattern Chosen | Why / Rejected |
|---|---|---|
| Backend deployment | Layered monolith on Pi | KISS; microservices rejected — single user, free-only, ARM |
| Cross-client logic | `shared-ts` (Zod, Axios, Zustand, utils) | DRY; avoid duplication |
| Market data | Adapter + Facade + Strategy | Isolate Yahoo/NBP volatility |
| Rebalancing calc | Strategy + Pure functions | Vary algorithm without editing callers |

Add similar row per task when documenting decisions.
