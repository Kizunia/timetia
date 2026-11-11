# Kibootia --- Product Ideation Decisions and Planning Brief

-   **Document type:** Temporary product-planning brief for AI-assisted
    ideation
-   **Project:** Kibootia
-   **Purpose:** Transfer the decisions, preferences, constraints, and
    open questions established so far into the repository's temporary
    documentation area so Claude can continue ideation with the owner.
-   **Intended location:**
    `kibootia-docs/temp/kibootia-ideation-decisions.md` (or the
    equivalent `temp/` folder in this branch)
-   **Status:** Working brief --- not an authoritative permanent
    specification and not an implementation claim
-   **Prepared:** 2026-10-28

> **Important:** This document captures the planning conversation so
> far. It does not authorize implementation by itself. Claude must read
> the current permanent documentation and repository, ask the owner
> clarifying questions, document the answers, and only then update the
> permanent docs. Do not treat undecided items or recommendations as
> approved decisions.

------------------------------------------------------------------------

## 1. Instructions for Claude: how to use this brief

This document exists to preserve the product direction while further
ideation takes place. Use it as context, not as a reason to skip
questions.

### 1.1 Required workflow

1.  Read this entire brief.
2.  Inspect the current repository and the `docs/init` branch,
    especially the permanent documentation under `kibootia-docs/`, the
    ADR index, open questions, roadmap, and implementation-status
    report.
3.  Inspect relevant source files to establish what exists today. The
    current codebase may be incomplete, inconsistent, or technically
    weak. That is not a reason to reduce the product ambition.
4.  Compare three things separately:
    -   **Owner-confirmed product direction** recorded in this brief.
    -   **Existing permanent documentation and ADRs**, including earlier
        decisions that may now conflict with the newer direction.
    -   **Observed implementation**, including gaps and discrepancies.
5.  Do not silently reconcile conflicts. Identify them and explain the
    consequences.
6.  Continue product ideation with the owner. Ask focused, useful
    clarifying questions before making assumptions. Ask about meaningful
    trade-offs, dependencies, operating costs, security,
    maintainability, and the desired developer experience.
7.  When an answer changes or confirms a decision, record it in the
    appropriate temporary planning notes first or in a clearly
    structured decision log.
8.  Do not write the final permanent specifications until the owner has
    had the opportunity to answer the important questions and explicitly
    agrees that the ideation is sufficiently complete.
9.  Once the owner confirms, update permanent documentation using the
    existing documentation governance and ADR rules.
10. Update the relevant indexes, links, open questions, roadmap, and
    implementation-status document as appropriate. Never claim planned
    capabilities are implemented or verified.

### 1.2 The codebase is evidence, not the product boundary

The owner explicitly stated:

> The current codebase might not be good enough and should not be a
> limiting factor. You may inspect it, but do not reduce planning just
> because the current codebase is not good enough.

Consequences: - Do not scope the intended product to match the present
repository. - Do not assume a dependency's presence means its feature
works. - Do not assume a missing implementation means the capability is
out of scope. - Do not make a weak implementation the default
architecture merely because it is already present. - Treat existing code
as a starting point that may be refactored or replaced where needed. -
Record the gap between desired capability and observed implementation
honestly. - Recommend a staged delivery plan if the full target is too
large to implement at once. Staging delivery is not the same as lowering
the target vision.

### 1.3 Ask, do not guess

The owner wants Claude to continue ideating and ask more questions.
Claude should: - Ask before assuming important product behavior. -
Prefer conversational questions; when several independent decisions need
to be made together, a compact multiple-choice set is acceptable. -
Explain concepts and trade-offs before asking the owner to choose when
the topic is unfamiliar. - Distinguish decisions already made from
provisional recommendations. - Avoid repeatedly asking questions that
this brief already answers unless a contradiction makes confirmation
necessary. - Do not treat an answer to one question as blanket approval
for adjacent decisions. - Avoid producing a massive implementation
prompt prematurely. The owner asked for a decision brief first; the next
phase is further ideation, not immediate coding.

------------------------------------------------------------------------

## 2. Product identity and ambition

### 2.1 What Kibootia is intended to be

Kibootia is a reusable, enterprise-oriented application foundation built
around: - **Spring Boot** for the backend. - **Next.js** for the
frontend.

The ambition is to provide a strong, reusable foundation for multiple
independent applications rather than rebuilding common architecture,
security, configuration, persistence, testing, operations, and
developer-experience conventions for each new project.

Kibootia is not: - A single business-domain application. - A hosted SaaS
product by definition. - A replacement for Spring Boot or Next.js. - A
domain-specific product with assumptions that every downstream
application must inherit.

The foundation should remain generic across business domains. A
consuming application brings its own business features, domain models,
workflows, and user-facing product experience.

### 2.2 Intended audiences

Kibootia is intended for both: 1. The owner's own future applications
and projects. 2. Other developers or small teams building applications
on the same general stack.

The design should therefore be understandable, documented, forkable,
extensible, and useful beyond one specific consuming application.

### 2.3 Product category: comprehensive / batteries-included foundation

The owner selected a **batteries-included, comprehensive starter**
direction rather than a tiny minimal skeleton.

