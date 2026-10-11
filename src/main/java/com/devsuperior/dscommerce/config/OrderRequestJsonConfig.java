package com.devsuperior.dscommerce.config;

import com.devsuperior.dscommerce.dto.OrderItemRequestDTO;
import com.devsuperior.dscommerce.dto.OrderRequestDTO;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import org.springframework.boot.jackson.JsonComponent;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Streaming readers for order input only; shared Jackson settings remain unchanged. */
@JsonComponent
public class OrderRequestJsonConfig {

    public static class OrderRequestDeserializer extends StdDeserializer<OrderRequestDTO> {

        public OrderRequestDeserializer() {
            super(OrderRequestDTO.class);
        }

        @Override
        public OrderRequestDTO deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            requireToken(parser, JsonToken.START_OBJECT);
            List<OrderItemRequestDTO> items = null;
            boolean itemsSeen = false;
            while (parser.nextToken() != JsonToken.END_OBJECT) {
                requireToken(parser, JsonToken.FIELD_NAME);
                if (!"items".equals(parser.currentName()) || itemsSeen) {
                    throw invalidInput(parser);
                }
                itemsSeen = true;
                parser.nextToken();
                items = readItems(parser, context);
            }
            if (parser.nextToken() != null) {
                throw invalidInput(parser);
            }
            return new OrderRequestDTO(items);
        }

        private static List<OrderItemRequestDTO> readItems(JsonParser parser, DeserializationContext context)
                throws IOException {
            if (parser.currentToken() == JsonToken.VALUE_NULL) {
                return null;
            }
            requireToken(parser, JsonToken.START_ARRAY);
            var items = new ArrayList<OrderItemRequestDTO>();
            while (parser.nextToken() != JsonToken.END_ARRAY) {
                items.add(parser.currentToken() == JsonToken.VALUE_NULL
                        ? null : context.readValue(parser, OrderItemRequestDTO.class));
            }
            return items;
        }
    }

    public static class OrderItemRequestDeserializer extends StdDeserializer<OrderItemRequestDTO> {

        public OrderItemRequestDeserializer() {
            super(OrderItemRequestDTO.class);
        }

        @Override
        public OrderItemRequestDTO deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            requireToken(parser, JsonToken.START_OBJECT);
            Set<String> seen = new HashSet<>();
            Long productId = null;
            Integer quantity = null;
            while (parser.nextToken() != JsonToken.END_OBJECT) {
                requireToken(parser, JsonToken.FIELD_NAME);
                String property = parser.currentName();
                if (!seen.add(property)) {
                    throw invalidInput(parser);
                }
                switch (property) {
                    case "productId" -> {
                        parser.nextToken();
                        requireIntegerOrNull(parser);
                        productId = parser.currentToken() == JsonToken.VALUE_NULL ? null : parser.getLongValue();
                    }
                    case "quantity" -> {
                        parser.nextToken();
                        requireIntegerOrNull(parser);
                        quantity = parser.currentToken() == JsonToken.VALUE_NULL ? null : parser.getIntValue();
                    }
                    default -> throw invalidInput(parser);
                }
            }
            return new OrderItemRequestDTO(productId, quantity);
        }
    }

    private static void requireIntegerOrNull(JsonParser parser) throws JsonMappingException {
        if (parser.currentToken() != JsonToken.VALUE_NULL) {
            requireToken(parser, JsonToken.VALUE_NUMBER_INT);
        }
    }

    private static void requireToken(JsonParser parser, JsonToken expected) throws JsonMappingException {
        if (parser.currentToken() != expected) {
            throw invalidInput(parser);
        }
    }

    private static JsonMappingException invalidInput(JsonParser parser) {
        return JsonMappingException.from(parser, "Invalid order data");
    }
}
