# Implementation Plan: Login e Autenticação

**Branch**: `003-login-autenticacao` | **Date**: 2026-09-23 | **Spec**: `specs/003-login-autenticacao/spec.md`

**Input**: Feature specification from `specs/003-login-autenticacao/spec.md`

## Summary

Add login exclusively by `name` and `password` through `POST /login`. A validated request DTO reaches an authentication service, which performs an exact, case-sensitive JPA lookup by name, compares the submitted password with a BCrypt hash through `PasswordEncoder.matches(...)`, and issues a non-empty HS256-signed JWT only after successful authentication. Unknown names and wrong passwords produce the same generic `401 Unauthorized`; missing, empty, or whitespace-only fields produce `400 Bad Request`; internal failures produce a generic 5xx response. No existing route becomes protected, and anonymous `GET /products` and `GET /products/{id}` behavior remains unchanged.

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: Spring Boot 3.3.4, Spring Web, Spring Data JPA/Hibernate, Bean Validation, Spring Security, Spring Security OAuth2 JOSE/JWT support, Jackson, H2

**Storage**: Relational database through JPA/Hibernate. Authentication reads an existing user record containing a unique login name and BCrypt password hash. Local/test validation uses H2; login seed data exists only under `src/test/resources`.

**Testing**: JUnit 5, Spring Boot Test, MockMvc, Mockito service tests, repository tests with in-memory H2, JWT encode/decode tests

**Target Platform**: JVM backend service exposing HTTP/JSON APIs

**Project Type**: Single Spring Boot web service

**Performance Goals**: One exact indexed lookup by name and one BCrypt comparison per login attempt. When the name is absent, perform `PasswordEncoder.matches(...)` against a configured BCrypt dummy hash to reduce observable timing differences.

**Constraints**: Never return or log passwords or hashes; never issue a token on failure; do not normalize login names; keep HTTP security stateless without introducing resource-protection rules; preserve all existing anonymous routes; exclude registration, password management, refresh tokens, orders, roles, permissions, administrative authorization, and OAuth Authorization Server flows.

**Scale/Scope**: One login endpoint, one request DTO, one token response DTO, one minimal user entity/repository lookup, one authentication service, BCrypt and HS256 JWT components/configuration, centralized failure handling, test-only seed data, and focused authentication plus catalog regression tests.

## Constitution Check

*GATE: Passed before research and re-checked after design.*

Pre-design and post-design results:

- Java and Spring Boot First: PASS. All components use Java 21 and Spring Boot conventions.
- Layered Architecture: PASS. The controller owns HTTP mapping; the service owns lookup orchestration, password verification, dummy comparison, and token issuance; the repository only performs persistence lookup.
- DTO-Based API Contracts: PASS. `LoginRequestDTO`, `TokenResponseDTO`, and `ErrorResponseDTO` form the API boundary; `UserAccount` is not serialized.
- Persistence and Validation Discipline: PASS. `UserAccount` uses JPA through a repository, and `@NotBlank` validation runs at the controller boundary.
- Centralized Error Handling and REST Semantics: PASS. Central handling produces consistent `400`, `401`, and 5xx bodies; `POST /login` is the single authentication command endpoint.

## Project Structure

### Documentation (this feature)

```text
specs/003-login-autenticacao/
|- plan.md
|- research.md
|- data-model.md
|- quickstart.md
|- contracts/
|  `- login-openapi.yaml
`- tasks.md                 # Created later; not part of this planning correction
```

### Source Code (repository root)

```text
src/
|- main/
|  |- java/com/devsuperior/dscommerce/
|  |  |- config/                    # BCrypt, HS256 encoder/decoder, and permissive stateless HTTP security
|  |  |- controllers/               # LoginController and existing ProductController
|  |  |- controllers/handlers/      # Central error mapping
|  |  |- dto/                       # Login request, token response, and error DTOs
|  |  |- entities/                  # Minimal UserAccount and existing catalog entities
|  |  |- repositories/              # UserAccountRepository and existing product repository
|  |  `- services/                  # Authentication/token service and existing catalog service
|  `- resources/
`- test/
   |- java/com/devsuperior/dscommerce/
   |  |- controllers/
   |  |- fixtures/
   |  |- repositories/
   |  `- services/
   `- resources/                    # Test profile, test-only users, and catalog seed
pom.xml
README.md
```

**Structure Decision**: Extend the existing single Spring Boot service and established layered packages. Authentication is a small vertical slice; no new deployable module or out-of-scope domain is introduced.

## Technical Decisions

- Expose only `POST /login` with JSON fields `name` and `password`.
- Resolve users with `UserAccountRepository.findByName(...)` using exact, case-sensitive comparison and no trimming, case folding, or other name normalization.
- Persist only a BCrypt hash and verify submitted passwords with `PasswordEncoder.matches(...)`.
- For an unknown name, execute `PasswordEncoder.matches(...)` against a valid precomputed BCrypt dummy hash before returning the same invalid-credentials result used for a wrong password.
- Return `TokenResponseDTO(accessToken, tokenType)` with a non-empty JWT and `tokenType` equal to `Bearer`; do not expose `expiresIn` because the approved requirements do not require it.
- Sign JWTs with HMAC SHA-256 (`HS256`). Supply at least 256 bits of key material externally as Base64 through `security.jwt.secret-base64`, mapped from environment variable `JWT_SECRET_BASE64`, and decode it before creating the signing key.
- Give JWTs `sub`, `name`, `iat`, and `exp`. Use configurable property `security.jwt.ttl` with technical default `PT15M` (15 minutes). This finite lifetime is a technical security default, not a new functional requirement or renewal policy.
- Use `JwtEncoder` for issuance and `JwtDecoder` for cryptographic verification in token tests and any internal validation needed by authentication. Do not configure a Resource Server bearer filter or use the decoder to protect application routes in this feature.
- Configure Spring Security statelessly and disable CSRF for this JSON API, but permit all existing requests. No global authorization policy is introduced; no route becomes token-required.
- Keep `GET /products` and `GET /products/{id}` explicitly covered by anonymous regression tests.
- Apply `@NotBlank` to both inputs. Invalid request bodies return `400` with a generic login-input message and never echo submitted values.
- Normalize unknown-name and wrong-password outcomes to the same service exception, `401` status, response body, and generic message.
- Map unexpected repository, BCrypt, configuration, or signing failures to a safe generic 5xx response distinct from `401`, always without a token.
- Keep refresh tokens, Authorization Server flows, Resource Server protection, roles, permissions, registration, password recovery/change, orders, and administrative authorization outside the feature.

## Maven Dependencies

- Add `org.springframework.boot:spring-boot-starter-security` for `PasswordEncoder` and the minimal stateless HTTP security configuration.
- Add `org.springframework.security:spring-security-oauth2-jose` for `JwtEncoder`, `JwtDecoder`, Nimbus JOSE/JWT support, and HS256 signing/verification.
- Do not add Spring Authorization Server.
- Do not add `spring-boot-starter-oauth2-resource-server` because this feature does not protect resources with incoming bearer tokens.

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| None | N/A | N/A |