Interpret this carefully: - Common, reusable enterprise capabilities
should be available as part of the foundation. - "Included" does not
mean every subsystem must be tightly coupled, always enabled, or
impossible to remove. - Capabilities should have clear boundaries and
should be modular where practical. - The starter should provide a
working default experience, not just a catalogue of libraries or empty
interfaces. - Optional or advanced infrastructure may remain opt-in
where making it mandatory would impose unnecessary operational
complexity. - The product should offer a coherent, integrated baseline
with documented extension paths.

The goal is not to maximize the number of dependencies. It is to deliver
a high-quality, useful foundation whose included capabilities work
together.

### 2.4 Guiding engineering principles

These are consistent with the current documentation and the owner's
intended direction: - Secure by default. - Explicit module boundaries
and contracts. - Architecture as an enforceable contract, not just
diagrams. - Separation of concerns. - Keep business-domain assumptions
out of the generic foundation. - Keep infrastructure replaceable where a
meaningful boundary is warranted. - Use dependencies and abstractions
for a clear reason. - Document not only what exists but why, how to
verify it, and how to extend it. - Prefer a modular monolith initially
over unnecessary microservice distribution. - Deliver in stages, while
preserving the larger target vision.

------------------------------------------------------------------------

## 3. Decisions and preferences already established

This section is intended to capture what has already been decided in the
ideation conversation. It is not a claim that these decisions are
implemented in the repository.

### 3.1 Current product scope

**Decision:** Plan for a comprehensive, batteries-included enterprise
foundation.

The desired product should include a coherent base spanning application
architecture, authentication and authorization, user administration,
persistence, file storage, configuration, testing, observability, local
development, and deployment guidance.

Some advanced capabilities may be optional integrations, but the overall
starter should feel like a usable foundation rather than a bare
scaffold.

### 3.2 Reference application / fresh-clone experience

**Decision:** A fresh clone should ultimately offer a working reference
application.

The target developer experience is that a developer can: 1. Clone the
repository. 2. Follow guided startup instructions. 3. Start the local
infrastructure. 4. Open the application and sign in. 5. Manage users
through an administrative interface. 6. Upload or change a profile
photo/avatar. 7. Observe the included enterprise infrastructure working
together.

This is a target state, not a statement that these flows currently work.

Claude should ask further questions about the exact reference features,
sample accounts, seed data, demo security, and how the reference
application is separated from reusable foundation code.

### 3.3 User management

**Decision:** Include a user-administration capability and UI, but make
it modular and replaceable.

The planned capability should cover the generic concerns needed by
applications, including: - User identity/profile representation
appropriate to the architecture. - Profile/avatar upload integration. -
Account lifecycle where supported by the identity architecture. - Role
and permission integration. - An administrative interface to manage
users, roles, and access. - Backend authorization for administrative
actions.

Important distinction: - Authentication and identity are expected to use
Keycloak with OpenID Connect. - Spring Security protects the backend and
enforces application access. - The user-management UI and
application-facing management workflows should not become inseparable
from the rest of the foundation. - The user administration module should
be customizable or replaceable by a consuming application. - Claude must
determine which operations belong to Keycloak, which belong to
Kibootia's application database, and which belong in the frontend. Do
not assume the application should duplicate Keycloak's user store or
become a second identity provider.

The user-management feature should be generic; it must not encode a
particular consuming application's business users or domain roles.

### 3.4 File storage and profile photos

**Decision:** Include file storage, including profile photos/avatars.

**Default storage choice:** MinIO.

Intended capabilities: - Upload a file. - Replace/update a file. -
Retrieve or serve a file safely. - Delete a file. - Support profile
photos and leave room for other application uploads. - Store file
contents in object storage rather than in the relational database. -
Store appropriate metadata and ownership/reference information in the
relational database. - Apply backend validation and authorization. -
Configure storage through environment-driven settings. - Include MinIO
in the local development infrastructure. - Place storage behind a clear
service/adapter boundary so the storage provider can potentially be
changed later.

MinIO is the selected default for this planning direction. Do not
quietly replace it with local disk, database blobs, or another vendor.
If new evidence raises concerns about licensing, compatibility,
maintenance, or deployment, explain the trade-off and ask the owner
before changing the choice.

Still to clarify with the owner: - File size limits and allowed content
types. - Whether profile images are public, private, or conditionally
accessible. - Whether to issue short-lived presigned URLs or stream
files through the backend. - Malware scanning, content inspection, image
resizing, and thumbnail generation. - Bucket policy and object naming. -
Metadata, deletion, orphan cleanup, and retention behavior. - Production
storage configuration and backup expectations.

These are open design details, not already-decided behavior.

### 3.5 Observability

**Decision:** Aim for a complete local observability stack, not merely
basic log statements and health checks.

The intended direction includes meaningful support for: - Application
logs. - Metrics. - Request correlation. - Distributed tracing. - Health
checks. - Dashboards and useful local visibility into the system.

Technologies discussed as candidates include: - Spring Boot Actuator. -
Micrometer. - Prometheus. - Grafana. - OpenTelemetry. - Loki with
Grafana for logs.

These were discussed as a likely technology set, but the owner has not
yet confirmed every individual component as a final decision. Claude
should verify the exact stack, the responsibilities of each component,
and whether a single local Compose stack is the preferred experience.

The final design should avoid duplicative or competing telemetry paths.
It should document what each component collects, where data goes, how to
inspect it locally, how to secure it, and what changes in production.

