# DSCommerce

Spring Boot backend service for DSCommerce learning features.

## Running the Application

The application signs access tokens with HS256 and requires the signing key to be supplied externally through:

```properties
security.jwt.secret-base64=${JWT_SECRET_BASE64}
security.jwt.ttl=PT15M
```

`JWT_SECRET_BASE64` must contain Base64-encoded key material that decodes to at least 32 bytes. Never commit a real signing secret. For a normal PowerShell run, provide a secure external value and start Spring Boot:

```powershell
$env:JWT_SECRET_BASE64 = "<base64-de-chave-segura>"
mvn spring-boot:run
```

The default token lifetime is 15 minutes (`PT15M`) and can be changed through `security.jwt.ttl`. Starting the application this way does not create a production account; production identities must be provisioned outside this feature.

### Demonstration with Test-Only Data

To run with the test profile and test classpath:

```powershell
mvn spring-boot:test-run -Dspring-boot.run.profiles=test
```

This mode loads configuration and seed data exclusively from `src/test/resources`. The credentials `demo` / `secret123` are test-only data and do not represent a production account or password. The disposable JWT key from the test profile must never be reused in production.

## Product Catalog API

List products:

```bash
curl "http://localhost:8080/products"
```

`GET /products` returns a JSON page with:

- `content`: catalog items containing only `id`, `name`, `image`, and `price`
- `page`: zero-based current page number
- `size`: requested page size
- `totalElements`: total products in the catalog
- `totalPages`: total pages for the requested size

Pagination defaults and limits:

- Missing `page` defaults to `0`.
- Missing `size` defaults to `12`.
- `page` must be `>= 0`.
- `size` must be between `1` and `50`.
- Invalid explicit pagination returns `400 Bad Request` with `status`, `error`, `message`, and `path`.

Request a specific page and size:

```bash
curl "http://localhost:8080/products?page=1&size=10"
```

Products are ordered by `name ASC, id ASC`, preserving stable ordering for duplicate product names across pages.

Product listing and detail routes remain intentionally public:

```text
GET /products
GET /products/{id}
```

Neither route requires an `Authorization` header. Feature 003 issues JWTs but does not enable bearer-token authentication, OAuth2 Resource Server, Authorization Server, or protection for catalog endpoints. The issued JWT does not currently authorize access to protected routes.

Validation examples:

```bash
curl -i "http://localhost:8080/products?page=-1&size=12"
curl -i "http://localhost:8080/products?page=0&size=0"
curl -i "http://localhost:8080/products?page=0&size=51"
```

Run the catalog tests:

```bash
mvn test -Dtest=ProductControllerTest,ProductCatalogServiceTest,ProductRepositoryTest
```

## Login and Authentication API

`POST /login` accepts exactly `name` and `password` as JSON fields.

### Successful Login

With the application running on the test classpath, a PowerShell request using the test-only account is:

```powershell
curl.exe -i -X POST "http://localhost:8080/login" `
  -H "Content-Type: application/json" `
  -d '{"name":"demo","password":"secret123"}'
```

Successful authentication returns `HTTP 200` and only the token fields:

```json
{
  "accessToken": "<jwt-token>",
  "tokenType": "Bearer"
}
```

The response does not expose a password, password hash, signing secret, refresh token, or `expiresIn` field. Token expiration is carried by the JWT `exp` claim.

### Authentication Errors

An unknown user and an incorrect password return exactly the same generic `HTTP 401` contract, so the response does not reveal which credential failed or whether an account exists:

```json
{
  "status": 401,
  "error": "Unauthorized",
  "message": "Invalid credentials",
  "path": "/login"
}
```

A missing, `null`, empty, or whitespace-only `name` or `password`, as well as any unknown JSON property, returns the generic `HTTP 400` contract:

```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "Invalid login data",
  "path": "/login"
}
```

If authentication cannot be completed because of an internal failure, the public response contains no internal cause, stack trace, credential, hash, key, or secret:

```json
{
  "status": 500,
  "error": "Internal Server Error",
  "message": "Authentication could not be completed",
  "path": "/login"
}
```

## Automated Tests

Run the complete test suite:

```bash
mvn test
```

The suite covers JWT/HS256 configuration, BCrypt password matching, valid authentication, invalid credentials and input, safe internal-error handling, confidentiality, regressions in features 001 and 002, and continued anonymous access to the public product routes.
