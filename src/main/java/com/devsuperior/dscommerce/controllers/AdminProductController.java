package com.devsuperior.dscommerce.controllers;

import com.devsuperior.dscommerce.dto.AdminProductDetailDTO;
import com.devsuperior.dscommerce.dto.AdminProductItemDTO;
import com.devsuperior.dscommerce.dto.PageResponseDTO;
import com.devsuperior.dscommerce.dto.ProductWriteRequestDTO;
import com.devsuperior.dscommerce.security.AuthenticatedAccountPrincipal;
import com.devsuperior.dscommerce.services.AdminProductQueryService;
import com.devsuperior.dscommerce.services.ProductMaintenanceService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.List;

@RestController
@RequestMapping("/admin/products")
public class AdminProductController {
    private final AdminProductQueryService queries;
    private final ProductMaintenanceService maintenance;

    public AdminProductController(AdminProductQueryService queries, ProductMaintenanceService maintenance) {
        this.queries = queries;
        this.maintenance = maintenance;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AdminProductDetailDTO> createProduct(
            @Valid @RequestBody ProductWriteRequestDTO input,
            @RequestParam MultiValueMap<String, String> query,
            @AuthenticationPrincipal AuthenticatedAccountPrincipal principal) {
        AdminQueryParameters.requireNoQuery(query);
        // The Spring service proxy completes its transaction before this controller builds HTTP success.
        var response = maintenance.createProduct(input, principal);
        var location = ServletUriComponentsBuilder.fromCurrentRequestUri()
                .path("/{id}").buildAndExpand(response.id()).toUri();
        return ResponseEntity.created(location).body(response);
    }

    @GetMapping
    public ResponseEntity<PageResponseDTO<AdminProductItemDTO>> listProducts(
            @RequestParam MultiValueMap<String, String> query,
            @AuthenticationPrincipal AuthenticatedAccountPrincipal principal) {
        AdminQueryParameters.requireListQuery(query);
        int page = AdminQueryParameters.page(query);
        int size = AdminQueryParameters.size(query);
        String name = query.getFirst("name");
        long offset = (long) page * size;
        // JPA's first-result offset is an int; a valid HTTP page can be beyond that range.
        if (offset > Integer.MAX_VALUE) {
            var metadata = queries.listProducts(name, 0, size, principal);
            if (offset >= metadata.totalElements()) {
                return ResponseEntity.ok(new PageResponseDTO<AdminProductItemDTO>(List.of(), page, size,
                        metadata.totalElements(), metadata.totalPages()));
            }
        }
        return ResponseEntity.ok(queries.listProducts(name, page, size, principal));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AdminProductDetailDTO> findProductDetails(
            @PathVariable String id,
            @RequestParam MultiValueMap<String, String> query,
            @AuthenticationPrincipal AuthenticatedAccountPrincipal principal) {
        AdminQueryParameters.requireNoQuery(query);
        long productId = AdminQueryParameters.productId(id);
        return ResponseEntity.ok(queries.findProductDetails(productId, principal));
    }
}