### 3.6 Technology catalogue and recipes

**Decision:** Documentation should include both a technology catalogue
and practical recipes/guides.

The technology catalogue should identify technologies that are: -
Selected as defaults. - Integrated in the repository. - Verified by
tests or a working setup. - Planned but not implemented. - Optional or
supported as extension paths. - Considered but not selected, where the
reasoning is useful.

These labels must be explicit and must not be conflated.

The catalogue should cover the initial technology direction, including
the observability stack and file storage, rather than only the
technologies currently present in the repository. It should also invite
future enterprise technology additions through a disciplined decision
process.

Recipes/guides should explain: - The problem a technology solves. - When
to use it and when not to. - Compatibility assumptions and version
alignment. - Trade-offs and operational costs. - How it integrates with
the architecture. - How configuration and secrets are handled. - How
security, testing, observability, and documentation are maintained. -
How a consuming application can replace or extend it. - How to verify
the integration.

Do not add a technology just because it is popular or has an
"enterprise" label. Each one needs a clear purpose and fit.

### 3.7 Authorization ambition

**Decision:** The authorization foundation should be more ambitious than
simple role checks, while keeping the implementation modular and
appropriately staged.

The intended target includes: - Extensible role-based access control
(RBAC). - Resource-level or object-level authorization. - A path to
attribute-based access control (ABAC) and contextual policies. -
Ownership, relationship, and potentially tenant context where
relevant. - Explicit backend enforcement. - Tests for allowed and denied
cases. - A model that can grow without forcing every application to
adopt every advanced capability.

Do not treat frontend hiding or disabled controls as security
enforcement. The backend is authoritative.

Authentication and authorization are separate: - Keycloak with OpenID
Connect is the intended identity/authentication integration. - Spring
Security is the backend security enforcement foundation. - Application
authorization logic decides whether the authenticated principal may
perform a specific action on a specific resource. - Do not assume that
all application authorization policy must live inside Keycloak.

The owner wants advanced authorization capability in the product vision.
That does not automatically mean a third-party policy engine must be
mandatory from day one.

### 3.8 OPA: current recommendation, not yet confirmed

OPA means **Open Policy Agent**, a general-purpose policy engine. It can
evaluate policies, often written in Rego, against structured input. It
does not authenticate users or automatically know all of the
application's resource data.

The architecture discussion reached this recommendation: - Do not make
OPA mandatory merely to call the starter "enterprise." - Design a clear
policy-decision boundary so OPA can be integrated later or optionally. -
Initially, Spring Security plus a well-designed application
authorization layer may be sufficient. - Consider OPA when policies
become complex, need independent policy lifecycle/governance, need
consistent evaluation across multiple services, or benefit from
separating policy decisions from application code. - Adding OPA
introduces operational concerns: running and securing the policy engine,
supplying policy input/data, testing and versioning policies, monitoring
decisions, and handling policy-engine failure.

**Status:** The owner has not yet answered the explicit OPA choice.
Claude must ask and record a final decision.

The choices previously presented were: - **A. Spring Security only
initially:** build a well-structured authorization layer, no OPA
integration planned. - **B. OPA-ready architecture with optional
integration:** create a policy decision interface and keep OPA as an
optional extension. - **C. OPA included by default:** ship and operate
OPA as part of the standard stack.

The previous recommendation was **B**, but it is not an owner-confirmed
decision.

### 3.9 Multi-tenancy / tenant readiness: unresolved

Earlier project context points toward: - Single-tenant behavior
initially. - No organization onboarding or full tenant-management UI in
the initial scope. - Preserve a path toward future tenant-aware
authorization and data access. - Avoid adding a full tenant subsystem
before it is needed. - When tenant-owned data is introduced, tenant
isolation must be enforced in backend authorization and data-access
boundaries, not frontend filtering. - Prefer designing tenant boundaries
deliberately rather than trying to retrofit isolation after data has
spread across the system.

However, the exact scope is **not finalized**. A recent question
presented these choices: - **A. No special preparation:** handle tenancy
only if a future application needs it. - **B. Extension-ready:**
document and preserve a tenant boundary, but do not implement a tenant
subsystem now. - **C. Tenant-aware by default:** include tenant context
and enforcement as part of the default platform.

Prior context suggests B may be a reasonable candidate, but Claude must
not silently treat it as approved. Explain the cost and consequences and
ask the owner.

### 3.10 Local developer experience

**Decision:** Target a guided local startup experience with Docker
Compose, health checks, and troubleshooting.

The intended developer experience should make it clear: - What
prerequisites are needed. - How to configure the local environment. -
Which services are started by Compose. - How to start the backend and
frontend. - How to tell when services are healthy. - How to sign in to
the reference application. - Where to inspect logs, metrics, traces, and
dashboards. - How to reset local data safely. - How to troubleshoot
common failures.

Do not make the fresh-clone path depend on undocumented manual steps or
hidden environment state.

### 3.11 Configuration and secrets

**Decision:** Use safe environment templates and validate configuration.

Desired behavior: - Provide a clearly documented example environment
file. - Never commit real secrets. - Validate required configuration and
fail clearly when required settings are missing or malformed. - Use
local-only/generated secrets where appropriate. - Keep local defaults
distinct from production configuration. - Do not allow production to
silently fall back to insecure development credentials. - Document how
configuration reaches the application and infrastructure.

