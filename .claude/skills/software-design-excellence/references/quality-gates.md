# Quality Gates — Checklists

Run before marking any task done.

## Architect Gate

- [ ] Pattern evaluation table committed (adopted/rejected + why, YAGNI/KISS noted)
- [ ] UML committed: `classDiagram` + `sequenceDiagram`/`flowchart` + `stateDiagram-v2` where applicable, with legends
- [ ] Contracts updated: `docs/openapi/` + `npm run openapi:bundle` + `npm run openapi:lint` if touched
- [ ] DB changes via Flyway `V{n}__*.sql` with CHECK constraints, `NUMERIC(18,4)`/`(18,8)`/`(7,4)`, `TIMESTAMPTZ`
- [ ] Mermaid over ASCII; `docs/architecture/` describes system as it IS
- [ ] Delegation message lists exact diagram files/sections for Backend/Frontend to read

## Developer Gate (Backend — Java/Spring / Frontend — React)

- [ ] Read assigned UML diagrams + `docs/architecture/` before coding; flagged mismatches
- [ ] Clean Code: small methods, meaningful names, no nulls/booleans, CQS respected
- [ ] SOLID respected: single responsibility, constructor-injected interfaces, no Law-of-Demeter violations (`a.getB().getC()`)
- [ ] No speculative pattern; adopted patterns match diagram legends
- [ ] Boundaries kept: entities not exposed, `MapStruct` mappers, `client/` adapters, `shared-ts` for shared logic
- [ ] Money = `BigDecimal` / backend `NUMERIC`; no `double`/`float`; no `ddl-auto`
- [ ] Tests: unit for math, `@WebMvcTest` for controllers, `@DataJpaTest`+Testcontainers for repos; `@SpringBootTest`+Testcontainers for integration
- [ ] Lint/typecheck clean: backend Checkstyle, frontend `tsc --noEmit`, `npm run build` ok

## Review Gate

- [ ] Diagram ↔ code ↔ docs consistent; coupling/cohesion justified
- [ ] No god classes, anemic models, or primitive obsession left behind
- [ ] New abstractions justified by actual variation (not future speculation)
- [ ] YAGNI/KISS trade-off documented in PR/task doc
