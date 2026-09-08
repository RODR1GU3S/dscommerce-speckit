# Quickstart: Consulta do Catalogo de Produtos

## Prerequisites

- Java 21 installed.
- Maven available through `mvn` or the project Maven wrapper once implementation creates it.
- A local database profile configured for development, or H2 configured for local validation.

## Contracts and Model

- API contract: `specs/001-consulta-catalogo-produtos/contracts/products-openapi.yaml`
- Data model: `specs/001-consulta-catalogo-produtos/data-model.md`

## Validation Scenarios

### 1. Start the application

```bash
mvn spring-boot:run
```

Expected outcome: the application starts successfully and exposes the product catalog API.

### 2. Request the first page with defaults

```bash
curl "http://localhost:8080/products"
```

Expected outcome:

- Response status is `200 OK`.
- Response contains `content`, `page`, `size`, `totalElements`, and `totalPages`.
- `page` is `0`.
- `size` is `12`.
- Every product item contains only `id`, `name`, `image`, and `price`.
- Product items are ordered by `name` ascending, with `id` ascending for duplicate names.

### 3. Request a specific page and size

```bash
curl "http://localhost:8080/products?page=1&size=10"
```

Expected outcome:

- Response status is `200 OK`.
- Response metadata reports `page` as `1` and `size` as `10`.
- At most 10 product items are returned.
- Pagination metadata is coherent with the total product count.

### 4. Request a page beyond the available range

```bash
curl "http://localhost:8080/products?page=999&size=12"
```

Expected outcome:

- Response status is `200 OK`.
- `content` is an empty array.
- Pagination metadata still reports the requested page, requested size, total elements, and total pages.

### 5. Validate invalid pagination

```bash
curl -i "http://localhost:8080/products?page=-1&size=12"
curl -i "http://localhost:8080/products?page=0&size=0"
curl -i "http://localhost:8080/products?page=0&size=51"
```

Expected outcome:

- Each response status is `400 Bad Request`.
- The response body uses the centralized error format documented in the OpenAPI contract.
- Service and repository logic are not responsible for controller-bound validation errors.

## Test Commands

```bash
mvn test
```

User Story 3 targeted verification:

```bash
mvn test "-Dtest=ProductControllerTest,ProductCatalogServiceTest,ProductRepositoryTest"
```

Expected test coverage:

- Controller/API tests for default pagination, explicit pagination, invalid pagination, empty results, and response field shape.
- Service tests for page request construction and DTO mapping.
- Repository tests proving `name ASC, id ASC` ordering and page metadata with at least 50 seeded products.

## Phase 6 Verification Results

Recorded on 2026-09-05.

- T032 fail-first check: `mvn test -Dtest=ProductControllerTest` failed before pagination validation was implemented. The three new invalid-pagination tests expected `400 Bad Request` but received `200 OK`.
- Focused controller verification after T033/T034: `mvn test -Dtest=ProductControllerTest` passed with 9 tests, 0 failures.
- Full suite verification for T036: `mvn test` passed with 17 tests, 0 failures, 0 errors, 0 skipped. Existing US1-US3 controller, service, and repository behavior did not regress in the automated suite.

Manual runtime/API validation for T037:

- Started the application with `mvn spring-boot:run`.
- Also attempted `mvn spring-boot:run "-Dspring-boot.run.profiles=test" "-Dspring-boot.run.useTestClasspath=true"` to use seeded test data.
- Runtime `GET /products` checks completed successfully, but the running H2 catalog was empty (`totalElements: 0`) in both runs because the seed data is test-scoped. Product field-shape and ordering behavior with the 52 seeded products remains covered by the automated controller/repository tests.
- `curl "http://localhost:8080/products"` returned `200 OK` with `content: []`, `page: 0`, `size: 12`, `totalElements: 0`, and `totalPages: 0`.
- `curl "http://localhost:8080/products?page=1&size=10"` returned `200 OK` with `content: []`, `page: 1`, `size: 10`, `totalElements: 0`, and `totalPages: 0`.
- `curl "http://localhost:8080/products?page=999&size=12"` returned `200 OK` with `content: []`, `page: 999`, `size: 12`, `totalElements: 0`, and `totalPages: 0`.
- `curl -i "http://localhost:8080/products?page=-1&size=12"` returned `400 Bad Request` with `status`, `error`, `message`, and `path`; message was `page must be greater than or equal to 0`.
- `curl -i "http://localhost:8080/products?page=0&size=0"` returned `400 Bad Request` with `status`, `error`, `message`, and `path`; message was `size must be between 1 and 50`.
- `curl -i "http://localhost:8080/products?page=0&size=51"` returned `400 Bad Request` with `status`, `error`, `message`, and `path`; message was `size must be between 1 and 50`.