Existing documentation discusses environment-driven local configuration
and HashiCorp Vault for production secrets. A provisional ADR discusses
platform-injected secrets without a Vault client inside the application.
The exact production secret delivery decision should be reviewed and
confirmed rather than assumed.

### 3.12 Deployment documentation

**Decision:** Document both local development and production deployment
workflows.

The documentation must explicitly distinguish: - A convenient local
developer environment. - A reference deployment. - A production
deployment requiring environment-specific hardening and operational
controls.

A successful local Compose startup must not be described as proof of
production readiness.

The exact production target---such as a generic container platform,
managed hosting, or a later Kubernetes path---has not been finalized in
the ideation captured here. Do not force Kubernetes into the default
architecture without a concrete requirement.

### 3.13 Architecture direction

The current permanent docs record these existing architecture decisions,
which remain useful context unless the owner chooses to revise them: - A
modular monolith as the initial backend architecture. - Hexagonal
architecture where it protects meaningful boundaries. - Separation
between API DTOs, application models, domain logic where useful, and
persistence entities. - Explicit module contracts. - Feature-owned
persistence implementations. - Architecture tests, with ArchUnit already
selected in an accepted ADR. - MySQL with Spring Data JPA/Hibernate and
Flyway as the existing intended persistence stack. - Keycloak/OIDC for
identity and Spring Security for backend protection. - In-process events
for suitable local reactions, with no durable message broker initially.

These decisions should be evaluated against the expanded product scope.
Do not assume that a broader set of capabilities automatically requires
microservices or a message broker. Conversely, do not let the existing
minimal milestone plan permanently exclude useful capabilities that are
now part of the intended product.

### 3.14 Messaging and event infrastructure

The current accepted ADR 0014 chooses in-process events initially and
defers Kafka/RabbitMQ until a concrete need arises.

The owner has not confirmed a new default decision that reverses that
ADR. In the broader product discussion, durable messaging was considered
as a possible enterprise capability, but it was not selected as a
mandatory default.

Claude should ask whether the starter should: - Keep in-process events
as the only default and document broker recipes. - Include one optional
broker integration in the reference stack. - Ship a durable broker by
default.

If a broker is considered, compare RabbitMQ and Kafka against actual use
cases and operational costs. Do not install both by default merely to
offer choices. Explain whether a transactional outbox is needed for
reliable publication and which delivery guarantees the design actually
provides.

### 3.15 Java version

The Java-version decision was explicitly deferred in the recent
conversation so the owner could focus on product planning first.

The current repository and existing ADRs disagree: the
implementation-status report says the Maven configuration specifies Java
17 while ADR 0019 records Java 21 as the accepted baseline. This
discrepancy is real and should be recorded, but **do not reopen or
decide the Java version as part of this product-vision ideation unless
the owner brings it back into scope**.

The owner values stability and compatibility, and wants to avoid
choosing a version that is too old or too new. Resolve this separately
later.

------------------------------------------------------------------------

## 4. Repository inspection: what is currently known

The owner asked for the GitHub repository and the `docs/init` branch to
be inspected, with the explicit warning that the codebase must not limit
the vision.

Repository: - `https://github.com/ishanKulkarni13/Kibootia` - Branch:
`docs/init` - Branch URL:
`https://github.com/ishanKulkarni13/Kibootia/tree/docs/init`

The following observations were made through GitHub repository/file
inspection. They are not a fresh build/test result.

### 4.1 Existing documentation system

The branch contains a permanent documentation tree under
`kibootia-docs/`, including: - `product/` for vision, goals, milestones,
and implementation status. - `architecture/` for architecture, identity,
data, APIs, and communication. - `engineering/` for setup, conventions,
testing, and verification. - `operations/` for configuration,
deployment, monitoring, and secrets. - `decisions/` for ADRs, an ADR
index, and open questions. - `meta/` for documentation standards and
living-documentation rules. - `shared/` for shared terminology. -
`temp/` for non-authoritative source/planning material.

Documentation governance includes relative links, indexes, standard
headers, ADR conventions, and a distinction between document status and
implementation status. Preserve these conventions when updating
permanent docs unless the owner approves a deliberate change.

### 4.2 Observed implementation snapshot

The implementation-status document, dated 2026-10-27, reports: - A
Spring Boot backend skeleton exists. - The backend Maven configuration
uses Spring Boot 4.1.1 and currently declares Java 17, while ADR 0019
records Java 21 as the accepted baseline. - MySQL 8.4 is configured in
the Compose file. - Spring Security and OAuth2 resource-server
dependencies exist, but the status report says security configuration is
not complete. - Flyway dependencies exist, but migration scripts are
absent. - JPA/Hibernate is intended but the status report says the JPA
starter is absent. - Keycloak is intended but the status report says it
is not in the current Compose file and no realm file exists. - The
frontend directory is not present according to the status report. - The
status report says there is no CI configuration. - The status report
says nothing was verified in its snapshot because the tests were not
run. - `spring-boot-admin-starter-server` was identified in the Maven
configuration as unintended by the owner. - The Compose file currently
starts MySQL and Adminer, with a persistent MySQL volume and a health
check. - The Compose file requires MySQL credentials from environment
configuration.

