# Quickstart: Login e Autenticação

This Windows/PowerShell guide validates the feature after implementation using only test-classpath data. It does not create a production account.

## Prerequisites

- Java 21 and Maven available on `PATH`.
- Test profile configuration in `src/test/resources/application-test.properties`.
- A test-only BCrypt-backed user seeded by `src/test/resources/import.sql` with credentials `demo` / `secret123`.
- The test profile supplies disposable JWT configuration and uses `security.jwt.ttl=PT15M`.

The `demo` / `secret123` account belongs exclusively to `src/test/resources`; it is not a production account. `src/main/resources` does not create accounts or contain signing secrets.

Relevant references:

- API: `specs/003-login-autenticacao/contracts/login-openapi.yaml`
- Model: `specs/003-login-autenticacao/data-model.md`
- Public APIs to preserve: `specs/001-consulta-catalogo-produtos/contracts/products-openapi.yaml` and `specs/002-visualizar-detalhes-produto/contracts/product-detail-openapi.yaml`

## Automated Validation

```powershell
mvn test
```

The final feature gate executed this command and produced the following verified result:

```text
Tests run: 77
Failures: 0
Errors: 0
Skipped: 0
BUILD SUCCESS
```

This automated gate verified features 001 and 002 without regression and feature 003 GREEN; HS256 JWT configuration and compatible `JwtEncoder`/`JwtDecoder`; BCrypt `PasswordEncoder`; exact, case-sensitive account lookup; JWT issuance; valid authentication; dummy BCrypt work for unknown users; uniform invalid-credential handling; HTTP 400, 401, and 500 contracts; rejection of unknown JSON properties; confidentiality of responses and logs; and anonymous access to `GET /products` and `GET /products/{id}`. No security, validation, or HTTP-contract regression was detected, and the public-route policy for product listing and detail remains unchanged.

The Pull Request review remediation is included in this gate. Non-string JSON credentials, including numeric `name` and boolean `password` values, are rejected with HTTP 400 and the generic four-field `Bad Request` / `Invalid login data` contract before authentication service execution. When the application is mounted under a servlet context path, the logical `/login` endpoint retains that login-specific contract while the public `path` field preserves the external request URI, such as `/api/login`. These cases also verify that responses and logs expose no password, hash, token, JWT secret, key material, or equivalent sensitive data.

## Run with Test Classpath

Set a disposable test key. This Base64 value decodes to exactly 32 bytes and must never be used in production:

```powershell
$env:JWT_SECRET_BASE64 = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="
mvn spring-boot:test-run -Dspring-boot.run.profiles=test
```

`spring-boot:test-run` uses the test classpath, so `application-test.properties` and the test-only `import.sql` are available. Keep this process running while executing the commands below in another PowerShell window.

The following sections are reproducible manual procedures. Their expected behavior is backed by the automated gate above; this document does not claim that these `curl.exe` commands were executed as part of the final gate.

## A. Valid Login

```powershell
curl.exe -i -X POST "http://localhost:8080/login" -H "Content-Type: application/json" -d '{"name":"demo","password":"secret123"}'
```

Expected: `HTTP/1.1 200`. The response contains only a non-empty `accessToken` and `tokenType` equal to `Bearer`. It contains no password, password hash, secret, refresh token, or `expiresIn`; expiry is in JWT claim `exp`.

## B. Unknown Name

```powershell
curl.exe -i -X POST "http://localhost:8080/login" -H "Content-Type: application/json" -d '{"name":"unknown","password":"secret123"}'
```

Expected: `HTTP/1.1 401`, error `Unauthorized`, message `Invalid credentials`, path `/login`, and no token. The server performs a BCrypt comparison against the dummy hash before rejecting the request.

## C. Wrong Password

```powershell
curl.exe -i -X POST "http://localhost:8080/login" -H "Content-Type: application/json" -d '{"name":"demo","password":"wrong"}'
```

Expected: the same `HTTP/1.1 401`, message, and response shape as the unknown-name case, with no token.

## D. Invalid Input

```powershell
curl.exe -i -X POST "http://localhost:8080/login" -H "Content-Type: application/json" -d '{"name":" ","password":"secret123"}'
curl.exe -i -X POST "http://localhost:8080/login" -H "Content-Type: application/json" -d '{"name":"demo"}'
curl.exe -i -X POST "http://localhost:8080/login" -H "Content-Type: application/json" -d '{"name":"demo","password":"secret123","unexpectedField":"value"}'
```

Missing, `null`, empty, or whitespace-only `name` or `password`, and any unknown JSON property, have the same expected result: `HTTP/1.1 400`, error `Bad Request`, message `Invalid login data`, path `/login`, no echoed credential value, and no token.

## E. Public Catalog Listing

```powershell
curl.exe -i "http://localhost:8080/products?page=0&size=12"
```

Expected: `HTTP/1.1 200` without an Authorization header and behavior identical to feature 001. This route remains intentionally public.

## F. Public Product Detail

```powershell
curl.exe -i "http://localhost:8080/products/1"
```

Expected: the existing feature 002 result without an Authorization header. This route remains intentionally public. For seeded product `1`, this is `HTTP/1.1 200`; missing ids retain the existing `404` behavior rather than becoming `401`.

Feature 003 issues JWTs but does not enable OAuth2 Resource Server, Authorization Server, or bearer-token protection. The JWT is not required for either public product route.

## Internal Failure Validation

Internal failures are validated primarily by automated controller/service tests that simulate repository, password-encoder, or JWT-encoder failures. Do not deliberately disrupt real infrastructure for this check.

Verified public contract: `HTTP 500`, error `Internal Server Error`, message `Authentication could not be completed`, path `/login`, and no token, password, hash, secret, internal cause, or stack trace.
