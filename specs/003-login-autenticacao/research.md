# Research: Login e Autenticação

## Authentication Endpoint

Decision: Add `POST /login` accepting JSON with exactly `name` and `password`.

Rationale: The specification defines name—not email—as the credential and requires a token result. A dedicated JSON endpoint is sufficient for this first-party login feature.

Alternatives considered: HTTP Basic was rejected because the required result is a token. OAuth password grant and a full Authorization Server were rejected as obsolete or disproportionate to the login-only scope.

## User Lookup Semantics

Decision: Use `UserAccountRepository.findByName(...)` for one exact, case-sensitive lookup. Do not trim, lowercase, uppercase, or otherwise normalize `name`.

Rationale: Exact comparison is deterministic and does not invent identity equivalence absent from the approved specification. A unique database column/index prevents ambiguous results.

Alternatives considered: Case-insensitive lookup and automatic normalization were rejected because they would change credential semantics. Email lookup was rejected by FR-001.

## Password Storage and Verification

Decision: Store only BCrypt hashes and verify with Spring Security `PasswordEncoder.matches(rawPassword, storedHash)`.

Rationale: BCrypt is adaptive, salted, and supported by reviewed Spring Security primitives. The raw password remains request-scoped and is never returned or logged.

Alternatives considered: Plaintext, reversible encryption, custom cryptography, and fast unsalted hashes were rejected as unsafe. Argon2 is viable but unnecessary for this bounded feature.

## Unknown-User Timing Mitigation

Decision: When `findByName(...)` returns empty, call `PasswordEncoder.matches(submittedPassword, dummyBcryptHash)` with a valid precomputed BCrypt dummy hash, discard the result, and then return the same invalid-credentials exception used for password mismatch.

Rationale: Performing comparable BCrypt work reduces the observable timing difference between unknown users and wrong passwords while preserving identical HTTP status, message, and body.

Alternatives considered: Returning immediately for unknown users was rejected because it creates a stronger timing signal. Creating a fresh dummy hash per request was rejected because it adds unnecessary and inconsistent cost.

## JWT Format, Algorithm, and Key

Decision: Issue a compact JWT signed with HMAC SHA-256 (`HS256`) using Spring Security JOSE. Include `sub`, `name`, `iat`, and `exp`.

Rationale: HS256 provides integrity with a simple symmetric deployment model suitable for one service. JWT remains stateless and verifiable without token persistence.

Alternatives considered: Opaque tokens require storage or introspection. RSA adds key-pair management not justified for a single issuer/verifier. Unsigned tokens are unacceptable.

Decision: Supply at least 256 bits of random key material externally in Base64 through property `security.jwt.secret-base64`, conventionally populated by environment variable `JWT_SECRET_BASE64`. Decode Base64 before constructing the HMAC key and fail application startup if decoding fails or produces fewer than 32 bytes.

Rationale: External configuration avoids committed secrets; an explicit encoding avoids ambiguity between characters and key bytes; startup validation prevents weak or malformed keys.

Alternatives considered: Hard-coded keys, raw passphrases, and implicit text encodings were rejected because they weaken secret handling and make effective key strength unclear.

## JWT Lifetime and Response

Decision: Use property `security.jwt.ttl` with default `PT15M`. Set `exp = iat + ttl`, require a positive finite duration, and return only `accessToken` and `tokenType: Bearer` in the HTTP response.

Rationale: Fifteen minutes is a conservative technical default and remains externally configurable. Finite expiry limits token exposure. The duration is a technical security decision, not a new functional requirement; the approved specification neither requires `expiresIn` nor defines renewal.

Alternatives considered: Non-expiring tokens were rejected as unsafe. Returning `expiresIn` was rejected as unnecessary for the approved contract. Refresh tokens were rejected by FR-012.

## Encoder and Decoder Scope

Decision: Use `JwtEncoder` to issue HS256 tokens and `JwtDecoder` to verify signature and temporal claims in token tests and any internal token validation. Do not configure an OAuth2 Resource Server bearer filter and do not make route protection part of this feature.

Rationale: The decoder proves the issued token is cryptographically valid and recognized by the application's own configuration without expanding the scope into resource authorization.

Alternatives considered: Authorization Server and Resource Server flows were rejected because no protected resource or authorization policy is defined by this feature.

## Input Validation and Errors

Decision: Apply `@NotBlank` to `name` and `password`. Missing, null, empty, and whitespace-only values return the same generic `400 Bad Request` login-input message without echoing values.

Decision: Unknown names and password mismatches return the same generic `401 Unauthorized` response and no token. Unexpected repository, BCrypt, key, or signing failures return a safe generic 5xx response and no token.

Rationale: These decisions satisfy validation, anti-enumeration, and internal-failure separation requirements while keeping errors centralized.

## HTTP Security and Public Regression Safety

Decision: Use a stateless Spring Security filter chain with CSRF disabled for the JSON API and permit all requests. Do not introduce `deny unspecified routes`, bearer-token enforcement, or any other global authorization policy.

Rationale: Spring Security is needed for password and JWT primitives, but the approved feature defines no protected resources. Permitting existing requests prevents the dependency's secure-by-default behavior from regressing features 001 and 002.

Decision: Retain all feature 001/002 tests and add explicit requests without an Authorization header for `GET /products` and `GET /products/{id}`.

## Test Data and Validation

Decision: Seed the known login user only in `src/test/resources/import.sql` (or an equivalent test-only SQL resource) and configure the JWT key only in the test profile or test environment. Do not create production accounts or production secrets.

Rationale: Accounts already exist by specification assumption. A test-only BCrypt-backed account makes automated and manual test-classpath validation reproducible without adding registration behavior.

## Testing Strategy

Decision: Cover exact/case-sensitive repository lookup, successful BCrypt verification and JWT decoding, wrong password, unknown name with dummy comparison, blank validation, identical `401` bodies, internal failure, absence of sensitive output/logging, and anonymous product-route regression.
