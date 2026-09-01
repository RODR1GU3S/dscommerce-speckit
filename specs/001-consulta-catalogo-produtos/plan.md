# Implementation Plan: Consulta do Catalogo de Produtos

**Branch**: `001-consulta-catalogo-produtos` | **Date**: 2026-08-19 | **Spec**: `specs/001-consulta-catalogo-produtos/spec.md`

**Input**: Feature specification from `specs/001-consulta-catalogo-produtos/spec.md`

**Note**: This template is filled in by the `$speckit-plan` command; its definition describes the execution workflow.

## Summary

Implement a public REST catalog endpoint that returns a paginated list of existing products with only name, image reference, and price, ordered alphabetically by product name. The implementation will use a layered Spring Boot backend with controller DTOs, a service-managed read flow, and a JPA repository query using `name` ascending plus `id` ascending as the stable tie-breaker.

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: Spring Boot 3.x, Spring Web, Spring Data JPA, Hibernate, Bean Validation, Jackson

**Storage**: Relational database accessed through JPA/Hibernate repositories; H2 can be used for local validation until a production database is configured

**Testing**: JUnit 5, Spring Boot Test, MockMvc, repository tests with an in-memory database

**Target Platform**: JVM backend service exposing HTTP/JSON APIs

**Project Type**: Single Spring Boot web service

**Performance Goals**: Return a bounded catalog page without loading the full catalog into memory; support validation with at least 50 products across multiple pages

**Constraints**: Default pagination is page `0` and size `12`; page must be `>= 0`; size must be between `1` and `50`; invalid pagination returns `400 Bad Request`; response must not expose product detail fields outside this feature

**Scale/Scope**: One read-only catalog listing endpoint, one response DTO for listed products, one page wrapper contract, and tests for empty, paginated, ordered, default, and invalid pagination flows

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

Pre-design gate:

- Java and Spring Boot First: PASS. The feature is planned as a Spring Boot REST backend.
- Layered Architecture: PASS. Controller handles HTTP/query params, service owns catalog read rules, repository handles JPA access.
- DTO-Based API Contracts: PASS. API output uses catalog DTOs and does not expose JPA entities.
- Persistence and Validation Discipline: PASS. Product reads use Spring Data JPA; query parameter constraints are validated at the controller boundary.
- Centralized Error Handling and REST Semantics: PASS. Invalid pagination maps to `400 Bad Request` through centralized error handling; successful empty results return `200 OK` with page metadata.

Post-design gate:

- Java and Spring Boot First: PASS. Research and contracts retain Spring Boot 3.x as the implementation stack.
- Layered Architecture: PASS. `ProductController`, `ProductCatalogService`, and `ProductRepository` responsibilities are explicitly separated.
- DTO-Based API Contracts: PASS. `ProductCatalogItemDTO` and `PageResponseDTO` form the public response shape.
- Persistence and Validation Discipline: PASS. `Product` is modeled as a JPA entity, query params have explicit bounds, and stable ordering is repository-driven.
- Centralized Error Handling and REST Semantics: PASS. Contracts define `200` for usable/empty pages and `400` for invalid pagination.

## Project Structure

### Documentation (this feature)

```text
specs/001-consulta-catalogo-produtos/
|- plan.md              # This file ($speckit-plan command output)
|- research.md          # Phase 0 output ($speckit-plan command)
|- data-model.md        # Phase 1 output ($speckit-plan command)
|- quickstart.md        # Phase 1 output ($speckit-plan command)
|- contracts/           # Phase 1 output ($speckit-plan command)
`- tasks.md             # Phase 2 output ($speckit-tasks command - NOT created by $speckit-plan)
```

### Source Code (repository root)

```text
src/
|- main/
|  |- java/.../controllers/
|  |- java/.../dto/
|  |- java/.../entities/
|  |- java/.../repositories/
|  |- java/.../services/
|  `- resources/
`- test/
   `- java/.../
pom.xml
README.md
```

**Structure Decision**: Use a single Spring Boot backend project at the repository root. The repository currently contains only Spec Kit documentation, so implementation tasks will create the standard Maven/Spring source tree rather than adapt existing source directories.

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| None | N/A | N/A |
