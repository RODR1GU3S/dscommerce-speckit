package com.devsuperior.dscommerce.config;

import com.devsuperior.dscommerce.dto.CustomerRegistrationRequestDTO;
import com.fasterxml.jackson.core.JsonLocation;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import org.springframework.boot.jackson.JsonComponent;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

/** Registration input only: raw strings and scoped parsing, without shared mapper changes. */
@JsonComponent
public class CustomerRegistrationRequestJsonConfig {

    private static final String INVALID_INPUT = "Invalid customer registration data";

    public static class CustomerRegistrationRequestDeserializer extends StdDeserializer<CustomerRegistrationRequestDTO> {

        public CustomerRegistrationRequestDeserializer() {
            super(CustomerRegistrationRequestDTO.class);
        }

        @Override
        public CustomerRegistrationRequestDTO deserialize(JsonParser parser, DeserializationContext context)
                throws IOException {
            try {
                requireToken(parser, JsonToken.START_OBJECT);
                Set<String> seen = new HashSet<>();
                String name = null;
                String fullName = null;
                String email = null;
                String phone = null;
                String birthDate = null;
                String password = null;
                while (parser.nextToken() != JsonToken.END_OBJECT) {
                    requireToken(parser, JsonToken.FIELD_NAME);
                    String property = parser.currentName();
                    if (!seen.add(property)) {
                        throw invalidInput();
                    }
                    switch (property) {
                        case "name" -> name = readStringOrNull(parser);
                        case "fullName" -> fullName = readStringOrNull(parser);
                        case "email" -> email = readStringOrNull(parser);
                        case "phone" -> phone = readStringOrNull(parser);
                        case "birthDate" -> birthDate = readStringOrNull(parser);
                        case "password" -> password = readStringOrNull(parser);
                        default -> throw invalidInput();
                    }
                }
                if (parser.nextToken() != null) {
                    throw invalidInput();
                }
                // Missing/null fields remain visible to the real local Bean Validation constraint.
                return new CustomerRegistrationRequestDTO(name, fullName, email, phone, birthDate, password);
            } catch (JsonProcessingException malformedDocument) {
                // Discard parser messages, source locations and causes that may contain raw secrets.
                throw invalidInput();
            }
        }

        @Override
        public CustomerRegistrationRequestDTO getNullValue(DeserializationContext context) throws JsonMappingException {
            throw invalidInput();
        }
    }

    private static String readStringOrNull(JsonParser parser) throws IOException {
        if (parser.nextToken() == JsonToken.VALUE_NULL) {
            return null;
        }
        requireToken(parser, JsonToken.VALUE_STRING);
        return parser.getText();
    }

    private static void requireToken(JsonParser parser, JsonToken expected) throws JsonMappingException {
        if (parser.currentToken() != expected) {
            throw invalidInput();
        }
    }

    private static JsonMappingException invalidInput() {
        // No processor/source reference, original cause, rejected field name or received value.
        return new JsonMappingException(null, INVALID_INPUT, JsonLocation.NA);
    }
}
