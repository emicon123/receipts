---
name: software-design-excellence
description: Apply software design & architecture excellence — Clean Code, SOLID/DRY/YAGNI, Law of Demeter, GoF & PoSA patterns, architectural principles/styles/patterns, enterprise patterns — to every architecture and implementation task. Use when Architect designs contracts/UML/diagrams, when Backend (Java/Spring) or Frontend (React) implements/refactors features, or during code review and tech-debt work to ensure justified, KISS/YAGNI-driven quality.
---

# Software Design Excellence

## Overview

Ensure every design and implementation applies the full `roadmap.sh/software-design-architecture` body of knowledge with YAGNI/KISS as tie-breaker. Architect evaluates patterns → commits UML → delegates; Developers read UML → implement with Clean Code → verify via checklists.

## Workflow Decision Tree

```
Task arrives
  ├─ Architect task (new feature, contract, schema, refactor)?
  │   └─ Run §1 Evaluate → §2 Diagram → then delegate
  └─ Developer task (implement, fix, refactor, review)?
      └─ Read diagrams + contracts → §3 Implement → §4 Verify
```

## §1 Evaluate — Pattern & Principle Selection (Architect + Developer)

Before any diagram or code, evaluate and record a short table:

| Candidate | Verdict | Why |
|---|---|---|
| Factory Method | rejected | YAGNI — single creation path |
| Strategy | adopted | Rebalancing calc varies by account type |

Rules:
- Consider all 23 GoF patterns + PoSA, plus SOLID/DRY/YAGNI/Law of Demeter/Tell-don’t-ask/Hollywood/Composition-over-Inheritance.
- YAGNI/KISS wins ties — do not introduce speculative abstractions.
- Record verdict in task doc, ADR, or PR description.

Load details:
- Principles → [design-principles.md](references/design-principles.md)
- Patterns → [design-patterns.md](references/design-patterns.md)
- Architecture & Enterprise → [architecture.md](references/architecture.md)

## §2 Diagram — UML-First (Architect, Required Before Delegation)

Create Mermaid diagrams in authoritative doc (`docs/architecture/diagrams/<task>.md` or inline in `docs/architecture/*.md`). If the `mermaid` skill is available (`~/.claude/skills/mermaid/`), invoke it for every diagram — select type via its Diagram Type Reference and read the matching syntax doc in `mermaid/references/` before emitting code; follow its Output Specification and apply Configuration & Themes (theming/directives/layouts) where it improves clarity:

- `classDiagram` — entities, DTOs, services, mappers, repos, clients, relationships + pattern annotations → `mermaid/references/classDiagram.md`
- `sequenceDiagram` / `flowchart` — request/response, multi-step flows, scheduled jobs → `mermaid/references/sequenceDiagram.md` / `mermaid/references/flowchart.md`
- `stateDiagram-v2` / use-case `flowchart` — lifecycles, user journeys where applicable → `mermaid/references/stateDiagram.md`
- `erDiagram` / `C4` / `architecture` — data model / system context where applicable → `mermaid/references/entityRelationshipDiagram.md` / `mermaid/references/c4.md` / `mermaid/references/architecture.md`

Each diagram needs legend/notes stating which patterns/principles were applied and why.
No delegation without committed diagrams; for trivial tasks write one-line justification instead.

Optional paradigms/OOP context → [paradigms-and-oop.md](references/paradigms-and-oop.md) · Mermaid theming/directives/layouts → `mermaid/references/config-theming.md`, `config-directives.md`, `config-layouts.md`

## §3 Implement — Clean Code (Developer)

Apply while coding; do not re-introduce rejected patterns.

- Keep methods/classes/files small, names meaningful, tests fast/independent — see [clean-code.md](references/clean-code.md)
- Respect boundaries, coupling/cohesion, component principles — see [architecture.md](references/architecture.md)
- Use enterprise patterns (Repository, DTO, Mapper, Transaction Script, etc.) only when justified

## §4 Verify — Quality Gates

Before marking done, run the relevant checklist in [quality-gates.md](references/quality-gates.md):

- Architect gate: pattern evaluation committed, UML with legends, contracts updated, delegation lists exact diagram sections
- Developer gate: diagrams read, clean-code checks pass, SOLID/DRY/YAGNI respected, no speculative pattern, tests cover behavior
- Review gate: diagram↔code↔doc consistency, coupling/boundary violations flagged

## Reference Map

| Domain | File | Load when |
|---|---|---|
| Clean Code (12 rules) | [clean-code.md](references/clean-code.md) | Writing or reviewing any code |
| Paradigms & OOP | [paradigms-and-oop.md](references/paradigms-and-oop.md) | Choosing style, modeling domain |
| Design Principles (SOLID, Law of Demeter…) | [design-principles.md](references/design-principles.md) | Evaluating principles |
| Design Patterns (GoF + PoSA) | [design-patterns.md](references/design-patterns.md) | Selecting patterns |
| Architecture (Principles/Styles/Patterns + Enterprise) | [architecture.md](references/architecture.md) | System-level decisions |
| Checklists | [quality-gates.md](references/quality-gates.md) | Before finishing any task |
| Mermaid (if available) | `~/.claude/skills/mermaid/SKILL.md` + `mermaid/references/*.md` | Generating any Mermaid diagram — read matching syntax doc + Output Spec/Theming |

Source roadmap: https://roadmap.sh/software-design-architecture and https://github.com/kamranahmedse/developer-roadmap
