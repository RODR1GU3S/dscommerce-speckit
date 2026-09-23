---

description: "Tests-first implementation tasks for login and authentication"
---

# Tasks: Login e Autenticação

**Input**: Approved design documents from `/specs/003-login-autenticacao/`

**Prerequisites**: `spec.md`, `plan.md`, `research.md`, `data-model.md`, `contracts/login-openapi.yaml`, `quickstart.md`, and `.specify/memory/constitution.md`

**Tests-first rule**: Every new US1/US2 behavior follows an explicit executable RED → implementation → GREEN sequence. A RED checkpoint is valid only when the selected test command compiles and fails because the asserted behavior is absent or incorrect; an unrelated compilation or application-startup failure does not satisfy the checkpoint.

**Regression rule**: The feature 001/002 characterization suite runs before Spring Security is added, after every `SecurityFilterChain` change, at the end of every story, and in the final `mvn test` gate.

**Scope rule**: This feature adds login and token issuance only. It does not add refresh tokens, Authorization Server, Resource Server, roles, permissions, administrative authorization, registration, password recovery/change, orders, or global protection of existing routes.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: May run concurrently only when it edits a different file and depends on no incomplete task or state produced by another concurrent task.
- **[Story]**: User-story traceability label (`US1`, `US2`, or `US3`) used in story phases.
- Test execution tasks name the exact test source files selected by Maven; no task in this document is executed by generating this checklist.

## Phase 1: Pre-Security Public Baseline (US3 Characterization)

**Purpose**: Capture and prove the anonymous feature 001/002 behavior before adding Spring Security dependencies or changing HTTP policy.

**Independent Test**: With the pre-security application, anonymous requests to `GET /products` and `GET /products/{id}` preserve pagination, validation, success, and not-found behavior.

- [ ] T001 [P] [US3] Add full-context anonymous characterization coverage for `GET /products`, including default/explicit pagination, ordering, out-of-range pages, and validation errors, in `src/test/java/com/devsuperior/dscommerce/controllers/PublicCatalogSecurityIntegrationTest.java`
- [ ] T002 [P] [US3] Add full-context anonymous characterization coverage for existing and missing `GET /products/{id}` responses in `src/test/java/com/devsuperior/dscommerce/controllers/PublicProductDetailSecurityIntegrationTest.java`
- [ ] T003 [US3] Execute `mvn -Dtest=PublicCatalogSecurityIntegrationTest,PublicProductDetailSecurityIntegrationTest,ProductControllerTest test` and confirm the pre-security anonymous baseline is GREEN using `src/test/java/com/devsuperior/dscommerce/controllers/PublicCatalogSecurityIntegrationTest.java`, `src/test/java/com/devsuperior/dscommerce/controllers/PublicProductDetailSecurityIntegrationTest.java`, and `src/test/java/com/devsuperior/dscommerce/controllers/ProductControllerTest.java`

**Checkpoint**: The public baseline is recorded and GREEN before any Spring Security dependency or `SecurityFilterChain` exists.

---

## Phase 2: Test Infrastructure Setup

**Purpose**: Add dependencies and externalized properties required to compile executable tests without implementing authentication, JWT, BCrypt, or HTTP-security behavior.

- [ ] T004 Add Spring Security and Spring Security OAuth2 JOSE dependencies, without Authorization Server or Resource Server starters, in `pom.xml`
- [ ] T005 [P] Add externally supplied `security.jwt.secret-base64=${JWT_SECRET_BASE64}` and default `security.jwt.ttl=PT15M` properties without committing a secret in `src/main/resources/application.properties`
- [ ] T006 [P] Add a disposable 32-byte Base64 JWT key and `security.jwt.ttl=PT15M` only for automated tests in `src/test/resources/application-test.properties`

**Checkpoint**: Tests can compile against Spring Security/JOSE APIs, but no production security behavior has been implemented.

---

## Phase 3: Foundational Security and JWT Configuration (Tests First)

**Purpose**: Introduce the permissive HTTP filter chain, validated JWT configuration, and BCrypt encoder only after executable tests describe each behavior.