These observations should be rechecked if the branch has changed since
the snapshot. Do not treat them as permanent facts without checking
current files.

### 4.3 Documentation conflicts that need attention

The existing permanent docs and temporary project definition were
written before the more ambitious product direction above. For example,
the earlier goals/non-goals and project definition exclude: - MinIO or
dedicated object storage. - A full Prometheus/Grafana stack. - A
complete ABAC engine. - Organization-management UI. - A full
tenant-management subsystem. - Kafka or RabbitMQ as initial
requirements.

Some of those exclusions may still be appropriate, but the expanded
direction now includes file storage, a full local observability target,
a more advanced authorization foundation, and modular user
administration. Therefore, the documents need an explicit reconciliation
process.

Do not simply edit an old accepted ADR as if it never existed. Under the
existing documentation standards, an accepted ADR is not rewritten; a
changed decision should be recorded in a new ADR that supersedes the
previous one where appropriate. If a document is only draft, it may be
updated after the owner confirms the direction.

### 4.4 No implementation claims from this brief

This document does not prove that any capability is implemented or
working. Claude must use the repository and automated verification to
classify each capability as planned, observed, or verified according to
the project's living-documentation rules.

------------------------------------------------------------------------

## 5. Documentation governance to preserve

The existing permanent documentation establishes the following
conventions. Claude should follow them when later updating the permanent
docs.

### 5.1 Source-of-truth order

The current living-documentation file says the precedence order is: 1.
Current decisions of the project owner. 2. Accepted ADRs. 3. Permanent
documentation. 4. Current code and automated tests as evidence of what
works. 5. Drafts, proposals, and temporary notes.

The distinction is important: - Owner decisions establish intended
direction. - Code/tests establish observed or verified behavior. - An
intended capability can be planned without being implemented. - An
accepted ADR is not proof of implementation.

### 5.2 Document status versus implementation status

Keep these separate.

Document status: - `draft` - `review` - `accepted` - `deprecated` -
`archived`

Implementation status: - `planned` - `observed` - `verified` -
`not applicable`

Only use `verified` where the project's verification rules are
satisfied. Merely reading code or manually seeing a screen is not
equivalent to a passing automated test/CI result if the project's
verification standard requires CI.

### 5.3 ADRs

The existing standards say: - ADRs use four-digit sequence numbers. -
Every ADR is listed in `decisions/INDEX.md`. - Accepted ADRs are not
rewritten. - A changed decision is recorded in a new ADR that identifies
what it supersedes and why. - Related permanent architecture/operations
docs and indexes must be updated. - Resolved open questions should be
removed from the open-questions list and the resolution noted.

### 5.4 Relative links and indexes

-   Use relative links within the documentation tree.
-   Update each folder's `README.md` when documents are added, moved,
    renamed, or retired.
-   Update the central ADR index when an ADR is added or changes status.
-   Keep one canonical home for each fact and link to it elsewhere.
-   Do not copy the same long explanation into several permanent
    documents.
-   Preserve the portability of the documentation when a consuming
    project renames or forks the documentation root.

### 5.5 Temporary notes are not permanent authority

This file belongs in `temp/` and is a planning handoff. It should not be
treated as a replacement for the permanent vision, architecture
specifications, or ADRs. Once the owner confirms the final decisions and
the permanent documentation has been updated, the owner may retain,
supersede, or archive this planning note according to the project's
documentation conventions.

------------------------------------------------------------------------

## 6. Open decisions Claude must continue exploring

This section is deliberately not filled with invented answers. These are
areas where the owner expects further ideation and questions.

### 6.1 OPA decision

Ask the owner to choose between: - Spring Security plus a well-designed
application authorization layer, without OPA initially. - OPA-ready
architecture with optional integration. - OPA integrated and operated by
default.

Explain the practical difference, the data that must be supplied to a
policy engine, operational complexity, and why ABAC does not
automatically require OPA.

### 6.2 Tenant strategy

Ask the owner to choose between: - No tenant-specific preparation. -
Extension-ready tenant boundary without a full tenancy subsystem. -
Tenant-aware architecture and enforcement by default.

Discuss schema implications, data-access scoping, request context,
authorization, background jobs, object storage, tests, and the cost of
retrofitting tenant isolation. Keep organization
onboarding/administration separate from low-level tenant-safety
foundations.

### 6.3 Exact observability stack

The owner chose a complete local observability direction, but the exact
components need confirmation. Explore a coherent baseline using the
technologies already discussed: - Actuator and Micrometer. -
Prometheus. - Grafana. - OpenTelemetry. - Loki/Grafana for logs.

Ask whether all should be in the default local Compose stack or whether
some should be optional profiles. Avoid overlapping collection paths and
explain resource cost and data flow.

### 6.4 Messaging strategy

The existing ADR defers a broker. Ask whether that should remain the
default, become an optional profile/recipe, or change to a broker in the
standard stack. Compare RabbitMQ and Kafka against real needs; do not
assume both are necessary.

### 6.5 Reference application scope

Ask which features the reference application must demonstrate beyond: -
Sign-in. - User administration. - Role/access management. - Avatar
upload. - Observability and local infrastructure visibility.

Clarify whether the reference app is a demo module shipped with the
starter, an example feature removable from forks, or a separate sample
application. It must not contaminate the generic foundation with
business-domain assumptions.

