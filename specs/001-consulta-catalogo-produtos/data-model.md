# Data Model: Consulta do Catalogo de Produtos

## Product

Represents an item already available in the catalog.

### Fields

| Field | Type | Required | Notes |
|-------|------|----------|-------|
| `id` | Long | Yes | Stable identifier used internally and as deterministic sort tie-breaker. |
| `name` | String | Yes | Product display name used for alphabetical ordering. |
| `image` | String | Yes | Image reference clients can use to render the product image. |
| `price` | BigDecimal | Yes | Current catalog price exposed in the listing. |

### Validation Rules

- `name` must be present and non-blank.
- `image` must be present and non-blank.
- `price` must be present and greater than or equal to zero.
- Listing responses must not include product detail fields beyond the catalog item contract.

### Relationships

- No new relationships are required for this feature.
- Existing product relationships, if later introduced, stay outside the listing response unless a future feature adds them.

### State Transitions

- None. This feature is read-only and does not create, update, delete, import, activate, or deactivate products.

## Catalog Page

Represents one page of catalog listing results.

### Fields

| Field | Type | Required | Notes |
|-------|------|----------|-------|
| `content` | List<ProductCatalogItem> | Yes | Products for the requested page. Empty when no products exist or requested page is beyond the available range. |
| `page` | Integer | Yes | Zero-based current page number. Defaults to `0` when omitted. |
| `size` | Integer | Yes | Requested page size after defaults. Defaults to `12` when omitted. |
| `totalElements` | Long | Yes | Total products available for the catalog query. |
| `totalPages` | Integer | Yes | Total pages for the current page size. |

### Validation Rules

- Missing `page` uses `0`.
- Missing `size` uses `12`.
- `page` must be greater than or equal to `0`.
- `size` must be between `1` and `50`.
- Invalid explicit pagination values return `400 Bad Request`.

## ProductCatalogItem

DTO shape for one product in the catalog page response.

### Fields

| Field | Type | Required | Notes |
|-------|------|----------|-------|
| `id` | Long | Yes | Included to give clients a stable item identifier for navigation and UI keys. |
| `name` | String | Yes | Product display name. |
| `image` | String | Yes | Product image reference. |
| `price` | BigDecimal | Yes | Current catalog price. |

### Ordering Rule

Catalog pages are ordered by `name ASC, id ASC`. The `id ASC` tie-breaker keeps duplicate product names in a consistent order across equivalent requests.