### HTTP Policy RED → Implementation → GREEN

- [ ] T007 Execute `mvn -Dtest=PublicCatalogSecurityIntegrationTest,PublicProductDetailSecurityIntegrationTest test` after T004 and confirm a valid RED caused by Spring Security's default authentication requirement, not compilation or startup failure, using `src/test/java/com/devsuperior/dscommerce/controllers/PublicCatalogSecurityIntegrationTest.java` and `src/test/java/com/devsuperior/dscommerce/controllers/PublicProductDetailSecurityIntegrationTest.java`
- [ ] T008 Implement only the stateless, CSRF-disabled, permit-all `SecurityFilterChain` with no bearer-token or Resource Server configuration in `src/main/java/com/devsuperior/dscommerce/config/SecurityConfig.java`
- [ ] T009 Execute `mvn -Dtest=PublicCatalogSecurityIntegrationTest,PublicProductDetailSecurityIntegrationTest,ProductControllerTest test` and confirm the post-`SecurityFilterChain` public regression gate is GREEN using `src/test/java/com/devsuperior/dscommerce/controllers/PublicCatalogSecurityIntegrationTest.java`, `src/test/java/com/devsuperior/dscommerce/controllers/PublicProductDetailSecurityIntegrationTest.java`, and `src/test/java/com/devsuperior/dscommerce/controllers/ProductControllerTest.java`

### JWT Configuration RED → Implementation → GREEN

- [ ] T010 Add executable application-context tests for invalid Base64, decoded keys shorter than 256 bits, zero TTL, negative TTL, and a valid HS256 encoder/decoder configuration without importing not-yet-created project configuration classes in `src/test/java/com/devsuperior/dscommerce/config/JwtConfigurationTest.java`
- [ ] T011 Execute `mvn -Dtest=JwtConfigurationTest test` and confirm every invalid-configuration case and the missing valid HS256 bean behavior fail for the expected assertions while the test source compiles in `src/test/java/com/devsuperior/dscommerce/config/JwtConfigurationTest.java`
- [ ] T012 Implement Base64 decoding, minimum 32-byte key validation, and positive finite TTL binding in `src/main/java/com/devsuperior/dscommerce/config/JwtProperties.java`
- [ ] T013 Implement HS256 `JwtEncoder` and `JwtDecoder` beans from the validated symmetric key in `src/main/java/com/devsuperior/dscommerce/config/JwtConfig.java`
- [ ] T014 Execute `mvn -Dtest=JwtConfigurationTest test` and confirm all invalid and valid JWT configuration cases are GREEN in `src/test/java/com/devsuperior/dscommerce/config/JwtConfigurationTest.java`

### BCrypt Encoder RED → Implementation → GREEN

- [ ] T015 Add an executable context test that requires a BCrypt-backed `PasswordEncoder` bean and proves `matches(raw, encoded)` behavior in `src/test/java/com/devsuperior/dscommerce/config/PasswordEncoderConfigurationTest.java`
- [ ] T016 Execute `mvn -Dtest=PasswordEncoderConfigurationTest test` and confirm valid RED because the BCrypt `PasswordEncoder` bean is absent, not because of compilation or unrelated startup failure, in `src/test/java/com/devsuperior/dscommerce/config/PasswordEncoderConfigurationTest.java`
- [ ] T017 Add the BCrypt `PasswordEncoder` bean without changing the already validated permit-all HTTP policy in `src/main/java/com/devsuperior/dscommerce/config/SecurityConfig.java`
- [ ] T018 Execute `mvn -Dtest=PasswordEncoderConfigurationTest,PublicCatalogSecurityIntegrationTest,PublicProductDetailSecurityIntegrationTest,ProductControllerTest test` and confirm BCrypt GREEN plus the mandatory post-`SecurityFilterChain` regression gate in `src/test/java/com/devsuperior/dscommerce/config/PasswordEncoderConfigurationTest.java`, `src/test/java/com/devsuperior/dscommerce/controllers/PublicCatalogSecurityIntegrationTest.java`, `src/test/java/com/devsuperior/dscommerce/controllers/PublicProductDetailSecurityIntegrationTest.java`, and `src/test/java/com/devsuperior/dscommerce/controllers/ProductControllerTest.java`

