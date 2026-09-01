---

description: "Task list for Consulta do Catalogo de Produtos"
---

# Tasks: Consulta do Catalogo de Produtos

**Input**: Design documents from `/specs/001-consulta-catalogo-produtos/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/products-openapi.yaml, quickstart.md

**Tests**: Included because the feature artifacts explicitly require API, service, and repository validation for pagination, ordering, DTO field shape, and invalid input handling.

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (US1, US2, US3)
- Include exact file paths in descriptions

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Create the Spring Boot Maven service skeleton required by all catalog work.

- [X] T001 Create Spring Boot Maven project descriptor with Java 21, Spring Web, Spring Data JPA, Bean Validation, H2, and test dependencies in pom.xml
- [X] T002 Create the Spring Boot application entry point in src/main/java/com/devsuperior/dscommerce/DscommerceApplication.java
- [X] T003 [P] Configure local datasource, JPA, and validation defaults in src/main/resources/application.properties
- [X] T004 [P] Create test profile configuration for H2 integration tests in src/test/resources/application-test.properties
- [X] T005 [P] Create README quickstart section for the product catalog endpoint in README.md

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Establish shared domain, validation, error, and test infrastructure before user stories are implemented.

**CRITICAL**: No user story work can begin until this phase is complete.

- [X] T006 Create Product JPA entity with id, name, image, and price fields plus Bean Validation annotations in src/main/java/com/devsuperior/dscommerce/entities/Product.java
- [X] T007 Create ProductRepository extending JpaRepository for Product persistence access in src/main/java/com/devsuperior/dscommerce/repositories/ProductRepository.java
- [X] T008 [P] Create ProductCatalogItemDTO response DTO with id, name, image, and price fields in src/main/java/com/devsuperior/dscommerce/dto/ProductCatalogItemDTO.java
- [X] T009 [P] Create PageResponseDTO generic page wrapper with content, page, size, totalElements, and totalPages fields in src/main/java/com/devsuperior/dscommerce/dto/PageResponseDTO.java
- [X] T010 [P] Create ErrorResponseDTO for centralized error responses in src/main/java/com/devsuperior/dscommerce/dto/ErrorResponseDTO.java
- [X] T011 Create GlobalExceptionHandler for validation errors and consistent 400 responses in src/main/java/com/devsuperior/dscommerce/controllers/handlers/GlobalExceptionHandler.java
- [X] T012 [P] Create reusable test product factory for controller, service, and repository tests in src/test/java/com/devsuperior/dscommerce/fixtures/ProductFactory.java
- [X] T013 [P] Create SQL seed data with at least 50 unordered products and duplicate names in src/test/resources/import.sql

**Checkpoint**: Foundation ready - user story implementation can now begin.

---

## Phase 3: User Story 1 - Consultar catalogo paginado (Priority: P1) MVP

**Goal**: Visitors and customers can request a paginated product catalog list without loading the full catalog.

**Independent Test**: Call `GET /products` with default and explicit pagination and verify page metadata, bounded content size, empty out-of-range pages, and HTTP 200 responses.

### Tests for User Story 1

> Write these tests first and confirm they fail before implementation.

- [X] T014 [P] [US1] Add MockMvc contract tests for GET /products default pagination, explicit pagination, and out-of-range empty pages in src/test/java/com/devsuperior/dscommerce/controllers/ProductControllerTest.java
- [X] T015 [P] [US1] Add service tests for PageRequest creation, default page 0, default size 12, and empty page mapping in src/test/java/com/devsuperior/dscommerce/services/ProductCatalogServiceTest.java
- [X] T016 [P] [US1] Add repository pagination test proving page size limits and total metadata with seeded products in src/test/java/com/devsuperior/dscommerce/repositories/ProductRepositoryTest.java

### Implementation for User Story 1

- [X] T017 [US1] Implement ProductCatalogService listProducts(page, size) pagination flow returning PageResponseDTO in src/main/java/com/devsuperior/dscommerce/services/ProductCatalogService.java
- [X] T018 [US1] Implement ProductController GET /products with default page=0 and size=12 query parameters in src/main/java/com/devsuperior/dscommerce/controllers/ProductController.java
- [X] T019 [US1] Wire ProductController to ProductCatalogService and return 200 OK page responses in src/main/java/com/devsuperior/dscommerce/controllers/ProductController.java
- [X] T020 [US1] Verify US1 behavior with `mvn test -Dtest=ProductControllerTest,ProductCatalogServiceTest,ProductRepositoryTest` documented in specs/001-consulta-catalogo-produtos/quickstart.md

**Checkpoint**: User Story 1 is fully functional and testable independently.

---

## Phase 4: User Story 2 - Visualizar dados essenciais do produto (Priority: P2)

**Goal**: Each listed product exposes only the essential catalog fields: id, name, image, and price.

**Independent Test**: Call `GET /products` for any non-empty page and verify every item has id, name, image, and price, with no product detail fields outside the catalog contract.

### Tests for User Story 2

> Write these tests first and confirm they fail before implementation.

- [X] T021 [P] [US2] Add MockMvc response-shape tests asserting product items expose only id, name, image, and price in src/test/java/com/devsuperior/dscommerce/controllers/ProductControllerTest.java
- [X] T022 [P] [US2] Add DTO mapping tests for Product to ProductCatalogItemDTO field accuracy and price preservation in src/test/java/com/devsuperior/dscommerce/services/ProductCatalogServiceTest.java

### Implementation for User Story 2

- [X] T023 [US2] Implement Product to ProductCatalogItemDTO mapping in ProductCatalogService in src/main/java/com/devsuperior/dscommerce/services/ProductCatalogService.java
- [X] T024 [US2] Ensure ProductController serializes PageResponseDTO<ProductCatalogItemDTO> without exposing Product entity fields in src/main/java/com/devsuperior/dscommerce/controllers/ProductController.java
- [X] T025 [US2] Verify US2 behavior with `mvn test -Dtest=ProductControllerTest,ProductCatalogServiceTest` documented in specs/001-consulta-catalogo-produtos/quickstart.md

**Checkpoint**: User Stories 1 and 2 both work independently.

---

## Phase 5: User Story 3 - Receber produtos ordenados por nome (Priority: P3)

**Goal**: Catalog results are returned in ascending alphabetical order by product name with stable ordering for duplicate names.

**Independent Test**: Seed unordered products across multiple pages, call `GET /products`, and verify results follow `name ASC, id ASC` consistently across pages.

### Tests for User Story 3

> Write these tests first and confirm they fail before implementation.

- [X] T026 [P] [US3] Add repository ordering tests for `name ASC, id ASC` with duplicate product names in src/test/java/com/devsuperior/dscommerce/repositories/ProductRepositoryTest.java
- [X] T027 [P] [US3] Add MockMvc ordering tests across page boundaries for GET /products in src/test/java/com/devsuperior/dscommerce/controllers/ProductControllerTest.java
- [X] T028 [P] [US3] Add service tests verifying PageRequest sort uses name ascending then id ascending in src/test/java/com/devsuperior/dscommerce/services/ProductCatalogServiceTest.java

### Implementation for User Story 3

- [X] T029 [US3] Update ProductCatalogService PageRequest construction to sort by name ascending and id ascending in src/main/java/com/devsuperior/dscommerce/services/ProductCatalogService.java
- [X] T030 [US3] Ensure ProductRepository pagination delegates sorting to Spring Data JPA without in-memory slicing in src/main/java/com/devsuperior/dscommerce/repositories/ProductRepository.java
- [X] T031 [US3] Verify US3 behavior with `mvn test -Dtest=ProductControllerTest,ProductCatalogServiceTest,ProductRepositoryTest` documented in specs/001-consulta-catalogo-produtos/quickstart.md

**Checkpoint**: All user stories are independently functional.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Validate contract compliance, documentation, and full feature behavior.

- [ ] T032 [P] Add validation tests for page < 0, size < 1, and size > 50 returning 400 Bad Request in src/test/java/com/devsuperior/dscommerce/controllers/ProductControllerTest.java
- [ ] T033 Implement @Min and @Max query parameter validation for ProductController pagination inputs in src/main/java/com/devsuperior/dscommerce/controllers/ProductController.java
- [ ] T034 Align error response body fields with the OpenAPI ErrorResponse schema in src/main/java/com/devsuperior/dscommerce/controllers/handlers/GlobalExceptionHandler.java
- [ ] T035 [P] Update endpoint documentation and validation commands in README.md
- [ ] T036 Run full test suite with `mvn test` and record any remaining test gaps in specs/001-consulta-catalogo-produtos/quickstart.md
- [ ] T037 Validate GET /products manually against all quickstart curl scenarios and record results in specs/001-consulta-catalogo-produtos/quickstart.md

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies - can start immediately
- **Foundational (Phase 2)**: Depends on Setup completion - blocks all user stories
- **User Story 1 (Phase 3)**: Depends on Foundational completion - MVP scope
- **User Story 2 (Phase 4)**: Depends on Foundational completion and can reuse US1 endpoint/service files
- **User Story 3 (Phase 5)**: Depends on Foundational completion and can be validated after US1 endpoint exists
- **Polish (Phase 6)**: Depends on all desired user stories being complete

### User Story Dependencies

- **User Story 1 (P1)**: Can start after Phase 2; no dependency on other stories
- **User Story 2 (P2)**: Can start after Phase 2, but final API verification is easiest after US1 creates GET /products
- **User Story 3 (P3)**: Can start after Phase 2, but final API verification is easiest after US1 creates GET /products

### Within Each User Story

- Tests must be written and fail before implementation
- Entity and DTO foundation before service implementation
- Service behavior before controller integration
- Repository pagination and sorting must stay database-backed through Spring Data JPA
- Story checkpoint must pass before moving to the next priority in a sequential implementation

### Parallel Opportunities

- T003, T004, and T005 can run in parallel after T001 starts
- T008, T009, T010, T012, and T013 can run in parallel after package structure exists
- US1 tests T014, T015, and T016 can be written in parallel
- US2 tests T021 and T022 can be written in parallel
- US3 tests T026, T027, and T028 can be written in parallel
- After Phase 2, different developers can work on US1, US2, and US3 test files in parallel, coordinating changes to ProductCatalogService and ProductController

---

## Parallel Example: User Story 1

```bash
# Launch US1 test-authoring tasks together:
Task: "T014 [P] [US1] Add MockMvc contract tests for GET /products default pagination, explicit pagination, and out-of-range empty pages in src/test/java/com/devsuperior/dscommerce/controllers/ProductControllerTest.java"
Task: "T015 [P] [US1] Add service tests for PageRequest creation, default page 0, default size 12, and empty page mapping in src/test/java/com/devsuperior/dscommerce/services/ProductCatalogServiceTest.java"
Task: "T016 [P] [US1] Add repository pagination test proving page size limits and total metadata with seeded products in src/test/java/com/devsuperior/dscommerce/repositories/ProductRepositoryTest.java"
```

## Parallel Example: User Story 2

```bash
# Launch US2 test-authoring tasks together:
Task: "T021 [P] [US2] Add MockMvc response-shape tests asserting product items expose only id, name, image, and price in src/test/java/com/devsuperior/dscommerce/controllers/ProductControllerTest.java"
Task: "T022 [P] [US2] Add DTO mapping tests for Product to ProductCatalogItemDTO field accuracy and price preservation in src/test/java/com/devsuperior/dscommerce/services/ProductCatalogServiceTest.java"
```

## Parallel Example: User Story 3

```bash
# Launch US3 test-authoring tasks together:
Task: "T026 [P] [US3] Add repository ordering tests for name ASC, id ASC with duplicate product names in src/test/java/com/devsuperior/dscommerce/repositories/ProductRepositoryTest.java"
Task: "T027 [P] [US3] Add MockMvc ordering tests across page boundaries for GET /products in src/test/java/com/devsuperior/dscommerce/controllers/ProductControllerTest.java"
Task: "T028 [P] [US3] Add service tests verifying PageRequest sort uses name ascending then id ascending in src/test/java/com/devsuperior/dscommerce/services/ProductCatalogServiceTest.java"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup
2. Complete Phase 2: Foundational
3. Complete Phase 3: User Story 1
4. Stop and validate `GET /products` default, explicit, and out-of-range pagination
5. Demo the MVP if the US1 tests and quickstart curl checks pass

### Incremental Delivery

1. Complete Setup + Foundational
2. Add User Story 1 and validate paginated listing
3. Add User Story 2 and validate item field shape
4. Add User Story 3 and validate database-backed ordering
5. Complete Polish validation for invalid pagination and documented quickstart coverage

### Parallel Team Strategy

1. Team completes Phase 1 and Phase 2 together
2. One developer owns controller tests, one owns service tests, and one owns repository tests
3. Coordinate implementation changes in ProductCatalogService and ProductController because multiple stories touch those files
4. Validate each story independently before merging polish tasks

---

## Notes

- [P] tasks use different files or are test-authoring tasks that can be drafted independently
- [US1], [US2], and [US3] labels map directly to spec.md user stories
- DTO contracts must not expose Product entity internals
- Invalid explicit pagination values return 400 Bad Request; missing values use defaults
- Catalog ordering is `name ASC, id ASC`
- Keep pagination database-backed through Spring Data JPA and avoid loading the full catalog in memory
