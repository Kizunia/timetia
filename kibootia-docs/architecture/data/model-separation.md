# Model Separation

- **Type:** rules
- **Module:** platform
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-10-27

> **Implementation:** planned.

Decisions: [ADR 0004](../../decisions/0004-separate-api-application-and-persistence-models.md) (accepted), [ADR 0005](../../decisions/0005-hand-written-model-mappers.md) (draft).

## Rule (owner decision)

Four kinds of types are kept separate:

| Type | Represents | Must not |
|---|---|---|
| API DTO | The external HTTP contract | Expose entities or internal structure |
| Application model | Data used to coordinate a use case | Depend on a concrete JPA entity where a persistence boundary exists |
| Domain model | Business rules, only where needed | Depend on infrastructure frameworks where designated |
| Persistence entity | Database mapping and persistence concerns | Be returned from an API or leak across features |

The mapping overhead is accepted deliberately in exchange for reduced coupling.

## Conversion (provisional)

Hand-written mapper classes or methods owned by the adapter or layer that needs the conversion. No mapping framework; this follows the exclusion of universal mapping frameworks. MapStruct was considered and not chosen. Every mapper needs a test.

## Examples

No example exists yet. [Milestone 4](../../product/roadmap/milestone-4-persistence-conventions.md) proposes a reference example.

## Related

[Layers and boundaries](../layers-and-boundaries.md).
