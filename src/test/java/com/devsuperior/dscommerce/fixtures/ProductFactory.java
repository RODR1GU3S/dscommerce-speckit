package com.devsuperior.dscommerce.fixtures;

import com.devsuperior.dscommerce.entities.Category;
import com.devsuperior.dscommerce.entities.Product;

import java.math.BigDecimal;

public final class ProductFactory {

    private ProductFactory() {
    }

    public static Product createProduct(long id, String name) {
        return createProduct(id, name, "Description for " + name);
    }

    public static Product createProduct(long id, String name, String description) {
        return new Product(
                id,
                name,
                description,
                "https://cdn.example.com/products/" + id + ".jpg",
                BigDecimal.valueOf(id * 10).setScale(2)
        );
    }

    public static Product createProduct(long id, String name, String description, Category... categories) {
        Product product = createProduct(id, name, description);
        for (Category category : categories) {
            if (category == null || category.getId() == null) {
                throw new IllegalArgumentException("Category and category id must not be null");
            }
            product.getCategories().add(category);
        }
        return product;
    }
}
