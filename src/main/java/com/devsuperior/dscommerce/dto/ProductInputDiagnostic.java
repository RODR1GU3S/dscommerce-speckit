package com.devsuperior.dscommerce.dto;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonIgnoreType;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Immutable internal representation evidence and safe diagnoses, never an HTTP input/output DTO.
 * The reader and services will produce these values; this type makes no parsing or business decisions.
 */
@JsonIgnoreType
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.NONE,
        getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE,
        setterVisibility = JsonAutoDetect.Visibility.NONE, creatorVisibility = JsonAutoDetect.Visibility.NONE)
public final class ProductInputDiagnostic {
    private final Map<Field, Representation> fields;
    private final Map<Integer, Representation> categoryElements;
    private final List<AdminFieldErrorDTO> errors;

    public ProductInputDiagnostic(Map<Field, Representation> fields, Map<Integer, Representation> categoryElements,
                                  List<AdminFieldErrorDTO> errors) {
        this.fields = Map.copyOf(fields);
        this.categoryElements = Map.copyOf(categoryElements);
        this.errors = List.copyOf(errors);
        if (this.categoryElements.keySet().stream().anyMatch(index -> index < 0)) {
            throw new IllegalArgumentException("Internal category indices must be zero-based");
        }
    }

    public Map<Field, Representation> fields() { return fields; }
    public Map<Integer, Representation> categoryElements() { return categoryElements; }
    public List<AdminFieldErrorDTO> errors() { return errors; }

    /** Do not infer JSON absence/null/type from Java values before the scoped reader is implemented. */
    public static ProductInputDiagnostic unexamined() {
        var fields = new EnumMap<Field, Representation>(Field.class);
        for (var field : Field.values()) fields.put(field, Representation.UNEXAMINED);
        return new ProductInputDiagnostic(fields, Map.of(), List.of());
    }

    public enum Field {
        NAME("name"), DESCRIPTION("description"), PRICE("price"), IMAGE("image"), CATEGORY_IDS("categoryIds");

        private final String jsonName;
        Field(String jsonName) { this.jsonName = jsonName; }
        public String jsonName() { return jsonName; }
    }

    /** READABLE describes representation only; it does not mean the value satisfies business rules. */
    public enum Representation {
        UNEXAMINED, ABSENT, NULL, READABLE, INVALID_TYPE, INVALID_FORMAT, DUPLICATE
    }

    /** Separate values supplied by future normalization, without mutating the request or exposing entities. */
    @JsonIgnoreType
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.NONE,
            getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE,
            setterVisibility = JsonAutoDetect.Visibility.NONE, creatorVisibility = JsonAutoDetect.Visibility.NONE)
    public static final class NormalizedValues {
        private final String name;
        private final String description;
        private final BigDecimal price;
        private final String image;
        private final List<Long> categoryIds;

        public NormalizedValues(String name, String description, BigDecimal price, String image, List<Long> categoryIds) {
            this.name = name;
            this.description = description;
            this.price = price;
            this.image = image;
            this.categoryIds = List.copyOf(categoryIds);
        }

        public String name() { return name; }
        public String description() { return description; }
        public BigDecimal price() { return price; }
        public String image() { return image; }
        public List<Long> categoryIds() { return categoryIds; }
    }
}
