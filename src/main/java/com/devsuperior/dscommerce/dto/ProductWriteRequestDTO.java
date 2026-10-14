package com.devsuperior.dscommerce.dto;

import com.devsuperior.dscommerce.validation.ValidProductInput;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Immutable administrative input. Raw values are retained for the future scoped reader/validator;
 * construction does not normalize, validate business rules or establish category existence.
 */
@ValidProductInput
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.NONE,
        getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE,
        setterVisibility = JsonAutoDetect.Visibility.NONE, creatorVisibility = JsonAutoDetect.Visibility.NONE)
public final class ProductWriteRequestDTO {
    private final String name;
    private final String description;
    private final String price;
    private final String image;
    private final List<Long> categoryIds;
    private final ProductInputDiagnostic diagnostic;

    /** Direct Java input has no JSON token/presence evidence; the scoped reader will supply it separately. */
    public ProductWriteRequestDTO(String name, String description, String price, String image, List<Long> categoryIds) {
        this(name, description, price, image, categoryIds, ProductInputDiagnostic.unexamined());
    }

    /** Internal construction point for the scoped reader, not a Jackson creator or an HTTP metadata property. */
    public ProductWriteRequestDTO(String name, String description, String price, String image, List<Long> categoryIds,
                                  ProductInputDiagnostic diagnostic) {
        this.name = name;
        this.description = description;
        this.price = price;
        this.image = image;
        // Keep original positions, repetitions and null placeholders for invalid/null elements.
        this.categoryIds = categoryIds == null ? null : Collections.unmodifiableList(new ArrayList<>(categoryIds));
        this.diagnostic = Objects.requireNonNull(diagnostic, "Internal input diagnostic is required");
    }

    @JsonProperty("name") public String name() { return name; }
    @JsonProperty("description") public String description() { return description; }
    @JsonProperty("price") public String price() { return price; }
    @JsonProperty("image") public String image() { return image; }
    @JsonProperty("categoryIds") public List<Long> categoryIds() { return categoryIds; }

    @JsonIgnore public ProductInputDiagnostic diagnostic() { return diagnostic; }
}