### 6.6 User identity and account management boundary

Ask which operations should be performed through Keycloak administration
APIs versus stored as application profile data. Clarify: - Whether roles
are sourced from Keycloak, application data, or a deliberately defined
combination. - How role changes are made and how quickly they take
effect. - Which profile fields are application-owned. - How account
disable/delete/deactivate flows should work. - Whether the admin UI is a
thin interface to Keycloak, an application-specific administration
layer, or a combination.

Do not invent a second user credential store.

### 6.7 File access and processing

Ask about: - Public versus private avatars. - Whether files are streamed
through the backend or served via short-lived presigned URLs. - File
validation, size/type limits, image processing, scanning, and
metadata. - Object deletion and orphan cleanup. - Local development
versus production object-storage configuration.

### 6.8 Security expectations

Clarify the desired baseline for: - OIDC login flow and frontend
session/token handling. - CSRF and CORS. - Backend audience and issuer
validation. - Deny-by-default behavior. - Admin privilege boundaries. -
Rate limiting and brute-force protection. - Secure cookies, session
expiry, logout, and token refresh. - Security headers and upload
security. - Secret rotation and local credentials. - Threat modeling and
security testing.

Do not assume the current draft security decisions settle every detail.

### 6.9 Production deployment target

Ask what deployment environments should be documented first. Explain
that the starter can remain container-friendly without making Kubernetes
mandatory. Clarify which production guidance should be generic versus
specific to a selected host/platform.

### 6.10 Technology catalogue detail

Ask whether the catalogue should include only core/default technologies
and approved extension recipes, or also a "considered alternatives"
section. Keep it actionable rather than turning it into a list of every
enterprise technology.

### 6.11 Version compatibility and upgrades

The owner wants stable, compatible versions and is wary of versions that
are too old or too new. Java selection was deferred. Later, establish a
version policy for the overall stack: - Supported baseline versions. -
How compatibility is validated. - Dependency update strategy. - Security
update policy. - How upgrades are tested. - How consuming projects
inherit or adopt updates. Do not decide the Java version as part of this
brief unless the owner explicitly brings it back into scope.

### 6.12 Starter adoption and updates

The existing open questions include how consuming applications adopt the
starter and receive updates, and what guarantees the starter offers.
This is especially important for a reusable foundation. Ask whether the
intended usage is primarily: - A template repository copied/forked by
each application. - A versioned starter that generates new projects. - A
shared library/platform dependency. - A hybrid approach.

Explain that a copied template and a shared dependency have different
upgrade and ownership models. Do not assume the answer.

### 6.13 Optionality and modularity

For each capability, distinguish: - Core mandatory behavior. - Included
and enabled by default. - Included but optional/disabled by default. -
Optional recipe/integration. - Future extension point only.

The user-management UI, storage provider, OPA, messaging, and
observability deployment are likely to have different modularity needs.
Ask how much a consuming application should be able to remove or replace
without fighting the architecture.

------------------------------------------------------------------------

## 7. Potential capability catalogue to validate with the owner

This is a **planning checklist**, not an approved final feature list.
Claude should use it to identify gaps and ask questions.

### 7.1 Application architecture

-   Modular monolith structure.
-   Clear feature/module ownership.
-   Explicit public contracts.
-   Dependency rules and architecture tests.
-   Separation of API, application, domain where warranted, and
    persistence models.
-   Consistent error and validation behavior.
-   API documentation and versioning conventions.
-   Pagination and filtering conventions where useful.

### 7.2 Identity and access

-   Keycloak/OIDC integration.
-   Spring Security resource-server protection.
-   Token validation and issuer/audience policy.
-   Role-based authorization.
-   Resource/object-level authorization.
-   ABAC/contextual policy extension.
-   Optional OPA integration (decision pending).
-   User administration interface and service boundary.
-   Audit trail for security-sensitive administrative actions.
-   Optional tenant-aware extension (decision pending).

### 7.3 Persistence and data integrity

-   MySQL baseline.
-   Spring Data JPA/Hibernate.
-   Flyway-managed schema evolution.
-   Explicit transaction boundaries.
-   Auditing fields where justified.
-   Migration testing.
-   Testcontainers for integration tests.
-   Data ownership and cross-module access rules.
-   Future tenant isolation considerations.

### 7.4 Files and assets

-   MinIO default local object storage.
-   Upload/replace/retrieve/delete flow.
-   Avatar/profile-photo use case.
-   Metadata and ownership records.
-   Backend authorization and validation.
-   Storage abstraction and configuration.
-   Safe file-access strategy.
-   File cleanup and operational guidance.

### 7.5 Observability and operations

-   Health/readiness/liveness contract where applicable.
-   Structured logs.
-   Request/correlation IDs.
-   Metrics.
-   Distributed tracing.
-   Dashboards.
-   Centralized logs.
-   Local Compose observability stack.
-   Production configuration/security guidance.
-   Operational troubleshooting.

### 7.6 Developer experience

-   Guided setup.
-   Environment template.
-   Validated configuration.
-   Docker Compose infrastructure.
-   Health checks and startup ordering.
-   Clear service ports and URLs.
-   Seed/demo accounts that are safe for local use.
-   Troubleshooting guides.
-   One clear fresh-clone path.
-   Safe reset/reseed instructions.