**Checkpoint**: JWT key/TTL validation, HS256 beans, BCrypt matching, and anonymous HTTP behavior are all GREEN before story implementation.

---

## Phase 4: User Story 1 - Autenticar com credenciais válidas (Priority: P1) 🎯 MVP

**Goal**: Accept an exact existing name and correct password and return only a non-empty, verifiable HS256 JWT plus `tokenType: Bearer`, without exposing passwords or secrets.

**Independent Test**: Submit `demo` / `secret123` once to `POST /login`; verify HTTP 200, the exact response fields, and a JWT with valid HS256 signature and `sub`, `name`, `iat`, and configurable finite `exp` claims.

### Repository RED → Implementation → GREEN

- [ ] T019 [US1] Add an executable black-box JPA test that discovers the repository bean through the Spring context and specifies unique, exact, case-sensitive `name` lookup without compile-time imports of not-yet-created project types in `src/test/java/com/devsuperior/dscommerce/repositories/UserAccountRepositoryTest.java`
- [ ] T020 [US1] Execute `mvn -Dtest=UserAccountRepositoryTest test` and confirm valid RED because the required account persistence/repository behavior is absent, not because the test fails to compile, in `src/test/java/com/devsuperior/dscommerce/repositories/UserAccountRepositoryTest.java`
- [ ] T021 [US1] Create the `UserAccount` JPA entity with generated id, unique non-blank exact-case name, and a non-serialized BCrypt `passwordHash` in `src/main/java/com/devsuperior/dscommerce/entities/UserAccount.java`
- [ ] T022 [US1] Create `UserAccountRepository.findByName(...)` returning `Optional<UserAccount>` with exact, case-sensitive lookup semantics in `src/main/java/com/devsuperior/dscommerce/repositories/UserAccountRepository.java`
- [ ] T023 [US1] Add only a test account `demo` with a BCrypt hash for `secret123`, never a plaintext stored password or production account, in `src/test/resources/import.sql`
- [ ] T024 [US1] Execute `mvn -Dtest=UserAccountRepositoryTest test` and confirm unique and exact case-sensitive lookup GREEN in `src/test/java/com/devsuperior/dscommerce/repositories/UserAccountRepositoryTest.java`

### Token Issuance RED → Implementation → GREEN

- [ ] T025 [US1] Add an executable Spring black-box token-service test that discovers the service bean at runtime and specifies HS256, `sub`, `name`, `iat`, `exp`, configured TTL, non-empty output, and absence of password/hash/secret claims in `src/test/java/com/devsuperior/dscommerce/services/JwtTokenServiceTest.java`
- [ ] T026 [US1] Execute `mvn -Dtest=JwtTokenServiceTest test` and confirm valid RED because token issuance behavior is absent while JWT configuration remains GREEN in `src/test/java/com/devsuperior/dscommerce/services/JwtTokenServiceTest.java`
- [ ] T027 [US1] Create immutable `TokenResponseDTO` exposing only `accessToken` and `tokenType` in `src/main/java/com/devsuperior/dscommerce/dto/TokenResponseDTO.java`
- [ ] T028 [US1] Implement HS256 token issuance with `sub`, `name`, `iat`, and `exp = iat + configured TTL`, returning `Bearer` and no secret data in `src/main/java/com/devsuperior/dscommerce/services/JwtTokenService.java`
- [ ] T029 [US1] Execute `mvn -Dtest=JwtTokenServiceTest test` and confirm token issuance and claim verification GREEN in `src/test/java/com/devsuperior/dscommerce/services/JwtTokenServiceTest.java`

### BCrypt Authentication RED → Implementation → GREEN

