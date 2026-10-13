package com.devsuperior.dscommerce.controllers;

import com.devsuperior.dscommerce.dto.AdminProductDetailDTO;
import com.devsuperior.dscommerce.dto.AdminProductItemDTO;
import com.devsuperior.dscommerce.dto.PageResponseDTO;
import com.devsuperior.dscommerce.security.AuthenticatedAccountPrincipal;
import com.devsuperior.dscommerce.services.AdminProductQueryService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/admin/products")
public class AdminProductController {
    private final AdminProductQueryService queries;

    public AdminProductController(AdminProductQueryService queries) {
        this.queries = queries;
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