### 7.7 Testing and quality

-   Unit tests.
-   Integration tests.
-   Architecture tests.
-   API tests.
-   Authentication and authorization tests.
-   Migration and persistence tests.
-   Frontend tests.
-   End-to-end reference flow.
-   CI verification.
-   Dependency and security checks as appropriate.
-   Explicit distinction between observed and verified capabilities.

### 7.8 Deployment and configuration

-   Container-friendly backend and frontend.
-   Local versus production configuration separation.
-   Secret handling.
-   Health endpoints and observability.
-   Migration execution strategy.
-   Object storage setup.
-   Secure service exposure.
-   Backup and restore considerations for stateful dependencies.
-   Deployment recipes for selected targets.

### 7.9 Integration capabilities to consider, not automatically include

-   RabbitMQ or Kafka.
-   Transactional outbox.
-   Email delivery.
-   Scheduled/background jobs.
-   Caching.
-   Search/indexing.
-   Feature flags.
-   Rate limiting.
-   Audit/event history.
-   Notification delivery.
-   Additional storage providers.
-   Policy engine integrations.
-   Additional monitoring/alerting integrations.

Claude should ask which of these belong in the default product, which
should be optional recipes, and which should stay out until a real use
case exists.

------------------------------------------------------------------------

## 8. Planning and delivery principles

The target product can be comprehensive while implementation remains
staged.

### 8.1 Separate vision from delivery order

Do not use the first milestone plan as a permanent definition of product
scope. The current roadmap has six milestones focused on architecture,
configuration, security, persistence, API/operations, and frontend
integration. That sequence may need to be redesigned after the product
scope is confirmed.

A better future plan should: - Show dependencies among capabilities. -
Define a usable end-to-end vertical slice early. - Avoid building
isolated infrastructure that is never exercised. - Integrate security,
observability, testing, and documentation as capabilities are added. -
Deliver the reference application incrementally. - Identify which
infrastructure is mandatory for local startup and which can be an
optional profile. - Make completion criteria measurable and testable.

### 8.2 Prefer vertical integration over disconnected checklists

For example, the reference user-avatar flow should eventually
demonstrate the entire integration: 1. A user signs in. 2. Backend
security validates the caller. 3. Authorization confirms the user can
change that profile image. 4. The backend validates the file. 5. The
file is stored in MinIO. 6. Metadata is persisted in the relational
database. 7. The frontend displays the image using the chosen secure
access mechanism. 8. Logs, metrics, and traces help diagnose the
request. 9. Automated tests cover allowed and denied cases, upload
constraints, persistence behavior, and the storage integration.

This is an illustrative end-to-end scenario, not a final technical
implementation decision. Claude should use similar scenarios to discover
missing requirements.

### 8.3 Avoid "enterprise by dependency count"

Every default technology should justify its: - Functional value. -
Security implications. - Maintenance cost. - Local resource usage. -
Production operating cost. - Integration and testing burden. - Failure
modes. - Upgrade path.

Where a capability is useful but too expensive to run for every
developer, consider an optional Compose profile or documented
integration rather than removing the capability from the product vision.

### 8.4 Make reference defaults safe

A working demo should not train developers to copy insecure production
patterns. Demo credentials, exposed ports, development secrets,
permissive CORS, public object buckets, and admin accounts must be
clearly scoped to local development and must not silently carry into
production.

------------------------------------------------------------------------

## 9. Important distinctions that must remain explicit

### 9.1 "Included" does not always mean "mandatory"

A capability can be part of the starter's supported product while
remaining: - Optional at runtime. - Enabled through a profile. -
Replaceable by a consuming application. - Documented as a recipe rather
than always running in local Compose.

Claude should classify each capability explicitly rather than collapsing
these categories.

### 9.2 "Ready for" does not mean "implemented"

An extension point, interface, or architecture decision does not mean an
integration exists. A catalogue must distinguish planned, observed, and
verified states.

### 9.3 Authentication is not authorization

A successful Keycloak login or valid access token proves identity/claims
according to the token policy. It does not automatically permit every
application action. Spring Security and application policy must enforce
access at the backend.

### 9.4 Role management is not the same as a full authorization model

RBAC answers questions through roles and permissions. Resource-level
policies and ABAC may also depend on resource ownership, tenant
membership, classification, relationship, or other attributes. The
product should support an appropriate growth path without pretending
every application needs maximum policy complexity.

### 9.5 A policy engine is not an identity provider

OPA evaluates policy; it does not replace Keycloak. It does not
automatically own the user store, manage role assignments, or discover
application data. Policy inputs and failure behavior must be designed.

### 9.6 Local observability is not production operations

A local dashboard stack helps development and diagnosis. Production
still needs environment-specific security, retention, access control,
alerting, scaling, backup, and operations decisions.

### 9.7 A modular monolith is compatible with a comprehensive foundation

A broad set of capabilities does not automatically require
microservices. Keep deployment and runtime architecture as simple as
practical while enforcing strong internal boundaries. Add distributed
infrastructure only where it solves a real requirement.

------------------------------------------------------------------------

## 10. Suggested process for recording future decisions

Claude should maintain a clear decision log during ideation. For each
topic, record:

-   **Question:** What needs to be decided?
-   **Context:** Why it matters.
-   **Options:** Realistic alternatives.
-   **Trade-offs:** Cost, complexity, security, flexibility, developer
    experience, operations.
