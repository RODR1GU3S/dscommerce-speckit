# DSCommerce

Spring Boot backend service for DSCommerce learning features.

## Requirements and Maven Wrapper

Install a Java 21 JDK and configure `JAVA_HOME` or make Java available on `PATH`. The project includes Apache Maven Wrapper 3.3.4 (`only-script`), configured to run Maven 3.9.16. A separate Maven installation is not required for normal project commands.

Check the Maven and Java versions from the repository root:

Windows/PowerShell:

```powershell
.\mvnw.cmd --version
```

Linux/macOS:

```bash
./mvnw --version
```

The first invocation downloads the official Maven ZIP, verifies its configured SHA-256, and extracts it into the user's `.m2/wrapper/dists` cache. Later invocations reuse that distribution. Initial downloads require network access; Maven may also download project dependencies. The Wrapper selects Maven, while the installed JDK determines the Java runtime.

For Linux/macOS, keep `mvnw` with LF line endings and executable permission; install `unzip` and either `sha256sum` or `shasum` for this ZIP/checksum configuration. Git records `mvnw` as executable (`100755`); [.gitattributes](.gitattributes) keeps LF for `mvnw` and CRLF for `mvnw.cmd` in checkouts.

See [the SDD and harness guide](docs/harness.md) for generation commands, checksum provenance, and Windows and Linux validation evidence. The [first Linux CI run](https://github.com/RODR1GU3S/dscommerce-speckit/actions/runs/36776432450) passed the test suite through the Wrapper; macOS commands have received static review only.

## Running the Application

The application signs access tokens with HS256 and requires the signing key to be supplied externally through:

```properties
security.jwt.secret-base64=${JWT_SECRET_BASE64}
security.jwt.ttl=PT15M
```

`JWT_SECRET_BASE64` must contain Base64-encoded key material that decodes to at least 32 bytes. Never commit a real signing secret. For a normal PowerShell run, provide a secure external value and start Spring Boot:

```powershell
$env:JWT_SECRET_BASE64 = "<base64-de-chave-segura>"
.\mvnw.cmd spring-boot:run
```

On Linux/macOS, supply the external key for the process and run:

```bash
JWT_SECRET_BASE64="<base64-de-chave-segura>" ./mvnw spring-boot:run
```

The default token lifetime is 15 minutes (`PT15M`) and can be changed through `security.jwt.ttl`. Starting the application this way does not create a production account; production identities must be provisioned outside this feature.

### Demonstration with Test-Only Data

To run with the test profile and test classpath:

```powershell
.\mvnw.cmd spring-boot:test-run "-Dspring-boot.run.profiles=test"
```

Linux/macOS:

```bash
./mvnw spring-boot:test-run -Dspring-boot.run.profiles=test
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

Neither route requires an `Authorization` header. Login issues JWTs; protected order and administrative routes validate bearer tokens and current database roles. The catalog routes above remain public.

Validation examples:

```bash
curl -i "http://localhost:8080/products?page=-1&size=12"
curl -i "http://localhost:8080/products?page=0&size=0"
curl -i "http://localhost:8080/products?page=0&size=51"
```

Run the catalog tests:

Windows/PowerShell:

```powershell
.\mvnw.cmd test "-Dtest=ProductControllerTest,ProductCatalogServiceTest,ProductRepositoryTest"
```

Linux/macOS:

```bash
./mvnw test "-Dtest=ProductControllerTest,ProductCatalogServiceTest,ProductRepositoryTest"
```

## Payment and Own-Order Contracts (Feature 008)

- [Payment registration, version 0.1.0](specs/008-registrar-pagamento/contracts/payment-registration-openapi.yaml): `POST /admin/orders/{id}/payments` records an ADMIN-declared full settlement.
- [Complete own-order query snapshot, version 0.2.0](specs/008-registrar-pagamento/contracts/own-orders-openapi.yaml): `GET/HEAD /orders` and `/orders/{id}`, including stored `WAITING_PAYMENT` and `PAID` states, with ownership unchanged.
- [Contract precedence and limits](specs/008-registrar-pagamento/contracts/README.md) and [procedures, scenario matrix and execution evidence](specs/008-registrar-pagamento/quickstart.md#t035--consolidação-documental-das-evidências-2026-10-23).

The complete 008 query snapshot is current for the locally implemented and validated 008 revision and replaces the 007 query contract after 008 delivery in the revision/environment concerned, according to the contract precedence conditions. The 007 contract remains historical evidence for earlier revisions without edits; the two snapshots are not simultaneously current alternatives. This local documentation does not establish deployment elsewhere or integration into `main`. T035's editorial impediment is resolved; [T036's final local review](specs/008-registrar-pagamento/quickstart.md#t036--revisão-final-e-encerramento-local-2026-10-23) completes all 36 tasks.

The local FINAL run `FINAL-T034R-20261023T170507809` passed 4,957 tests with no failures, errors or skipped tests and Maven exit 0 on Windows/Java 21/H2. T035 reuses that evidence without rerunning tests. Feature 008 has no remote CI validation in this record, and PostgreSQL is not validated. Feature 005's Gate IV remains an **EXCEÇÃO DOCUMENTADA**, not a strict PASS; see its [plan](specs/005-manter-produtos/plan.md).

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

Windows/PowerShell:

```powershell
.\mvnw.cmd test
```

Linux/macOS:

```bash
./mvnw test
```

The suite covers JWT/HS256 configuration, BCrypt password matching, valid authentication, invalid credentials and input, safe internal-error handling, confidentiality, regressions in features 001 and 002, and continued anonymous access to the public product routes.

## GitHub Actions CI

The [CI workflow](.github/workflows/ci.yml), named `CI - Testes Maven`, is configured for pull requests targeting `main`, pushes to `main` and `chore/harness-foundation`, and manual `workflow_dispatch` runs. GitHub requires the workflow to be present on the default branch for the manual trigger to be available.

The `Testes (Java 21 / Maven)` job uses Ubuntu 24.04, Java 21 Zulu and a Maven dependency cache. It prints Java and Wrapper-selected Maven versions, then runs from the repository root:

```bash
./mvnw --batch-mode --no-transfer-progress test
```

Open the repository's Actions tab, select the workflow and run, and inspect the job's step logs. Download the `surefire-reports` artifact from the run summary; it contains the reports from `target/surefire-reports/` and is retained for 14 days. Upload is attempted even when tests fail; a successful upload preserves the test failure. If an earlier failure prevents reports from being generated, upload emits a warning about missing files.

The [first remote Linux run](https://github.com/RODR1GU3S/dscommerce-speckit/actions/runs/36776432450), triggered by `push` on `chore/harness-foundation`, completed successfully with 79 tests, no failures, errors or skipped tests, and an uploaded Surefire artifact. Its tested commit, effective versions, report inspection and retention limit are recorded in the [harness guide](docs/harness.md). The earlier Windows results remain separate historical evidence. Each new revision needs its own checks; for `pull_request`, confirm the GitHub merge reference tested in the checkout logs as well as the proposal's branch commit.

A configured workflow does not make its check mandatory for merging. That depends on repository rules requiring the check; branch protection rules have not been configured in this stage.
