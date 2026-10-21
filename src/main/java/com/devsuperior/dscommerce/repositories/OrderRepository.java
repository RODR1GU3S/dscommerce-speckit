package com.devsuperior.dscommerce.repositories;

import com.devsuperior.dscommerce.entities.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** JPA writes use the complete aggregate; own-order reads require its owner. */
public interface OrderRepository extends JpaRepository<Order, Long> {

    @Transactional(readOnly = true)
    @Query(value = """
            SELECT o.id FROM Order o
            WHERE o.buyer.id = :ownerId
            ORDER BY o.createdAt DESC, o.id DESC
            """, countQuery = """
            SELECT COUNT(o.id) FROM Order o
            WHERE o.buyer.id = :ownerId
            """)
    Page<Long> findOwnOrderIds(@Param("ownerId") long ownerId, Pageable pageable);

    /** No Pageable here: the ID page already selected the roots, and all their items are needed. */
    @Transactional(readOnly = true)
    @Query("""
            SELECT DISTINCT o FROM Order o
            LEFT JOIN FETCH o.items i
            LEFT JOIN FETCH i.product
            WHERE o.buyer.id = :ownerId AND o.id IN :ids
            """)
    List<Order> findOwnOrdersWithItemsByIds(@Param("ownerId") long ownerId, @Param("ids") Collection<Long> ids);

    /** Restricts the root before fetching its complete item sequence; no global existence probe. */
    @Transactional(readOnly = true)
    @Query("""
            SELECT DISTINCT o FROM Order o
            LEFT JOIN FETCH o.items i
            LEFT JOIN FETCH i.product
            WHERE o.id = :id AND o.buyer.id = :ownerId
            """)
    Optional<Order> findOwnOrderWithItemsById(@Param("ownerId") long ownerId, @Param("id") long id);
}
