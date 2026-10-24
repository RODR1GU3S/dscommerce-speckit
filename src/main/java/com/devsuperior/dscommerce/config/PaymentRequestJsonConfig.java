package com.devsuperior.dscommerce.config;

import com.devsuperior.dscommerce.dto.PaymentRegistrationRequestDTO;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import org.springframework.boot.jackson.JsonComponent;

import java.io.IOException;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.regex.Pattern;

/** Payment input only: pure local parsing, with no shared mapper, clock or domain IO changes. */
@JsonComponent
public class PaymentRequestJsonConfig {
    private static final Pattern OCCURRENCE = Pattern.compile(
            "[0-9]{4}-(?:0[1-9]|1[0-2])-(?:0[1-9]|[12][0-9]|3[01])"
                    + "T(?:[01][0-9]|2[0-3]):[0-5][0-9]:[0-5][0-9]"
                    + "(?:\\.[0-9]{1,9})?(?:Z|[+-](?:(?:0[0-9]|1[0-7]):[0-5][0-9]|18:00))");

    public static class PaymentRegistrationRequestDeserializer extends StdDeserializer<PaymentRegistrationRequestDTO> {
        public PaymentRegistrationRequestDeserializer() {
            super(PaymentRegistrationRequestDTO.class);
        }

        @Override
        public PaymentRegistrationRequestDTO deserialize(JsonParser parser, DeserializationContext context)
                throws IOException {
            try {
                requireToken(parser, JsonToken.START_OBJECT);
                Instant occurredAt = null;
                boolean seen = false;
                while (parser.nextToken() != JsonToken.END_OBJECT) {
                    requireToken(parser, JsonToken.FIELD_NAME);
                    if (!"occurredAt".equals(parser.currentName()) || seen) {
                        throw invalidInput(parser);
                    }
                    seen = true;
                    if (parser.nextToken() != JsonToken.VALUE_NULL) {
                        requireToken(parser, JsonToken.VALUE_STRING);
                        occurredAt = readOccurrence(parser);
                    }
                }
                if (parser.nextToken() != null) {
                    throw invalidInput(parser);
                }
                // Missing/null values remain visible to @NotNull at the future MVC boundary.
                return new PaymentRegistrationRequestDTO(occurredAt);
            } catch (JsonProcessingException malformedDocument) {
                // Never propagate parser diagnostics containing supplied values as application messages.
                throw invalidInput(parser);
            }
        }

        @Override
        public PaymentRegistrationRequestDTO getNullValue(DeserializationContext context) throws JsonMappingException {
            throw invalidInput(context.getParser());
        }
    }

    private static Instant readOccurrence(JsonParser parser) throws IOException {
        String text = parser.getText();
        if (!OCCURRENCE.matcher(text).matches() || text.startsWith("0000") || text.endsWith("-00:00")) {
            throw invalidInput(parser);
        }
        try {
            Instant instant = OffsetDateTime.parse(text, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant();
            int utcYear = instant.atOffset(ZoneOffset.UTC).getYear();
            if (utcYear < 1 || utcYear > 9999 || instant.getNano() % 1_000_000 != 0) {
                throw invalidInput(parser);
            }
            return instant;
        } catch (DateTimeException invalidOccurrence) {
            throw invalidInput(parser);
        }
    }

    private static void requireToken(JsonParser parser, JsonToken expected) throws JsonMappingException {
        if (parser.currentToken() != expected) {
            throw invalidInput(parser);
        }
    }

    private static JsonMappingException invalidInput(JsonParser parser) {
        return JsonMappingException.from(parser, "Invalid payment data");
    }
}
