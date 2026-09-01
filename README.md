# DSCommerce

Spring Boot backend service for DSCommerce learning features.

## Product Catalog MVP

Start the application:

```bash
mvn spring-boot:run
```

Request the product catalog with default pagination:

```bash
curl "http://localhost:8080/products"
```

Request a specific page and size:

```bash
curl "http://localhost:8080/products?page=1&size=10"
```

Run the MVP tests:

```bash
mvn test -Dtest=ProductControllerTest,ProductCatalogServiceTest,ProductRepositoryTest
```
