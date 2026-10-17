package com.devsuperior.dscommerce.controllers;

import org.springframework.util.MultiValueMap;

import java.util.List;

/** HTTP representation checks shared only by the administrative GET controllers; no persistence or business rules. */
public final class AdminQueryParameters {
    private static final List<String> LIST_FIELDS = List.of("name", "page", "size");

    private AdminQueryParameters() { }

    public static void requireListQuery(MultiValueMap<String, String> query) {
        if (query.keySet().stream().anyMatch(field -> !LIST_FIELDS.contains(field))) {
            throw new InvalidParameterException("body");
        }
        for (var field : LIST_FIELDS) {
            if (query.containsKey(field) && query.get(field).size() != 1) {
                throw new InvalidParameterException(field);
            }
        }
    }

    public static void requireNoQuery(MultiValueMap<String, String> query) {
        if (!query.isEmpty()) throw new InvalidParameterException("body");
    }

    public static int page(MultiValueMap<String, String> query) {
        return query.containsKey("page") ? (int) number("page", query.getFirst("page"), 0, Integer.MAX_VALUE) : 0;
    }

    public static int size(MultiValueMap<String, String> query) {
        return query.containsKey("size") ? (int) number("size", query.getFirst("size"), 1, 50) : 12;
    }

    public static long productId(String value) {
        return number("id", value, 1, Long.MAX_VALUE);
    }

    private static long number(String field, String value, long minimum, long maximum) {
        final long parsed;
        try {
            parsed = Long.parseLong(value);
        } catch (NumberFormatException exception) {
            // Do not propagate the conversion exception, which can contain the received value.
            throw new InvalidParameterException(field);
        }
        if (parsed < minimum || parsed > maximum) throw new InvalidParameterException(field);
        return parsed;
    }

    public static final class InvalidParameterException extends RuntimeException {
        private final String field;

        private InvalidParameterException(String field) {
            super("Invalid administrative request parameter");
            this.field = field;
        }

        public String getField() { return field; }
    }
}
