package com.devsuperior.dscommerce.repositories;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class ProductRepositoryTest {

    @Autowired
    private ProductRepository productRepository;

    @Test
    void findAllShouldReturnRequestedPageSizeAndTotalMetadata() {
        var page = productRepository.findAll(PageRequest.of(1, 10));

        assertThat(page.getContent()).hasSize(10);
        assertThat(page.getNumber()).isEqualTo(1);
        assertThat(page.getSize()).isEqualTo(10);
        assertThat(page.getTotalElements()).isEqualTo(52);
        assertThat(page.getTotalPages()).isEqualTo(6);
    }

    @Test
    void findAllShouldReturnEmptyContentWhenPageIsOutOfRange() {
        var page = productRepository.findAll(PageRequest.of(999, 12));

        assertThat(page.getContent()).isEmpty();
        assertThat(page.getNumber()).isEqualTo(999);
        assertThat(page.getSize()).isEqualTo(12);
        assertThat(page.getTotalElements()).isEqualTo(52);
        assertThat(page.getTotalPages()).isEqualTo(5);
    }

    @Test
    void findAllShouldReturnProductsOrderedByNameAscThenIdAsc() {
        var sort = Sort.by(
                Sort.Order.asc("name"),
                Sort.Order.asc("id")
        );

        var page =
                productRepository.findAll(
                    PageRequest.of(0, 4, sort)
                );

        assertThat(page.getContent())
                .extracting("name")
                .containsExactly("Duplicate", "Duplicate", "Product 01", "Product 02");
        assertThat(page.getContent())
                .extracting("id")
                .containsExactly(51L, 52L, 50L, 49L);
    }

    @Test
    void findAllShouldKeepNameAscThenIdAscAcrossPageBoundaries() {
        var sort = Sort.by(
                Sort.Order.asc("name"),
                Sort.Order.asc("id")
        );

        var firstPage =
                productRepository.findAll(
                        PageRequest.of(0, 1, sort));
        var secondPage =
                productRepository.findAll(
                        PageRequest.of(1, 1, sort));

        assertThat(firstPage.getContent()).hasSize(1);
        assertThat(firstPage.getContent().getFirst().getName()).isEqualTo("Duplicate");
        assertThat(firstPage.getContent().getFirst().getId()).isEqualTo(51L);
        assertThat(secondPage.getContent()).hasSize(1);
        assertThat(secondPage.getContent().getFirst().getName()).isEqualTo("Duplicate");
        assertThat(secondPage.getContent().getFirst().getId()).isEqualTo(52L);
    }
}
