package com.devsuperior.dscommerce.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

@Entity
@Table(name = "tb_product")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(nullable = false)
    private String name;

    @NotBlank
    @Lob
    @Column(nullable = false)
    private String description;

    @NotBlank
    @Lob
    @Column(nullable = false)
    private String image;

    @NotNull
    @PositiveOrZero
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal price;

    @ManyToMany
    @JoinTable(
            name = "tb_product_category",
            joinColumns = @JoinColumn(name = "product_id"),
            inverseJoinColumns = @JoinColumn(name = "category_id")
    )
    private Set<Category> categories = new HashSet<>();

    protected Product() {
    }

    public Product(Long id, String name, String image, BigDecimal price) {
        this(id, name, name, image, price);
    }

    public Product(Long id, String name, String description, String image, BigDecimal price) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.image = image;
        this.price = price;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getImage() {
        return image;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public Set<Category> getCategories() {
        return categories;
    }

    /**
     * Replace already authorized, fully validated and normalized data on this managed instance.
     * The maintenance service owns business validation and the surrounding transaction.
     */
    public void replaceDetails(String name, String description, String image, BigDecimal price,
                               Collection<Category> selectedCategories) {
        Objects.requireNonNull(name, "Replacement name is required");
        Objects.requireNonNull(description, "Replacement description is required");
        Objects.requireNonNull(image, "Replacement image is required");
        Objects.requireNonNull(price, "Replacement price is required");
        // Snapshot before mutating: the selection may be this collection or a view backed by it.
        var selection = new HashSet<>(Objects.requireNonNull(selectedCategories, "Replacement categories are required"));
        selection.forEach(category -> Objects.requireNonNull(category, "Replacement category is required"));

        this.name = name;
        this.description = description;
        this.image = image;
        this.price = price;
        // Keep Hibernate's managed collection; only its associations change, never Category entities.
        this.categories.clear();
        this.categories.addAll(selection);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Product product)) {
            return false;
        }
        return Objects.equals(id, product.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
