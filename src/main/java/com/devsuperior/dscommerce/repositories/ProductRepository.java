package com.devsuperior.dscommerce.repositories;

import com.devsuperior.dscommerce.entities.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    @Query("""
            SELECT DISTINCT p
            FROM Product p
            LEFT JOIN FETCH p.categories
            WHERE p.id = :id
            """)
    Optional<Product> findByIdWithCategories(@Param("id") Long id);
}
