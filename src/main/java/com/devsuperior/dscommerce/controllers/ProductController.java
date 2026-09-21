package com.devsuperior.dscommerce.controllers;

import com.devsuperior.dscommerce.dto.PageResponseDTO;
import com.devsuperior.dscommerce.dto.ProductCatalogItemDTO;
import com.devsuperior.dscommerce.dto.ProductDetailDTO;
import com.devsuperior.dscommerce.services.ProductCatalogService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/products")
@Validated
public class ProductController {

    private final ProductCatalogService productCatalogService;

    public ProductController(ProductCatalogService productCatalogService) {
        this.productCatalogService = productCatalogService;
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductDetailDTO> findProductDetails(@PathVariable Long id) {
        return ResponseEntity.ok(productCatalogService.findProductDetails(id));
    }

    @GetMapping
    public ResponseEntity<PageResponseDTO<ProductCatalogItemDTO>> listProducts(
            @RequestParam(defaultValue = "0")
            @Min(value = 0, message = "page must be greater than or equal to 0")
            int page,
            @RequestParam(defaultValue = "12")
            @Min(value = 1, message = "size must be between 1 and 50")
            @Max(value = 50, message = "size must be between 1 and 50")
            int size
    ) {
        return ResponseEntity.ok(productCatalogService.listProducts(page, size));
    }
}