- [ ] T030 [US1] Create only a compileable authentication-service seam whose login method has no successful behavior yet and throws an unsupported-operation failure in `src/main/java/com/devsuperior/dscommerce/services/AuthenticationService.java`
- [ ] T031 [US1] Add service tests that verify one exact repository lookup, `PasswordEncoder.matches(rawPassword, storedHash)`, no raw-password persistence, and token issuance only after a successful BCrypt comparison in `src/test/java/com/devsuperior/dscommerce/services/AuthenticationServiceTest.java`
- [ ] T032 [US1] Execute `mvn -Dtest=AuthenticationServiceTest test` and confirm valid behavioral RED from the unsupported authentication seam, not compilation failure, in `src/test/java/com/devsuperior/dscommerce/services/AuthenticationServiceTest.java`
- [ ] T033 [US1] Implement the successful authentication path with one exact lookup, `PasswordEncoder.matches(...)`, and token issuance only after a valid BCrypt result in `src/main/java/com/devsuperior/dscommerce/services/AuthenticationService.java`
- [ ] T034 [US1] Execute `mvn -Dtest=AuthenticationServiceTest test` and confirm successful BCrypt authentication GREEN in `src/test/java/com/devsuperior/dscommerce/services/AuthenticationServiceTest.java`

### Login HTTP Contract RED → Implementation → GREEN

- [ ] T035 [P] [US1] Create a compileable immutable `LoginRequestDTO` seam with `name` and `password` fields but without validation behavior yet in `src/main/java/com/devsuperior/dscommerce/dto/LoginRequestDTO.java`
- [ ] T036 [P] [US1] Create a compileable `LoginController` seam without a mapped `/login` endpoint yet in `src/main/java/com/devsuperior/dscommerce/controllers/LoginController.java`
- [ ] T037 [US1] Add `POST /login` MockMvc tests for exact `name` and `password` input, HTTP 200, exactly `accessToken` and `tokenType: Bearer`, one-request completion, and no password/hash/secret fields in `src/test/java/com/devsuperior/dscommerce/controllers/LoginControllerTest.java`
- [ ] T038 [US1] Execute `mvn -Dtest=LoginControllerTest test` and confirm valid RED because `POST /login` is not mapped, not because of compilation or unrelated setup, in `src/test/java/com/devsuperior/dscommerce/controllers/LoginControllerTest.java`
- [ ] T039 [US1] Implement anonymous `POST /login` mapping and DTO-only success response handling without adding missing/empty/blank-field validation yet in `src/main/java/com/devsuperior/dscommerce/controllers/LoginController.java`
- [ ] T040 [US1] Execute `mvn -Dtest=LoginControllerTest test` and confirm the successful HTTP 200 contract GREEN in `src/test/java/com/devsuperior/dscommerce/controllers/LoginControllerTest.java`
- [ ] T041 [US1] Execute `mvn -Dtest=JwtConfigurationTest,PasswordEncoderConfigurationTest,UserAccountRepositoryTest,JwtTokenServiceTest,AuthenticationServiceTest,LoginControllerTest,PublicCatalogSecurityIntegrationTest,PublicProductDetailSecurityIntegrationTest,ProductControllerTest test` and confirm the US1 plus feature 001/002 regression gate is GREEN using the corresponding files under `src/test/java/com/devsuperior/dscommerce/`

**Checkpoint**: US1 is independently GREEN, and catalog listing/product detail remain anonymous and unchanged.

---

## Phase 5: User Story 2 - Rejeitar credenciais inválidas (Priority: P2)

**Goal**: Produce safe, uniform failures for invalid credentials and input, use dummy BCrypt work for unknown names, translate internal authentication failures deliberately, and expose no token or sensitive data.

**Independent Test**: Wrong password and unknown name produce identical generic 401 bodies; missing/empty/blank/unknown JSON input produces generic 400; repository, BCrypt, and token failures produce generic 5xx; every failure omits tokens, credentials, hashes, secrets, and stack traces.

### Compileable Exception Seams

- [ ] T042 [P] [US2] Create the compileable generic invalid-credentials exception type without wiring it into authentication yet in `src/main/java/com/devsuperior/dscommerce/services/exceptions/InvalidCredentialsException.java`
- [ ] T043 [P] [US2] Create the compileable authentication-processing exception type without wiring it into authentication yet in `src/main/java/com/devsuperior/dscommerce/services/exceptions/AuthenticationProcessingException.java`

