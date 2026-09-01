package com.devsuperior.dscommerce.fixtures;

import com.devsuperior.dscommerce.entities.Product;

import java.math.BigDecimal;

public final class ProductFactory {

    private ProductFactory() {
    }

    public static Product createProduct(long id, String name) {
        return new Product(id, name, "https://cdn.example.com/products/" + id + ".jpg", BigDecimal.valueOf(id * 10).setScale(2));
    }
}
