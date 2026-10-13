package com.devsuperior.dscommerce.repositories;

import com.devsuperior.dscommerce.entities.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    @Query("""
            SELECT DISTINCT p
            FROM Product p
            LEFT JOIN FETCH p.categories
            WHERE p.id = :id
            """)
    Optional<Product> findByIdWithCategories(@Param("id") Long id);

    @Transactional(readOnly = true)
    @Query(value = """
            SELECT p FROM Product p
            ORDER BY p.name ASC, p.id ASC
            """, countQuery = "SELECT COUNT(p) FROM Product p")
    Page<Product> findAdminPage(Pageable pageable);

    @Transactional(readOnly = true)
    @Query(value = """
            SELECT p FROM Product p
            WHERE locate(lower(:name), lower(p.name)) > 0
            ORDER BY p.name ASC, p.id ASC
            """, countQuery = """
            SELECT COUNT(p) FROM Product p
            WHERE locate(lower(:name), lower(p.name)) > 0
            """)
    Page<Product> findAdminPageByName(@Param("name") String name, Pageable pageable);

    @Transactional(readOnly = true)
    @Query("""
            SELECT DISTINCT p
            FROM Product p
            LEFT JOIN FETCH p.categories
            WHERE p.id = :id
            """)
    Optional<Product> findAdminByIdWithCategories(@Param("id") Long id);
}