### Handler Baseline and US2 RED

- [ ] T044 [P] [US2] Extend service tests for unknown name, wrong password, identical invalid-credential exceptions, no token, and verification that unknown names invoke `PasswordEncoder.matches` with a valid precomputed dummy BCrypt hash in `src/test/java/com/devsuperior/dscommerce/services/AuthenticationServiceTest.java`
- [ ] T045 [P] [US2] Extend login HTTP tests for unknown name and wrong password 401 equality; missing/null/empty/whitespace-only `name` and `password`; unknown JSON properties; no token; and no password/hash/secret disclosure in `src/test/java/com/devsuperior/dscommerce/controllers/LoginControllerTest.java`
- [ ] T046 [P] [US2] Add service tests that force repository, `PasswordEncoder`, and token-generation failures and require deliberate conversion to `AuthenticationProcessingException` without sensitive messages in `src/test/java/com/devsuperior/dscommerce/services/AuthenticationInternalFailureTest.java`
- [ ] T047 [P] [US2] Add handler characterization tests for all existing product not-found and validation responses plus new login 400/401/5xx response shapes in `src/test/java/com/devsuperior/dscommerce/controllers/GlobalExceptionHandlerRegressionTest.java`
- [ ] T048 [P] [US2] Add a security characterization test that captures logs for valid login, unknown name, wrong password, and internal failure and rejects raw passwords, stored BCrypt hashes, JWT secrets, decoded key material, tokens, and equivalent sensitive values in `src/test/java/com/devsuperior/dscommerce/services/AuthenticationSensitiveLoggingTest.java`
- [ ] T049 [US2] Execute only the existing product-error cases in `GlobalExceptionHandlerRegressionTest` and confirm the pre-handler-change baseline is GREEN in `src/test/java/com/devsuperior/dscommerce/controllers/GlobalExceptionHandlerRegressionTest.java`
- [ ] T050 [US2] Execute `mvn -Dtest=AuthenticationSensitiveLoggingTest test`; if GREEN, record it as the protected security characterization baseline, and if RED, record the exact sensitive logging source before remediation in `src/test/java/com/devsuperior/dscommerce/services/AuthenticationSensitiveLoggingTest.java`
- [ ] T051 [US2] If and only if T050 is RED, remove or sanitize the identified sensitive logging without adding replacement secret-bearing logs in `src/main/java/com/devsuperior/dscommerce/services/AuthenticationService.java`, `src/main/java/com/devsuperior/dscommerce/services/JwtTokenService.java`, `src/main/java/com/devsuperior/dscommerce/config/JwtConfig.java`, and `src/main/java/com/devsuperior/dscommerce/controllers/handlers/GlobalExceptionHandler.java` as applicable
- [ ] T052 [US2] If T051 was required, execute `mvn -Dtest=AuthenticationSensitiveLoggingTest test` and confirm GREEN; otherwise confirm the T050 GREEN characterization remains the accepted baseline in `src/test/java/com/devsuperior/dscommerce/services/AuthenticationSensitiveLoggingTest.java`
- [ ] T053 [US2] Execute `mvn -Dtest=AuthenticationServiceTest,AuthenticationInternalFailureTest,LoginControllerTest,GlobalExceptionHandlerRegressionTest test` and confirm executable RED for dummy BCrypt, uniform 401, missing/null/empty/whitespace-only input 400, unknown-property 400, deliberate internal-failure conversion, and generic 5xx while recording that confidentiality assertions are coupled to failing response-contract cases rather than relying on compilation failure, using the corresponding files under `src/test/java/com/devsuperior/dscommerce/`

### US2 Implementation

