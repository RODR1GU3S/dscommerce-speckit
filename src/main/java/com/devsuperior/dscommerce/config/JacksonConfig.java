package com.devsuperior.dscommerce.config;

import com.devsuperior.dscommerce.dto.LoginRequestDTO;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import org.springframework.boot.jackson.JsonComponent;

import java.io.IOException;
import java.util.Iterator;
import java.util.Set;

@JsonComponent
public class JacksonConfig {

    private static final Set<String> LOGIN_REQUEST_PROPERTIES = Set.of("name", "password");

    public static class LoginRequestDeserializer extends StdDeserializer<LoginRequestDTO> {

        public LoginRequestDeserializer() {
            super(LoginRequestDTO.class);
        }

        @Override
        public LoginRequestDTO deserialize(
                JsonParser parser,
                DeserializationContext context) throws IOException {
            JsonNode request = parser.getCodec().readTree(parser);

            if (!request.isObject() || containsUnknownProperty(request)) {
                throw JsonMappingException.from(parser, "Invalid login data");
            }

            return new LoginRequestDTO(
                    readStringValue(parser, request.get("name")),
                    readStringValue(parser, request.get("password")));
        }

        private static boolean containsUnknownProperty(JsonNode request) {
            Iterator<String> properties = request.fieldNames();
            while (properties.hasNext()) {
                if (!LOGIN_REQUEST_PROPERTIES.contains(properties.next())) {
                    return true;
                }
            }
            return false;
        }

        private static String readStringValue(JsonParser parser, JsonNode value)
                throws IOException {
            if (value == null || value.isNull()) {
                return null;
            }
            return parser.getCodec().treeToValue(value, String.class);
        }
    }
}
