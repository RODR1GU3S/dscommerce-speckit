# Quickstart: Visualizar Detalhes do Produto

This guide describes how to validate the feature after implementation. It does not add implementation tasks or code.

## Prerequisites

- Java 21 available on the PATH.
- Maven available through `mvn` or the project's configured wrapper if one is later added.
- Feature 001 behavior already passing.

## Relevant Contracts and Model

- Detail API contract: `specs/002-visualizar-detalhes-produto/contracts/product-detail-openapi.yaml`
- Feature data model: `specs/002-visualizar-detalhes-produto/data-model.md`
- Existing catalog listing contract to preserve: `specs/001-consulta-catalogo-produtos/contracts/products-openapi.yaml`

## Validation Commands

Run the automated test suite:

```bash
mvn test
```

Run the application for manual HTTP validation:

```bash
mvn spring-boot:test-run -Dspring-boot.run.profiles=test
```

The manual scenarios use the test data seeded by `src/test/resources/import.sql`, so the application must run with the test classpath. Running `mvn spring-boot:run` alone does not load this seed.

## Manual Validation Scenarios

### Existing Product Details

Request:

```http
GET /products/1
```

Expected outcome:

- Response status is `200 OK`.
- Response body contains exactly the selected product's `id`, `name`, `description`, `image`, `price`, and `categories`.
- `categories` contains category names only.
- If the selected product has multiple categories, all category names are present.
- If associated category names are duplicated, each name appears once.
- No fields from another product are returned.

### Product Not Found

Request:

```http
GET /products/999999
```

Expected outcome:

- Response status is `404 Not Found`.
- Response body uses the existing error shape with `status`, `error`, `message`, and `path`.
- No product detail fields are returned.

### Catalog Listing Preserved

Request:

```http
GET /products?page=0&size=12
```

Expected outcome:

- Response status is `200 OK`.
- Pagination defaults, bounds, and metadata remain compatible with feature 001.
- Items remain ordered by `name ASC, id ASC`.
- Each catalog item still exposes only `id`, `name`, `image`, and `price`; it does not expose `description` or `categories`.

## Test Coverage Expectations

- Controller tests for `GET /products/{id}` success and not found.
- Service tests for mapping one product detail, preserving price, returning unique category names, and throwing the not-found exception for missing ids.
- Repository tests for finding a product by id with categories and returning empty when absent.
- DTO/mapping coverage through service or controller tests proving the response shape.
- Regression tests or retained existing tests proving `GET /products` behavior from feature 001 is unchanged.
