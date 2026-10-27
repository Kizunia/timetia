# Layers and Boundaries

- **Type:** spec
- **Module:** platform
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-10-27

> **Implementation:** planned. The repository has one package, `com.kibootia.backend`, with only the application class.

Decisions: [ADR 0002](../decisions/0002-modular-monolith.md), [ADR 0003](../decisions/0003-hexagonal-architecture-where-boundaries-matter.md), [ADR 0004](../decisions/0004-separate-api-application-and-persistence-models.md).

## Purpose

State the kinds of code in the backend, what each may depend on, and how features and shared platform packages relate. The concrete package names are not decided ([package structure](../engineering/conventions/package-structure.md)).

## Kinds of code

| Kind | Responsibility | May depend on |
|---|---|---|
| API contracts (HTTP DTOs, controllers) | The external interface of a feature | Application use cases |
| Application use cases | Coordinate one operation, own transaction boundaries | Domain rules, ports |
| Domain rules | Business rules, where a domain model is warranted | Nothing framework-specific where intended |
| Ports | Interfaces for capabilities the application needs | Application and domain types |
| Infrastructure adapters | Implement ports: persistence, external systems | Ports, frameworks, vendor libraries |
| Persistence entities | Database mapping only | Persistence framework |
| Shared platform packages | Narrow cross-cutting capability | See below |

Allowed dependency direction: toward the application and domain; never from application or domain toward infrastructure implementations. This is the rule intended to be tested in [architecture enforcement](architecture-enforcement.md).

## Features

A [feature](../shared/glossary/feature.md) owns its use cases, public contracts and feature-specific infrastructure including persistence. Another feature may use only its public contract. It may not touch its repositories, entities or tables ([data ownership](data/transactions-and-data-ownership.md)). Circular dependencies between features are prohibited.

## Shared platform packages

Allowed for API conventions, security conventions, configuration, observability and auditing support. They stay narrow and carry no business logic. A [platform package](../shared/glossary/platform-package.md) that starts collecting unrelated classes is a defect.

## Model separation

API DTOs, application/domain models and persistence entities are separate types with separate responsibilities. Entities are not returned from APIs, and application logic does not depend on concrete entity classes where a persistence boundary is warranted. Details: [model separation](data/model-separation.md).

## Not every class needs an interface

Ports exist where they protect a meaningful boundary. Adding an interface with a single implementation and no boundary value is not required.

## Known limitations

Package names, the location of ports, and how a feature declares its public contract are undecided ([open questions](../decisions/open-questions.md)).