-   **Recommendation:** Claude's recommendation, clearly labeled as a
    recommendation.
-   **Owner decision:** Exact answer or decision, without
    reinterpretation.
-   **Status:** Open, provisional, confirmed, superseded.
-   **Affected docs:** Which permanent documents and ADRs will need
    changes.
-   **Implementation implications:** What needs to be built, tested,
    operated, or migrated.
-   **Verification criteria:** How the capability will be proven to
    work.

Do not mark a recommendation as confirmed until the owner explicitly
agrees.

------------------------------------------------------------------------

## 11. Expected outcome of the next ideation phase

Before updating permanent documentation, Claude should deliver a concise
but complete ideation checkpoint containing:

1.  A summary of decisions confirmed by this brief.
2.  A list of important questions that remain open.
3.  Recommendations with rationale and trade-offs.
4.  A conflict map showing which existing docs and ADRs contradict the
    new direction.
5.  A proposed capability catalogue with each capability classified as
    core, included/optional, optional integration, or out of scope.
6.  A proposed architecture/technology view that clearly distinguishes
    selected choices from candidates.
7.  A proposed staged roadmap that can deliver a working reference
    application without reducing the target vision.
8.  A list of permanent docs that will be updated, new docs that will be
    created, and ADRs that may need superseding.
9.  A request for owner confirmation before making those permanent
    documentation changes.

Claude should not treat this expected outcome as permission to stop
asking questions if critical design choices remain unresolved.

------------------------------------------------------------------------

## 12. Summary of the current decision state

  -----------------------------------------------------------------------------------
  Topic                   Current state           Notes
  ----------------------- ----------------------- -----------------------------------
  Product identity        Confirmed direction     Reusable Spring Boot + Next.js
                                                  enterprise foundation

  Product scope           Confirmed direction     Comprehensive/batteries-included,
                                                  modular rather than tightly coupled

  Audience                Confirmed direction     Owner's own future apps and other
                                                  developers/teams

  Reference application   Confirmed target        Fresh clone should demonstrate
                                                  sign-in, user administration,
                                                  avatar upload, and integrated
                                                  infrastructure

  User administration     Confirmed target        Included but modular/replacable by
                                                  consuming applications

  File storage            Confirmed default       MinIO, including profile photos and
                          choice                  a storage boundary

  Observability           Confirmed scope         Complete local stack; exact
                          direction               components still need owner
                                                  confirmation

  Technology catalogue    Confirmed documentation Include technologies and explicit
                          need                    status labels

  Recipes/guides          Confirmed documentation Explain integration, compatibility,
                          need                    trade-offs, security, tests, and
                                                  extension

  Authorization           Confirmed ambition      Extensible RBAC, resource-level
                                                  access, ABAC/contextual growth path

  OPA                     Open                    Prior recommendation: OPA-ready
                                                  with optional integration

  Multi-tenancy           Open                    Extension-ready is a plausible
                                                  direction, not yet confirmed

  Local developer         Confirmed target        Guided Compose startup, health
  experience                                      checks, troubleshooting

  Configuration           Confirmed target        Safe templates, validated
                                                  configuration, no committed secrets

  Deployment              Confirmed target        Local and production guidance,
  documentation                                   clearly distinguished

  Modular monolith        Existing accepted       Reassess only if a concrete
                          architectural direction requirement warrants a change

  Keycloak/OIDC + Spring  Existing intended       Clarify exact user/admin boundaries
  Security                direction               and frontend login flow

  MySQL/JPA/Flyway        Existing intended       Recheck against current repo and
                          direction               existing ADRs

  MinIO production        Open                    Local default selected; secure
  details                                         production behavior needs design

  Observability tools     Partially decided       Candidate technologies discussed,
                                                  exact bundle not yet confirmed

  Message broker          Open / existing ADR     Decide default vs optional
                          defers it               integration vs no broker

  Production platform     Open                    Do not assume Kubernetes

  Starter adoption/update Open                    Template/fork vs versioned
  model                                           generator vs shared
                                                  dependency/hybrid

  Java baseline           Deferred                Existing ADR and current pom
                                                  disagree; do not decide during this
                                                  phase unless owner reopens it

  Current codebase        Not a product limit     Inspect and document gaps; do not
  quality                                         reduce ambition to match the code

  Final Claude            Not requested yet       Continue ideation first; do not
  implementation prompt                           jump straight to implementation
  -----------------------------------------------------------------------------------

------------------------------------------------------------------------

## 13. Final instruction to Claude

Treat this brief as the accumulated starting point for the next round of
product ideation. Respect the confirmed directions, question the
unresolved ones, and actively look for gaps or contradictions.

The owner wants a strong, stable, enterprise-grade foundation that can
be reused across different applications. Do not confuse that ambition
with a requirement to run every possible infrastructure component by
default. Aim for a coherent, batteries-included developer experience
with modular boundaries, clear defaults, optional integrations where
appropriate, and high-quality documentation.

**Inspect the codebase, but do not let its current limitations define
the product. Ask the owner before resolving important ambiguity.
Document the owner's decisions accurately. Only after the ideation is
sufficiently complete should you update the permanent docs, following
the repository's existing documentation and ADR governance.**
