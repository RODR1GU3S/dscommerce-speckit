# Data Model: Login e Autenticação

This document separates persisted authentication data, transient authentication concepts, token claims, and HTTP DTOs. HTTP status codes and error mapping are controller/handler concerns, not entity responsibilities.

## Persisted Domain Model

### UserAccount

Minimal existing identity required for authentication.

| Field | Type | Required | Rules |
|-------|------|----------|-------|
| `id` | Long | Yes | Generated stable identifier; used as JWT subject. |
| `name` | String | Yes | Unique and non-blank. Located by exact, case-sensitive equality with no automatic normalization. |
| `passwordHash` | String | Yes | Valid BCrypt hash; never serialized, returned, or logged. |

Persistence rules:

- `name` has a unique constraint/index and is queried through `UserAccountRepository.findByName(...)`.
- Authentication performs no user creation or mutation.
- No roles, permissions, registration fields, refresh tokens, orders, or administrative relationships are modeled.

State transitions: None. Account registration, activation, profile maintenance, password recovery/change, and deletion are outside scope.

## Transient Authentication Model

### LoginCredentials

Service input derived from a validated request; not persisted.

| Field | Type | Required | Rules |
|-------|------|----------|-------|
| `name` | String | Yes | Already validated as non-blank; used exactly as submitted for case-sensitive lookup. |
| `password` | String | Yes | Already validated as non-blank; compared exactly and retained only for request processing. |

Authentication flow:

```text
validated credentials
  -> exact user lookup
  -> BCrypt comparison with stored hash, or dummy BCrypt hash if user is absent
  -> authenticated identity or generic invalid-credentials failure
  -> HS256 JWT issuance only for authenticated identity
```

Rules:

- Unknown name and wrong password produce the same authentication failure.
- The dummy comparison result never authenticates a user.
- Internal lookup, BCrypt, configuration, or signing failures do not issue a token.
- No login attempt entity or durable login-attempt record is introduced.

### AccessToken

Signed JWT created only after successful credential verification.

| Claim/Property | Type | Required | Rules |
|----------------|------|----------|-------|
| `sub` | String | Yes | Stable authenticated user id. |
| `name` | String | Yes | Exact authenticated login name; not a secret. |
| `iat` | Instant | Yes | Issuance time. |
| `exp` | Instant | Yes | `iat + security.jwt.ttl`; default TTL is `PT15M`. |
| `alg` | String | Yes | Header value `HS256`. |
| signature | Bytes | Yes | HMAC SHA-256 signature using decoded external key material of at least 32 bytes. |

Rules:

- The token is non-empty and issued only after successful authentication.
- The token contains neither submitted password nor stored hash.
- `JwtEncoder` issues it; `JwtDecoder` verifies signature and temporal claims without establishing route-protection policy.
- No refresh token, renewal record, revocation entity, role, or permission is modeled.

## HTTP DTOs

DTOs belong to the API boundary and do not add persistence behavior.

### LoginRequestDTO

| Field | JSON Type | Required | Validation |
|-------|-----------|----------|------------|
| `name` | string | Yes | `@NotBlank`; whitespace-only is invalid; value is not normalized. |
| `password` | string | Yes | `@NotBlank`; whitespace-only is invalid; write-only in the API schema. |

### TokenResponseDTO

| Field | JSON Type | Required | Rules |
|-------|-----------|----------|-------|
| `accessToken` | string | Yes | Non-empty HS256 JWT. |
| `tokenType` | string | Yes | Constant `Bearer`. |

The expiry is carried by JWT claim `exp`; no `expiresIn` field is exposed.

### ErrorResponseDTO

Reuse the centralized public error structure: `status`, `error`, `message`, and `path`.

Mapping rules outside the entity model:

- Invalid request input: generic `400` response without echoing submitted values.
- Unknown name and wrong password: identical generic `401` response.
- Internal processing failure: safe generic 5xx response distinct from `401`.
- Every failure response omits tokens, passwords, and password hashes.
