# Research: Visualizar Detalhes do Produto

## Product Description in the Existing Model

Decision: Extend `Product` with a required `description` field persisted in `tb_product`.

Rationale: The approved specification requires the detail view to present description, and the current entity only has `id`, `name`, `image`, and `price`. Keeping description on `Product` matches the domain language and avoids creating an artificial detail table for one intrinsic product attribute.

Alternatives considered: Reusing `image` or another display field was rejected because it would mix unrelated concerns. A separate `ProductDetails` entity was rejected because the current requirements do not justify a one-to-one split.

## Category Representation

Decision: Introduce a `Category` JPA entity with `id` and required `name`.

Rationale: The specification names Category as a key entity and requires category names in product details. A real entity keeps persistence explicit, supports product-category association tests, and leaves room for future category behavior without exposing management features now.

Alternatives considered: Storing category names as a delimited string on `Product` was rejected because it weakens data integrity and complicates duplicate handling. Returning hard-coded category names from the service was rejected because the feature depends on existing catalog data.

## Product and Category Relationship

Decision: Model `Product.categories` as a many-to-many association through a join table, with Product as the side used by the detail read flow.

Rationale: The requirements state that a product may have one or more categories and may show all associated category names. In a commerce catalog, the same category naturally applies to multiple products. A join table is the standard relational/JPA representation for this association.

Alternatives considered: A one-to-many relationship from Product to Category was rejected because it would imply each category belongs to only one product. A unidirectional many-to-one from Product to Category was rejected because it cannot represent multiple categories per product.

## Product Detail DTO

Decision: Add `ProductDetailDTO` as the response shape for details: `id`, `name`, `description`, `image`, `price`, and `categories`, where `categories` is a list of unique category names.

Rationale: The constitution requires DTO-based API contracts. A separate DTO prevents the detail endpoint from changing the feature 001 listing response and makes the expanded detail contract clear.

Alternatives considered: Extending `ProductCatalogItemDTO` was rejected because feature 001 tests assert that listing items expose only catalog summary fields. Returning `CategoryDTO` objects was rejected because the specification only requires category names.

## Individual Product Query

Decision: Add a repository detail query that finds a product by id and fetches categories for that product in the same persistence operation, returning `Optional<Product>`.

Rationale: The service needs a precise found/not-found boundary. Fetching categories with the detail query avoids accidental lazy loading behavior during DTO mapping and keeps category data retrieval explicit.

Alternatives considered: Calling `findById` and relying on lazy category access was rejected because it depends on persistence-context timing and can lead to N+1 or lazy initialization issues. Loading all products and filtering in memory was rejected because the feature is a single-resource lookup.

## API Exposure

Decision: Expose product detail retrieval as `GET /products/{id}`.

Rationale: The endpoint is resource-oriented, complements the existing `GET /products`, and lets clients navigate from catalog item `id` to the detail representation in one intentional selection step.

Alternatives considered: `GET /product-details/{id}` was rejected because it splits the product resource unnecessarily. Adding query parameters to `GET /products` was rejected because listing and detail are different representations and have different response shapes.

## Not-Found Handling

Decision: Introduce or reuse a service-layer not-found exception and handle it in `GlobalExceptionHandler` as `404 Not Found` using the existing `ErrorResponseDTO(status, error, message, path)` shape.

Rationale: The constitution requires centralized error handling and REST semantics. The current infrastructure already centralizes validation errors, so extending that handler preserves consistency and avoids controller-level try/catch.

Alternatives considered: Returning `null` or an empty DTO was rejected because it can confuse clients and risks showing incorrect data. Returning `200 OK` with an error payload was rejected because missing resources should use `404 Not Found`.

## Preserving Feature 001

Decision: Keep `GET /products`, `ProductCatalogItemDTO`, page metadata, pagination validation, and sort order unchanged.

Rationale: FR-007 requires the detail view to complement the existing catalog listing. Existing behavior includes default page `0`, default size `12`, size bounds `1..50`, `400 Bad Request` for invalid pagination, and catalog items with only `id`, `name`, `image`, and `price`.

Alternatives considered: Adding description/categories to list results was rejected because it would violate the feature 001 contract and broaden the listing payload unnecessarily.
