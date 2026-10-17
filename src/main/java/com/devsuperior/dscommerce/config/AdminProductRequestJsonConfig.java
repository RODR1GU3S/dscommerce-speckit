package com.devsuperior.dscommerce.config;

import com.devsuperior.dscommerce.dto.AdminFieldErrorDTO;
import com.devsuperior.dscommerce.dto.ProductInputDiagnostic;
import com.devsuperior.dscommerce.dto.ProductInputDiagnostic.Field;
import com.devsuperior.dscommerce.dto.ProductInputDiagnostic.Representation;
import com.devsuperior.dscommerce.dto.ProductWriteRequestDTO;
import com.fasterxml.jackson.core.JsonLocation;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import org.springframework.boot.jackson.JsonComponent;

import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Streaming administrative input only; no shared coercion, duplicate-detection or mapper settings change. */
@JsonComponent
public class AdminProductRequestJsonConfig {
    public static class ProductWriteRequestDeserializer extends StdDeserializer<ProductWriteRequestDTO> {
        public ProductWriteRequestDeserializer() { super(ProductWriteRequestDTO.class); }

        @Override
        public ProductWriteRequestDTO deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            try {
                if (parser.currentToken() == null) parser.nextToken();
                if (parser.currentToken() != JsonToken.START_OBJECT) throw invalidBody(parser);
                var input = new Input();
                while (parser.nextToken() != JsonToken.END_OBJECT) {
                    if (parser.currentToken() != JsonToken.FIELD_NAME) throw invalidBody(parser);
                    String property = parser.currentName();
                    if (parser.nextToken() == null) throw invalidBody(parser);
                    input.readProperty(parser, property);
                }
                // A complete request is a single root object, not a prefix followed by another value/garbage.
                if (parser.nextToken() != null) throw invalidBody(parser);
                return input.request();
            } catch (InvalidProductBodyException failure) {
                throw failure;
            } catch (JsonProcessingException malformedJson) {
                // Discard partial field evidence and parser text that could contain received values.
                throw invalidBody(parser);
            }
        }

        @Override
        public ProductWriteRequestDTO getNullValue(DeserializationContext context) throws JsonMappingException {
            throw invalidBody(context.getParser());
        }
    }

    private static final class Input {
        private static final BigInteger MIN_LONG = BigInteger.valueOf(Long.MIN_VALUE);
        private static final BigInteger MAX_LONG = BigInteger.valueOf(Long.MAX_VALUE);
        private final Map<Field, Representation> fields = new EnumMap<>(Field.class);
        private final Map<Field, String> texts = new EnumMap<>(Field.class);
        private final Map<Integer, Representation> categoryElements = new HashMap<>();
        private final List<AdminFieldErrorDTO> errors = new ArrayList<>();
        private final Set<String> seen = new HashSet<>();
        private List<Long> categoryIds;

        private Input() {
            for (var field : Field.values()) fields.put(field, Representation.ABSENT);
        }

        private void readProperty(JsonParser parser, String property) throws IOException {
            Field field = field(property);
            boolean duplicate = !seen.add(property);
            if (field == null) {
                errors.add(error("body", "UNKNOWN_FIELD", "Unknown request property"));
                if (duplicate) errors.add(error("body", "DUPLICATE_FIELD", "Repeated request property"));
                parser.skipChildren();
                return;
            }
            if (duplicate) {
                if (fields.get(field) != Representation.DUPLICATE) {
                    // Neither occurrence of an ambiguous field is reliable for that field's rules/references.
                    texts.remove(field);
                    if (field == Field.CATEGORY_IDS) { categoryIds = null; categoryElements.clear(); }
                    errors.removeIf(error -> error.field().equals(field.jsonName())
                            || error.field().startsWith(field.jsonName() + "["));
                    errors.add(error(field.jsonName(), "DUPLICATE_FIELD", "Repeated request property"));
                    fields.put(field, Representation.DUPLICATE);
                }
                parser.skipChildren();
                return;
            }
            if (parser.currentToken() == JsonToken.VALUE_NULL) {
                fields.put(field, Representation.NULL);
            } else if (field == Field.CATEGORY_IDS && parser.currentToken() == JsonToken.START_ARRAY) {
                fields.put(field, Representation.READABLE);
                categoryIds = readCategories(parser);
            } else if (field != Field.CATEGORY_IDS && parser.currentToken() == JsonToken.VALUE_STRING) {
                fields.put(field, Representation.READABLE);
                texts.put(field, parser.getText());
            } else {
                fields.put(field, Representation.INVALID_TYPE);
                errors.add(error(field.jsonName(), "INVALID_TYPE", "Invalid product field type"));
                parser.skipChildren();
            }
        }

        private List<Long> readCategories(JsonParser parser) throws IOException {
            var ids = new ArrayList<Long>();
            while (parser.nextToken() != JsonToken.END_ARRAY) {
                if (parser.currentToken() == null) throw invalidBody(parser);
                int index = ids.size();
                String field = "categoryIds[" + index + "]";
                Long id = null;
                if (parser.currentToken() != JsonToken.VALUE_NUMBER_INT) {
                    categoryElements.put(index, parser.currentToken() == JsonToken.VALUE_NULL
                            ? Representation.NULL : Representation.INVALID_TYPE);
                    errors.add(error(field, "INVALID_TYPE", "Expected an integer category identifier"));
                    parser.skipChildren();
                } else {
                    BigInteger value = parser.getBigIntegerValue();
                    if (value.compareTo(MIN_LONG) < 0 || value.compareTo(MAX_LONG) > 0) {
                        categoryElements.put(index, Representation.INVALID_FORMAT);
                        errors.add(error(field, "INVALID_FORMAT", "Category identifier is not representable"));
                    } else if (value.signum() <= 0) {
                        categoryElements.put(index, Representation.INVALID_FORMAT);
                        errors.add(error(field, "NOT_POSITIVE", "Category identifier must be positive"));
                    } else {
                        categoryElements.put(index, Representation.READABLE);
                        id = value.longValueExact();
                    }
                }
                // Every element retains its original slot, including invalid elements and repetitions.
                ids.add(id);
            }
            return ids;
        }

        private ProductWriteRequestDTO request() {
            for (var field : Field.values()) {
                if (fields.get(field) == Representation.ABSENT || fields.get(field) == Representation.NULL) {
                    errors.add(error(field.jsonName(), "REQUIRED", "Required product field"));
                }
            }
            return new ProductWriteRequestDTO(texts.get(Field.NAME), texts.get(Field.DESCRIPTION), texts.get(Field.PRICE),
                    texts.get(Field.IMAGE), categoryIds, new ProductInputDiagnostic(fields, categoryElements, errors));
        }

        private static Field field(String property) {
            for (var field : Field.values()) if (field.jsonName().equals(property)) return field;
            return null;
        }
    }

    private static AdminFieldErrorDTO error(String field, String code, String message) {
        return new AdminFieldErrorDTO(field, code, message);
    }

    private static InvalidProductBodyException invalidBody(JsonParser parser) {
        return new InvalidProductBodyException(parser);
    }

    /** Only body evidence survives an unexaminable document; the future administrative advice maps this error. */
    public static final class InvalidProductBodyException extends JsonMappingException {
        private InvalidProductBodyException(JsonParser parser) {
            super(parser, "Invalid product maintenance body", JsonLocation.NA);
        }

        public AdminFieldErrorDTO getDiagnostic() {
            return error("body", "INVALID_BODY", "Invalid request body");
        }
    }
}
