package com.devsuperior.dscommerce.services;

import com.devsuperior.dscommerce.entities.Category;
import com.devsuperior.dscommerce.entities.Product;
import com.devsuperior.dscommerce.fixtures.ProductFactory;
import com.devsuperior.dscommerce.repositories.ProductRepository;
import com.devsuperior.dscommerce.services.exceptions.ProductNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductCatalogServiceTest {

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private ProductCatalogService productCatalogService;

    @Test
    void findProductDetailsShouldMapFieldsPreservePriceAndReturnUniqueSortedCategoryNames() {
        BigDecimal price = new BigDecimal("1234.5670");
        Product product = new Product(
                42L,
                "Mechanical Keyboard",
                "Hot-swappable mechanical keyboard",
                "https://cdn.example.com/products/42.jpg",
                price
        );
        product.getCategories().add(new Category(3L, "Electronics"));
        product.getCategories().add(new Category(1L, "Computers"));
        product.getCategories().add(new Category(2L, "Electronics"));
        when(productRepository.findByIdWithCategories(42L))
                .thenReturn(Optional.of(product));

        var response = productCatalogService.findProductDetails(42L);

        assertThat(response.id()).isEqualTo(product.getId());
        assertThat(response.name()).isEqualTo(product.getName());
        assertThat(response.description()).isEqualTo(product.getDescription());
        assertThat(response.image()).isEqualTo(product.getImage());
        assertThat(response.price()).isEqualByComparingTo(price);
        assertThat(response.price().precision()).isEqualTo(price.precision());
        assertThat(response.price().scale()).isEqualTo(price.scale());
        assertThat(response.categories()).containsExactly("Computers", "Electronics");
        verify(productRepository).findByIdWithCategories(42L);
    }

    @Test
    void findProductDetailsShouldThrowProductNotFoundExceptionWhenProductDoesNotExist() {
        Long id = 99999L;
        when(productRepository.findByIdWithCategories(id))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> productCatalogService.findProductDetails(id))
                .isInstanceOf(ProductNotFoundException.class);

        verify(productRepository).findByIdWithCategories(id);
    }

    @Test
    void listProductsShouldCreatePageRequestAndMapPageMetadata() {
        Product product = ProductFactory.createProduct(1L, "Notebook");
        when(productRepository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(product), PageRequest.of(0, 12), 1));

        var response = productCatalogService.listProducts(0, 12);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(productRepository).findAll(pageableCaptor.capture());
        Pageable pageable = pageableCaptor.getValue();

        assertThat(pageable.getPageNumber()).isZero();
        assertThat(pageable.getPageSize()).isEqualTo(12);
        assertThat(response.page()).isZero();
        assertThat(response.size()).isEqualTo(12);
        assertThat(response.totalElements()).isEqualTo(1);
        assertThat(response.totalPages()).isEqualTo(1);
        assertThat(response.content()).hasSize(1);
    }

    @Test
    void listProductsShouldCreatePageRequestSortedByNameAscThenIdAsc() {
        when(productRepository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(2, 5), 52));

        productCatalogService.listProducts(2, 5);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(productRepository).findAll(pageableCaptor.capture());
        Pageable pageable = pageableCaptor.getValue();

        assertThat(pageable.getPageNumber()).isEqualTo(2);
        assertThat(pageable.getPageSize()).isEqualTo(5);
        assertThat(pageable.getSort().getOrderFor("name"))
                .extracting(Sort.Order::getDirection)
                .isEqualTo(Sort.Direction.ASC);
        assertThat(pageable.getSort().getOrderFor("id"))
                .extracting(Sort.Order::getDirection)
                .isEqualTo(Sort.Direction.ASC);
        assertThat(pageable.getSort().stream())
                .map(Sort.Order::getProperty)
                .containsExactly("name", "id");
    }

    @Test
    void listProductsShouldMapEmptyPagesWithRequestedPaginationMetadata() {
        when(productRepository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(999, 12), 52));

        var response = productCatalogService.listProducts(999, 12);

        assertThat(response.content()).isEmpty();
        assertThat(response.page()).isEqualTo(999);
        assertThat(response.size()).isEqualTo(12);
        assertThat(response.totalElements()).isEqualTo(52);
        assertThat(response.totalPages()).isEqualTo(5);
    }

    @Test
    void listProductsShouldMapProductToCatalogItemWithAccurateFieldsAndPreservedPrice() {
        BigDecimal price = new BigDecimal("123.4500");
        Product product = new Product(
                42L,
                "USB-C Hub",
                "https://cdn.example.com/products/42.jpg",
                price
        );
        when(productRepository.findAll(any(Pageable.class)))
                .thenReturn(
                        new PageImpl<>(List.of(product),
                                PageRequest.of(0, 12),
                                1
                        )
                );

        var response = productCatalogService.listProducts(0, 12);

        assertThat(response.content()).hasSize(1);
        var dto = response.content().getFirst();
        assertThat(dto.id()).isEqualTo(product.getId());
        assertThat(dto.name()).isEqualTo(product.getName());
        assertThat(dto.image()).isEqualTo(product.getImage());
        assertThat(dto.price()).isEqualTo(price);
    }
}
