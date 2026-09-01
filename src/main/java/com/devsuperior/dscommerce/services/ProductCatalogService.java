package com.devsuperior.dscommerce.services;

import com.devsuperior.dscommerce.dto.PageResponseDTO;
import com.devsuperior.dscommerce.dto.ProductCatalogItemDTO;
import com.devsuperior.dscommerce.repositories.ProductRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductCatalogService {

    private final ProductRepository productRepository;

    public ProductCatalogService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Transactional(readOnly = true)
    public PageResponseDTO<ProductCatalogItemDTO> listProducts(int page, int size) {
        var products = productRepository.findAll(PageRequest.of(page, size));
        var content = products.getContent().stream()
                .map(ProductCatalogItemDTO::new)
                .toList();

        return new PageResponseDTO<>(
                content,
                products.getNumber(),
                products.getSize(),
                products.getTotalElements(),
                products.getTotalPages()
        );
    }
}
