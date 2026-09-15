# Data Model: Visualizar Detalhes do Produto

## Product

Represents an item already available in the catalog.

### Fields

| Field | Type | Required | Notes |
|-------|------|----------|-------|
| `id` | Long | Yes | Stable identifier used by catalog listing and detail navigation. |
| `name` | String | Yes | Product display name. Preserves feature 001 listing sort behavior. |
| `description` | String | Yes | Detailed product description shown only in the detail view. |
| `image` | String | Yes | Image reference clients can render for listing and details. |
| `price` | BigDecimal | Yes | Current product price. |
| `categories` | Set<Category> | No | Associated product categories. Empty is technically representable, though validation scenarios should cover one and multiple categories. |

### Validation Rules

- `name` must be present and non-blank.
- `description` must be present and non-blank for product detail data.
- `image` must be present and non-blank.
- `price` must be present and greater than or equal to zero.
- Listing responses must continue to omit `description` and `categories`.
- Detail responses must include unique category names only; duplicate category names associated with the product must appear once.

### Relationships

- Product has a many-to-many relationship with Category through a product-category join table.
- Product detail retrieval reads the categories associated only with the selected product.

### State Transitions

- None. This feature is read-only and does not create, update, delete, import, activate, deactivate, or recategorize products.

## Category

Represents a classification associated with one or more catalog products.

### Fields

| Field | Type | Required | Notes |
|-------|------|----------|-------|
| `id` | Long | Yes | Stable category identifier for persistence. Not exposed by this feature's response contract. |
| `name` | String | Yes | Category display name exposed in product detail responses. |

### Validation Rules

- `name` must be present and non-blank.
- Category descriptions, hierarchy, filtering, and management are outside this feature.

### Relationships

- Category may be associated with many products.
- This feature only needs traversal from Product to Category for detail display.

### State Transitions

- None. This feature does not manage category lifecycle.

## ProductDetail

DTO shape for the product detail API response.

### Fields

| Field | Type | Required | Notes |
|-------|------|----------|-------|
| `id` | Long | Yes | Identifier of the selected product. |
| `name` | String | Yes | Name of the selected product. |
| `description` | String | Yes | Description of the selected product. |
| `image` | String | Yes | Image reference of the selected product. |
| `price` | BigDecimal | Yes | Price of the selected product. |
| `categories` | List<String> | Yes | Unique category names associated with the selected product. |

### Mapping Rules

- Map fields from the found Product entity to `ProductDetailDTO`.
- Map category names from `Product.categories`.
- Remove duplicate category names before returning the DTO.
- Prefer deterministic category-name ordering in the DTO to keep API responses and tests stable.
- Do not expose Category ids or JPA entity structure in the API.

## ErrorResponse

Existing DTO shape used for centralized errors.

### Fields

| Field | Type | Required | Notes |
|-------|------|----------|-------|
| `status` | Integer | Yes | HTTP status code, e.g. `404`. |
| `error` | String | Yes | HTTP reason phrase, e.g. `Not Found`. |
| `message` | String | Yes | Clear not-found message for the missing product. |
| `path` | String | Yes | Request path, e.g. `/products/999`. |

### Rules

- Product not found must not return data from another product.
- Product not found must be handled centrally by the global exception handler.
- The existing validation error behavior for feature 001 remains unchanged.
