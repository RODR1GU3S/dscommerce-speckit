# Implementation Plan: Visualizar Detalhes do Produto

**Branch**: `002-visualizar-detalhes-produto` | **Date**: 2026-09-12 | **Spec**: `specs/002-visualizar-detalhes-produto/spec.md`

**Input**: Feature specification from `specs/002-visualizar-detalhes-produto/spec.md`

**Note**: This template is filled in by the `$speckit-plan` command; its definition describes the execution workflow.

## Summary

Extend the existing product catalog backend with a read-only product detail consultation for an existing product selected from the catalog. The implementation will keep the current `GET /products` listing contract intact and add a resource-oriented `GET /products/{id}` detail endpoint that returns name, price, description, image, and unique category names through a dedicated response DTO. Product not found will be mapped to the existing centralized error response shape with `404 Not Found`.

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: Spring Boot 3.3.4, Spring Web, Spring Data JPA, Hibernate, Bean Validation, Jackson, H2

**Storage**: Relational database accessed through JPA/Hibernate repositories; current local/test profile uses H2 with schema generated from entities and seeded by `import.sql`

**Testing**: JUnit 5, Spring Boot Test, MockMvc, repository tests with in-memory H2, Mockito service tests

**Target Platform**: JVM backend service exposing HTTP/JSON APIs

**Project Type**: Single Spring Boot web service

**Performance Goals**: Fetch one product by identifier without scanning the full catalog; load its categories in the same repository operation used by the detail service flow to avoid lazy loading surprises and duplicate category output

**Constraints**: Preserve feature 001 behavior exactly: `GET /products` remains paginated, sorted by `name ASC, id ASC`, and exposes only `id`, `name`, `image`, and `price`. Product details are read-only and must not introduce product creation, editing, deletion, inventory, purchase, or category management behavior.

**Scale/Scope**: One new read-only detail endpoint, extension of the `Product` entity with description and categories, one new `Category` entity, one product-category join mapping, one detail response DTO, one repository query for detail retrieval, centralized not-found handling, and focused controller/service/repository/DTO tests.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

Pre-design gate:

- Java and Spring Boot First: PASS. The feature is planned inside the existing Spring Boot backend and uses the current Maven dependencies.
- Layered Architecture: PASS. Controller will expose HTTP, service will own product detail retrieval and not-found decision, repository will own persistence access.
- DTO-Based API Contracts: PASS. Product detail output will use a dedicated DTO and will not expose JPA entities.
- Persistence and Validation Discipline: PASS. Product/category relationships are deliberately modeled through JPA before implementation. No request body validation is needed for this read-only endpoint; existing query validation for feature 001 remains unchanged.
- Centralized Error Handling and REST Semantics: PASS. `GET /products/{id}` is resource-oriented. Missing products will map to `404 Not Found` through centralized exception handling and the existing `ErrorResponseDTO` shape.

Post-design gate:

- Java and Spring Boot First: PASS. Research and contracts retain Spring Boot 3.3.4, Spring Web, Spring Data JPA, Hibernate, Jackson, and H2.
- Layered Architecture: PASS. Planned changes are distributed across `ProductController`, `ProductCatalogService`, `ProductRepository`, entities, DTOs, and centralized handler responsibilities.
- DTO-Based API Contracts: PASS. `ProductDetailDTO` represents the public detail response; `ProductCatalogItemDTO` remains the listing response.
- Persistence and Validation Discipline: PASS. `Product.description`, `Category`, and `Product.categories` are modeled in the domain; repository detail retrieval uses a specific query for the relationship needed by the service.
- Centralized Error Handling and REST Semantics: PASS. The detail contract defines `200 OK` for found products and `404 Not Found` with `ErrorResponseDTO` for missing products.

## Project Structure

### Documentation (this feature)

```text
specs/002-visualizar-detalhes-produto/
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
|  |- java/com/devsuperior/dscommerce/controllers/
|  |- java/com/devsuperior/dscommerce/controllers/handlers/
|  |- java/com/devsuperior/dscommerce/dto/
|  |- java/com/devsuperior/dscommerce/entities/
|  |- java/com/devsuperior/dscommerce/repositories/
|  |- java/com/devsuperior/dscommerce/services/
|  `- resources/
`- test/
   |- java/com/devsuperior/dscommerce/controllers/
   |- java/com/devsuperior/dscommerce/fixtures/
   |- java/com/devsuperior/dscommerce/repositories/
   |- java/com/devsuperior/dscommerce/services/
   `- resources/
pom.xml
README.md
```

**Structure Decision**: Continue using the single Spring Boot backend structure established by feature 001. This feature complements the existing product catalog module instead of creating a new bounded module: `ProductController` remains the product HTTP entry point, `ProductCatalogService` remains the read-only product catalog service, and `ProductRepository` remains the persistence gateway for product reads.

## Technical Decisions

- Extend `Product` with a required `description` field because the detail view must expose it, while the listing DTO must continue to omit it.
- Introduce `Category` as a JPA entity with `id` and required `name`, because category names are product detail data and category management is out of scope.
- Represent the `Product` to `Category` association as many-to-many through a join table, because the requirements allow a product to belong to multiple categories and DSCommerce categories can classify multiple products.
- Add `ProductDetailDTO` with `id`, `name`, `description`, `image`, `price`, and `categories`, where `categories` is a list of unique category names.
- Keep `ProductCatalogItemDTO` unchanged so feature 001 still exposes only listing fields.
- Add a repository method for detail lookup by product id with categories fetched for that product. Use `Optional<Product>` as the repository/service boundary for not-found handling.
- Add `ProductCatalogService.findProductDetails(Long id)` or equivalent read-only service method. It should throw a domain/service exception when the repository returns empty.
- Add `GET /products/{id}` to `ProductController`, returning `ProductDetailDTO` with `200 OK`.
- Add or reuse a service-layer not-found exception handled by `GlobalExceptionHandler` as `404 Not Found` with `ErrorResponseDTO`.
- Update seed/test data only as needed during implementation so existing feature 001 tests remain valid or are intentionally adjusted for schema-required description while preserving their behavioral assertions.

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| None | N/A | N/A |