- [ ] T054 [P] [US2] Implement wrong-password and unknown-name normalization plus a precomputed valid dummy BCrypt `PasswordEncoder.matches(...)` call for absent users in `src/main/java/com/devsuperior/dscommerce/services/AuthenticationService.java`
- [ ] T055 [US2] Extend authentication to catch unexpected repository, BCrypt/`PasswordEncoder`, and JWT/token-generation failures and convert them deliberately to `AuthenticationProcessingException` without converting expected invalid credentials in `src/main/java/com/devsuperior/dscommerce/services/AuthenticationService.java`
- [ ] T056 [P] [US2] Add `@NotBlank` validation to both `name` and `password`, covering missing, null, empty, and whitespace-only values without trimming or normalization, in `src/main/java/com/devsuperior/dscommerce/dto/LoginRequestDTO.java`
- [ ] T057 [P] [US2] Configure login request deserialization to reject unknown JSON properties so the OpenAPI `additionalProperties: false` contract returns 400 in `src/main/java/com/devsuperior/dscommerce/config/JacksonConfig.java`
- [ ] T058 [P] [US2] Extend centralized handling with login-specific generic 400, identical 401, and safe generic 5xx mappings while preserving every existing handler and response contract in `src/main/java/com/devsuperior/dscommerce/controllers/handlers/GlobalExceptionHandler.java`

### US2 GREEN and Regression Gate

- [ ] T059 [US2] Execute `mvn -Dtest=AuthenticationServiceTest,AuthenticationInternalFailureTest,AuthenticationSensitiveLoggingTest,LoginControllerTest,GlobalExceptionHandlerRegressionTest,PublicCatalogSecurityIntegrationTest,PublicProductDetailSecurityIntegrationTest,ProductControllerTest test` and confirm input validation, all remaining US2 behaviors, sensitive-logging regression, existing handlers, and anonymous routes are GREEN using the corresponding files under `src/test/java/com/devsuperior/dscommerce/`

**Checkpoint**: US2 is independently GREEN, internal failures are intentionally translated, unknown JSON is rejected, and all existing error/public-route behavior remains unchanged.

---

## Phase 6: User Story 3 - Confirmar consultas públicas existentes (Priority: P3)

**Goal**: Close the story with the same characterization suite captured before Spring Security and prove that login attempts do not alter public catalog behavior.

**Independent Test**: Without an Authorization header, all baseline feature 001/002 scenarios still produce the same status codes and payload shapes after US1 and US2.

- [ ] T060 [US3] Execute `mvn -Dtest=PublicCatalogSecurityIntegrationTest,PublicProductDetailSecurityIntegrationTest,ProductControllerTest,LoginControllerTest test` without an Authorization header on product requests and confirm the final US3 regression gate is GREEN using the corresponding files under `src/test/java/com/devsuperior/dscommerce/controllers/`

**Checkpoint**: The exact pre-security characterization suite remains GREEN; no additional `SecurityFilterChain` change or route-protection task exists in US3.

---

## Phase 7: Polish and Final Validation

**Purpose**: Document the completed behavior, execute the complete suite, and retain evidence separately from implementation.

- [ ] T061 [P] Document external JWT configuration, test-profile execution, successful/error login examples, and the intentionally public route policy in `README.md`
- [ ] T062 Execute the final mandatory `mvn test` gate and confirm authentication plus every existing feature 001/002 test is GREEN using `pom.xml`
- [ ] T063 Record the verified final test command/results and reconcile the manual validation steps without embedding production secrets in `specs/003-login-autenticacao/quickstart.md`

---

## Dependencies and Execution Order

### Phase Dependencies

- **Phase 1** has no dependencies and must finish GREEN before T004 adds Spring Security.
- **Phase 2** depends on the baseline. T005 and T006 may run concurrently only after T004 because both are independent property-file edits.
- **Phase 3** depends on Phase 2. T007 must observe the expected default-security RED before T008; every `SecurityConfig` edit is followed by a public regression gate. JWT and BCrypt each have separate RED and GREEN checkpoints.
- **US1** depends on Phase 3. Each behavioral slice completes its own RED → implementation → GREEN sequence before the next dependent slice, then T041 gates US1 and features 001/002 together.
- **US2** depends on US1. T042 and T043 establish only compileable exception seams; T044-T048 specify behavior; T049 preserves the old handler baseline; T050-T052 establish or restore the sensitive-logging characterization; T053 is the behavioral RED; T054-T058 implement; T059 is GREEN plus regression.
- **US3 closure** depends on US1 and US2 and reruns the original baseline without another security-policy change.
- **Final validation** depends on every desired story. T062 runs the complete suite before T063 records results.

