package com.devsuperior.dscommerce.controllers;

import com.devsuperior.dscommerce.dto.PageResponseDTO;
import com.devsuperior.dscommerce.dto.ProductCatalogItemDTO;
import com.devsuperior.dscommerce.services.ProductCatalogService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/products")
public class ProductController {

    private final ProductCatalogService productCatalogService;

    public ProductController(ProductCatalogService productCatalogService) {
        this.productCatalogService = productCatalogService;
    }

    @GetMapping
    public ResponseEntity<PageResponseDTO<ProductCatalogItemDTO>> listProducts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size
    ) {
        return ResponseEntity.ok(productCatalogService.listProducts(page, size));
    }
}
