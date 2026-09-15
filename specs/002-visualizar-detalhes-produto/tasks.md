---
description: "Task list for Visualizar Detalhes do Produto"
---

# Tasks: Visualizar Detalhes do Produto

**Input**: Design documents from `/specs/002-visualizar-detalhes-produto/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/product-detail-openapi.yaml, quickstart.md

**Tests**: Included because quickstart.md explicitly defines controller, service, repository, DTO/mapping, and regression coverage expectations.

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2)
- Include exact file paths in descriptions

## Path Conventions

- Single Spring Boot project at repository root
- Production code: `src/main/java/com/devsuperior/dscommerce/`
- Test code: `src/test/java/com/devsuperior/dscommerce/`
- Test seed data: `src/test/resources/import.sql`

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Prepare the existing Spring Boot backend and test data baseline for product detail work.

- [X] T001 Review current Maven dependencies and confirm Spring Web, Spring Data JPA, Validation, H2, Spring Boot Test, MockMvc, Mockito, and JUnit 5 remain available in pom.xml
- [X] T002 [P] Review existing catalog listing tests to identify preserved feature 001 assertions in src/test/java/com/devsuperior/dscommerce/controllers/ProductControllerTest.java
- [X] T003 [P] Review existing service and repository catalog tests to identify fixture and seed assumptions in src/test/java/com/devsuperior/dscommerce/services/ProductCatalogServiceTest.java and src/test/java/com/devsuperior/dscommerce/repositories/ProductRepositoryTest.java
- [X] T004 [P] Review product fixture construction points before adding description/categories in src/test/java/com/devsuperior/dscommerce/fixtures/ProductFactory.java

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core domain, seed data, and shared error infrastructure that MUST be complete before story implementation.

**CRITICAL**: No user story work can begin until this phase is complete.

- [ ] T005 Add required description field, constructor support, and getter to Product entity in src/main/java/com/devsuperior/dscommerce/entities/Product.java
- [ ] T006 [P] Create Category JPA entity with id, name, equality by id, and validation annotations in src/main/java/com/devsuperior/dscommerce/entities/Category.java
- [ ] T007 Add many-to-many Product.categories mapping through a product-category join table in src/main/java/com/devsuperior/dscommerce/entities/Product.java
- [ ] T008 Update ProductFactory constructors and helpers for description and category-ready products in src/test/java/com/devsuperior/dscommerce/fixtures/ProductFactory.java
- [ ] T009 Update test seed products with description values and add tb_category plus tb_product_category seed data in src/test/resources/import.sql
- [ ] T010 [P] Add ProductNotFoundException service exception for missing products in src/main/java/com/devsuperior/dscommerce/services/exceptions/ProductNotFoundException.java
- [ ] T011 Review and confirm the existing GlobalExceptionHandler extension point and ErrorResponseDTO shape for future ProductNotFoundException handling, without implementing HTTP 404 behavior, in src/main/java/com/devsuperior/dscommerce/controllers/handlers/GlobalExceptionHandler.java and src/main/java/com/devsuperior/dscommerce/dto/ErrorResponseDTO.java

**Checkpoint**: Product description, category persistence, seed data, and centralized not-found handling are ready for user stories.

---

## Phase 3: User Story 1 - Visualizar detalhes completos do produto selecionado (Priority: P1) MVP

**Goal**: Consumers can select an existing catalog product and receive its id, name, price, description, image, and unique category names.

**Independent Test**: Request details for an existing seeded product and verify the response contains only that selected product's fields, includes all associated category names, and removes duplicate names.

### Tests for User Story 1

> Write these tests FIRST and ensure they fail before implementation.

- [ ] T012 [P] [US1] Add repository test for finding an existing product by id with categories loaded in src/test/java/com/devsuperior/dscommerce/repositories/ProductRepositoryTest.java
- [ ] T013 [P] [US1] Add service test for mapping product details, preserving price, and returning deterministic unique category names in src/test/java/com/devsuperior/dscommerce/services/ProductCatalogServiceTest.java
- [ ] T014 [P] [US1] Add controller test for GET /products/{id} returning 200 and ProductDetail JSON fields in src/test/java/com/devsuperior/dscommerce/controllers/ProductControllerTest.java
- [ ] T015 [P] [US1] Add regression test proving GET /products still omits description and categories in src/test/java/com/devsuperior/dscommerce/controllers/ProductControllerTest.java

### Implementation for User Story 1

- [ ] T016 [P] [US1] Create ProductDetailDTO record with entity mapping and unique sorted category names in src/main/java/com/devsuperior/dscommerce/dto/ProductDetailDTO.java
- [ ] T017 [US1] Add ProductRepository detail lookup query returning Optional<Product> with fetched categories in src/main/java/com/devsuperior/dscommerce/repositories/ProductRepository.java
- [ ] T018 [US1] Implement ProductCatalogService.findProductDetails(Long id) with read-only transaction and ProductDetailDTO mapping in src/main/java/com/devsuperior/dscommerce/services/ProductCatalogService.java
- [ ] T019 [US1] Add GET /products/{id} endpoint returning ProductDetailDTO in src/main/java/com/devsuperior/dscommerce/controllers/ProductController.java
- [ ] T020 [US1] Verify ProductCatalogItemDTO still maps only id, name, image, and price in src/main/java/com/devsuperior/dscommerce/dto/ProductCatalogItemDTO.java

**Checkpoint**: User Story 1 is fully functional and testable independently through GET /products/{id} for an existing product.

---

## Phase 4: User Story 2 - Lidar com produto nao encontrado (Priority: P2)

**Goal**: Consumers receive a clear 404 not-found response when requesting details for a product id that does not exist.

**Independent Test**: Request details for a missing product id and verify the API returns 404 with ErrorResponseDTO fields and no product detail fields.

### Tests for User Story 2

> Write these tests FIRST and ensure they fail before implementation.

- [ ] T021 [P] [US2] Add repository test proving detail lookup returns Optional.empty for absent ids in src/test/java/com/devsuperior/dscommerce/repositories/ProductRepositoryTest.java
- [ ] T022 [P] [US2] Add service test proving findProductDetails throws ProductNotFoundException for absent ids in src/test/java/com/devsuperior/dscommerce/services/ProductCatalogServiceTest.java
- [ ] T023 [P] [US2] Add controller test for GET /products/{id} returning 404 ErrorResponseDTO and no product fields in src/test/java/com/devsuperior/dscommerce/controllers/ProductControllerTest.java

### Implementation for User Story 2

- [ ] T024 [US2] Wire ProductCatalogService.findProductDetails(Long id) to throw ProductNotFoundException when ProductRepository returns empty in src/main/java/com/devsuperior/dscommerce/services/ProductCatalogService.java
- [ ] T025 [US2] Add centralized ProductNotFoundException handling in GlobalExceptionHandler returning HTTP 404 with status, error, message, and path in src/main/java/com/devsuperior/dscommerce/controllers/handlers/GlobalExceptionHandler.java

**Checkpoint**: User Story 2 is fully functional and testable independently through GET /products/{id} for a missing product.

---

## Phase 5: Polish & Cross-Cutting Concerns

**Purpose**: Final validation, regression protection, and documentation consistency.

- [ ] T026 [P] Run the full automated test suite and fix any failures without altering feature 001 behavior
- [ ] T027 [P] Validate quickstart scenarios for existing product details, product not found, and catalog listing preserved in specs/002-visualizar-detalhes-produto/quickstart.md
- [ ] T028 [P] Confirm implementation remains aligned with the OpenAPI detail contract in specs/002-visualizar-detalhes-produto/contracts/product-detail-openapi.yaml
- [ ] T029 Review constitution compliance for layered architecture, DTO boundaries, JPA persistence, and centralized error handling in .specify/memory/constitution.md

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies - can start immediately
- **Foundational (Phase 2)**: Depends on Setup completion - blocks all user stories
- **User Story 1 (Phase 3)**: Depends on Foundational completion - delivers MVP
- **User Story 2 (Phase 4)**: Depends on Foundational completion and reuses the detail service/endpoint path from US1
- **Polish (Phase 5)**: Depends on desired user stories being complete

### User Story Dependencies

- **User Story 1 (P1)**: Can start after Foundational; no dependency on User Story 2
- **User Story 2 (P2)**: Can start after Foundational, but final endpoint verification is most direct after US1 endpoint work exists

### Within Each User Story

- Tests must be written and fail before implementation
- Entity/DTO changes before service mapping
- Repository query before service detail flow
- Service flow before controller endpoint verification
- Story checkpoint must pass before moving to polish

### Parallel Opportunities

- Setup review tasks T002, T003, and T004 can run in parallel
- Foundational Category entity T006 and ProductNotFoundException T010 can run in parallel after T005 is understood
- US1 tests T012, T013, T014, and T015 can be authored in parallel because they touch different behavior slices
- US1 DTO task T016 can run in parallel with repository query task T017 after foundational entity work
- US2 tests T021, T022, and T023 can be authored in parallel
- Polish checks T026, T027, and T028 can run in parallel after implementation

---

## Parallel Example: User Story 1

```bash
Task: "T012 [P] [US1] Add repository test for finding an existing product by id with categories loaded in src/test/java/com/devsuperior/dscommerce/repositories/ProductRepositoryTest.java"
Task: "T013 [P] [US1] Add service test for mapping product details, preserving price, and returning deterministic unique category names in src/test/java/com/devsuperior/dscommerce/services/ProductCatalogServiceTest.java"
Task: "T014 [P] [US1] Add controller test for GET /products/{id} returning 200 and ProductDetail JSON fields in src/test/java/com/devsuperior/dscommerce/controllers/ProductControllerTest.java"
Task: "T015 [P] [US1] Add regression test proving GET /products still omits description and categories in src/test/java/com/devsuperior/dscommerce/controllers/ProductControllerTest.java"
```

## Parallel Example: User Story 2

```bash
Task: "T021 [P] [US2] Add repository test proving detail lookup returns Optional.empty for absent ids in src/test/java/com/devsuperior/dscommerce/repositories/ProductRepositoryTest.java"
Task: "T022 [P] [US2] Add service test proving findProductDetails throws ProductNotFoundException for absent ids in src/test/java/com/devsuperior/dscommerce/services/ProductCatalogServiceTest.java"
Task: "T023 [P] [US2] Add controller test for GET /products/{id} returning 404 ErrorResponseDTO and no product fields in src/test/java/com/devsuperior/dscommerce/controllers/ProductControllerTest.java"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup
2. Complete Phase 2: Foundational
3. Complete Phase 3: User Story 1
4. Stop and validate GET /products/{id} for an existing product plus GET /products regression behavior
5. Demo or proceed to User Story 2

### Incremental Delivery

1. Complete Setup + Foundational so entity mapping, seed data, and central error infrastructure are ready
2. Add User Story 1 so existing products can return complete details
3. Add User Story 2 so missing product requests return the central 404 shape
4. Finish with full quickstart validation and regression checks

### Parallel Team Strategy

1. One developer prepares foundational entity/seed data while another prepares not-found exception handling
2. After foundational work, repository/service/controller tests can be authored in parallel by behavior slice
3. Implementation should integrate through repository, then service, then controller to keep failures easy to diagnose

---

## Notes

- [P] tasks = different files or independently authorable behavior slices
- [US1] maps to complete detail view for an existing product
- [US2] maps to missing product handling
- Product creation, editing, deletion, inventory, purchase flow, and category management remain out of scope
- Preserve feature 001 list behavior: paginated GET /products sorted by name ASC and id ASC, exposing only id, name, image, and price
