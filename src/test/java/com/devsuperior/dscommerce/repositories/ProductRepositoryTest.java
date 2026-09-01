package com.devsuperior.dscommerce.repositories;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
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
}
