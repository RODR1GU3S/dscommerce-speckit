package com.devsuperior.dscommerce.repositories;

import com.devsuperior.dscommerce.entities.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    @Transactional(readOnly = true)
    List<Category> findAllByOrderByNameAscIdAsc();

    @Transactional(readOnly = true)
    @Query("select category from Category category where category.id in :ids")
    List<Category> findAllByIdIn(@Param("ids") Collection<Long> ids);
}
