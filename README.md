# DSCommerce

Spring Boot backend service for DSCommerce learning features.

## Product Catalog API

Start the application:

```bash
mvn spring-boot:run
```

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

Run the complete test suite:

```bash
mvn test
```
