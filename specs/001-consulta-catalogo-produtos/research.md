# Research: Consulta do Catalogo de Produtos

## Decision: Use Spring Boot 3.x with Java 21 for the catalog API

**Rationale**: The project constitution requires Java and Spring Boot as the primary backend stack. Java 21 is a current long-term-support baseline that works well with Spring Boot 3.x and keeps the feature aligned with modern Spring conventions.

**Alternatives considered**: Java 17 was considered as another Spring Boot 3.x-compatible LTS option, but Java 21 gives the project a newer baseline without adding feature complexity. Non-Spring stacks were rejected because they violate the constitution.

## Decision: Expose `GET /products` as a public paginated listing endpoint

**Rationale**: The feature is a read-only catalog consultation for visitors and customers. A resource-oriented `GET /products` endpoint matches REST semantics, supports query parameters for pagination, and avoids introducing product detail or mutation behavior outside the feature scope.

**Alternatives considered**: A separate `/catalog` route was considered, but `/products` keeps the API resource-oriented and leaves product detail endpoints extensible later. POST-based search was rejected because this feature does not require complex filter bodies.

## Decision: Use zero-based pagination with defaults `page=0` and `size=12`

**Rationale**: Spring Data `PageRequest` uses zero-based pages, so preserving that model avoids translation mistakes between controller, service, repository, and tests. A default size of 12 is a practical catalog grid/list size and is small enough to avoid loading excessive data when clients omit preferences.

**Alternatives considered**: One-based page numbers were considered for consumer readability, but they would require translation at the API boundary and could conflict with Spring Data conventions. Larger default sizes were rejected because the feature emphasizes paginated loading.

## Decision: Reject invalid pagination with `400 Bad Request`

**Rationale**: The spec allows rejecting or normalizing invalid pagination. Rejecting invalid explicit values gives consumers a clear outcome and prevents surprising behavior. Missing values are handled by documented defaults; invalid values are `page < 0`, `size < 1`, or `size > 50`.

**Alternatives considered**: Normalizing negative pages to page `0` and capping oversize requests were considered, but those can hide client errors. Returning empty lists for invalid pages was rejected because it would blur the difference between no results and invalid input.

## Decision: Sort by product name ascending and use product id ascending as a stable tie-breaker

**Rationale**: The spec requires ascending alphabetical ordering by name and consistent ordering when products share the same name. Adding `id ASC` after `name ASC` makes equivalent requests stable across pages while keeping the user-visible order by name.

**Alternatives considered**: Sorting only by name was rejected because duplicate names could move between equivalent paginated requests depending on database execution plans. Sorting by creation date was rejected because it changes the catalog order required by the spec.

## Decision: Return DTOs instead of JPA entities

**Rationale**: The constitution requires DTO-based API contracts. The catalog response only needs `id`, `name`, `image`, and `price` for listed products plus pagination metadata, so a narrow DTO prevents detail fields from leaking into the public contract.

**Alternatives considered**: Returning the `Product` entity directly was rejected because it violates the constitution and risks exposing future persistence fields. Returning a generic map was rejected because typed DTOs are clearer and easier to test.

## Decision: Use JPA repository pagination instead of loading and slicing in memory

**Rationale**: The feature must avoid loading the entire catalog. Spring Data JPA can push pagination and sorting to the database and return a `Page<Product>` with total counts and page metadata.

**Alternatives considered**: Loading all products into a service and slicing in Java was rejected because it fails the pagination intent and scales poorly. Native SQL was rejected because the simple read use case fits repository abstractions.

## Decision: Cover behavior with API, service, and repository tests

**Rationale**: The constitution recommends tests for API behavior and service rules. This feature has important boundary behavior: defaults, invalid pagination, empty pages, ordering, duplicate-name stability, and DTO field restrictions.

**Alternatives considered**: Only repository tests were rejected because they would not prove HTTP validation and response contracts. Only controller tests with mocks were rejected because they would not prove JPA pagination and ordering.
