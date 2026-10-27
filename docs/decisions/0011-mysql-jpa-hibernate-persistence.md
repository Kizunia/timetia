# 0011 MySQL, Spring Data JPA and Hibernate

- **Type:** adr
- **Module:** platform
- **Status:** accepted
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-10-27

> **Implementation:** observed in part: MySQL in Compose and the MySQL driver in `pom.xml`. Spring Data JPA/Hibernate is not yet a dependency.

## Context

The starter needs a default relational persistence stack. Source: project definition, section 7.1.

## Decision

MySQL is the initial database, with Spring Data JPA for repositories and Hibernate as the JPA implementation.

## Alternatives considered

None recorded.

## Consequences

JPA must be added to the build. Entities are infrastructure details behind [model separation](0004-separate-api-application-and-persistence-models.md). Database-specific behavior is exercised through [Testcontainers MySQL](0017-testcontainers-mysql-for-persistence-tests.md).

## Status

accepted

## Related

[Persistence stack](../architecture/data/persistence-stack.md).
