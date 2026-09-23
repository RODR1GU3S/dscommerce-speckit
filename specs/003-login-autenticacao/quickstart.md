# Quickstart: Login e Autenticação

This Windows/PowerShell guide validates the feature after implementation using only test-classpath data. It does not create a production account.

## Prerequisites

- Java 21 and Maven available on `PATH`.
- Test profile configuration in `src/test/resources/application-test.properties`.
- A test-only BCrypt-backed user seeded by `src/test/resources/import.sql` with credentials `demo` / `secret123`.
- Test JWT configuration supplies `security.jwt.secret-base64` from `JWT_SECRET_BASE64` and uses default `security.jwt.ttl=PT15M` unless the test profile overrides it.

The planned seed belongs only to `src/test/resources`; `src/main/resources` must not create accounts or contain signing secrets.

Relevant references:

- API: `specs/003-login-autenticacao/contracts/login-openapi.yaml`
- Model: `specs/003-login-autenticacao/data-model.md`
- Public APIs to preserve: `specs/001-consulta-catalogo-produtos/contracts/products-openapi.yaml` and `specs/002-visualizar-detalhes-produto/contracts/product-detail-openapi.yaml`

## Automated Validation

```powershell
mvn test
```

Expected: authentication tests and all feature 001/002 regression tests pass. Token tests decode the returned JWT with the configured `JwtDecoder` and verify HS256 signature, `sub`, `name`, `iat`, and finite `exp` without enabling resource protection.

## Run with Test Classpath

Set a disposable test key. This Base64 value decodes to exactly 32 bytes and must never be used in production:

```powershell
$env:JWT_SECRET_BASE64 = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="
mvn spring-boot:test-run -Dspring-boot.run.profiles=test
```

`spring-boot:test-run` uses the test classpath, so `application-test.properties` and the test-only `import.sql` are available. Keep this process running while executing the commands below in another PowerShell window.

## A. Valid Login

```powershell
curl.exe -i -X POST "http://localhost:8080/login" -H "Content-Type: application/json" -d '{"name":"demo","password":"secret123"}'
```

Expected: `HTTP/1.1 200`, a non-empty `accessToken`, and `tokenType` equal to `Bearer`. The body contains no password, hash, refresh token, or `expiresIn`; expiry is in JWT claim `exp`.

## B. Unknown Name

```powershell
curl.exe -i -X POST "http://localhost:8080/login" -H "Content-Type: application/json" -d '{"name":"unknown","password":"secret123"}'
```

Expected: `HTTP/1.1 401`, message `Invalid credentials`, and no token. The server performs a BCrypt comparison against the dummy hash before rejecting the request.

## C. Wrong Password

```powershell
curl.exe -i -X POST "http://localhost:8080/login" -H "Content-Type: application/json" -d '{"name":"demo","password":"wrong"}'
```

Expected: the same `HTTP/1.1 401`, message, and response shape as the unknown-name case, with no token.

## D. Missing or Blank Field

```powershell
curl.exe -i -X POST "http://localhost:8080/login" -H "Content-Type: application/json" -d '{"name":" ","password":"secret123"}'
curl.exe -i -X POST "http://localhost:8080/login" -H "Content-Type: application/json" -d '{"name":"demo"}'
```

Expected: `HTTP/1.1 400`, generic message `Invalid login data`, no echoed credential value, and no token.

## E. Public Catalog Listing

```powershell
curl.exe -i "http://localhost:8080/products?page=0&size=12"
```

Expected: `HTTP/1.1 200` without an Authorization header and behavior identical to feature 001.

## F. Public Product Detail

```powershell
curl.exe -i "http://localhost:8080/products/1"
```

Expected: the existing feature 002 result without an Authorization header. For seeded product `1`, this is `HTTP/1.1 200`; missing ids retain the existing `404` behavior rather than becoming `401`.

## Internal Failure Validation

Use an automated controller/service test that makes the repository or JWT encoder fail.

Expected: generic 5xx response, message `Authentication could not be completed`, and no token, password, hash, or stack trace.