### User Story Dependency Graph

```text
US3 pre-security baseline
        -> test infrastructure
        -> HTTP/JWT/BCrypt foundational RED-GREEN
        -> US1 RED-GREEN -> US1 regression gate
        -> US2 RED-GREEN -> US2 regression gate
        -> US3 final regression gate
        -> mvn test final gate
```

### Exact US1 RED → Implementation → GREEN

```text
Repository: T019 -> T020 RED -> T021-T023 -> T024 GREEN
JWT issuance: T025 -> T026 RED -> T027-T028 -> T029 GREEN
BCrypt authentication: T030-T031 -> T032 RED -> T033 -> T034 GREEN
POST /login success contract: T035-T037 -> T038 RED -> T039 -> T040 GREEN
US1 + public regression gate: T041 GREEN
```

### Exact US2 RED → Implementation → GREEN

```text
Compileable exception seams: T042-T043
Behavior tests: T044-T048
Existing-handler baseline: T049 GREEN
Sensitive-logging characterization: T050 GREEN, or T050 RED -> T051 remediation -> T052 GREEN
US2 behavioral checkpoint, including missing/empty/blank input: T053 RED
Invalid credentials/dummy BCrypt: T054
Repository/BCrypt/JWT internal-failure conversion: T055
Missing/null/empty/whitespace-only input validation: T056
Unknown JSON rejection: T057
Central 400/401/5xx mapping with existing-handler preservation: T058
Same US2 tests + logging + existing handlers + public routes: T059 GREEN
```

### Regression Gates

- **Before HTTP change**: T003.
- **After initial `SecurityFilterChain` creation**: T009.
- **After adding `PasswordEncoder` to `SecurityConfig`**: T018.
- **End of US1**: T041.
- **End of US2**: T059.
- **End of US3**: T060.
- **Final full suite**: T062 with `mvn test`.

## Parallel Opportunities

- T001 and T002 create independent baseline tests; T003 waits for both.
- T005 and T006 edit separate property files after T004.
- T035 and T036 create independent compile-only HTTP seams before T037.
- T042 and T043 create independent exception types before US2 tests.
- T044-T048 edit five different test files after both exception seams exist; T049/T050 wait for them.
- After T053 RED, T054, T056, T057, and T058 edit separate production files and depend on no state from one another; T055 waits for T054 because both edit `AuthenticationService.java`.
- T061 documentation can proceed independently after the story behavior stabilizes; T062 remains the authoritative final gate.

## Implementation Strategy

### MVP First

1. Capture the anonymous public baseline in T001-T003.
2. Complete foundational HTTP/JWT/BCrypt RED-GREEN cycles in T004-T018.
3. Complete every US1 RED-GREEN slice in T019-T040.
4. Stop at T041 and approve the independently working MVP only if US1 and public regressions are GREEN.

### Incremental Delivery

1. Add safe invalid-credential, input-validation, logging-regression, and internal-failure behavior through T042-T059.
2. Close US3 with the unchanged original characterization suite at T060.
3. Document behavior, execute `mvn test`, and record results through T061-T063.

### Commit Guidance

- Create logical commits at completed RED-GREEN checkpoints or coherent task groups.
- A separate commit is not required for every individual Txxx.
- Never commit a checkpoint whose GREEN or mandatory regression gate has not passed.

## Notes

- A test-writing task is not a RED checkpoint; the following explicit Maven execution task establishes RED.
- Compile-only seams exist solely to make behavioral RED tests executable and must not contain the target behavior before the test.
- The only seeded login account belongs in `src/test/resources/import.sql`, and its stored password value must be a BCrypt hash.
- `src/main/resources` must contain neither test accounts nor signing secrets.
- Raw passwords and password hashes must never be logged, serialized, included in tokens, or copied into exception messages.
- `GET /products` and `GET /products/{id}` remain public throughout the feature.
- No task adds excluded authorization, account-management, order, or token-renewal behavior.
