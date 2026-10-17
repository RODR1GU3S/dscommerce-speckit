package com.devsuperior.dscommerce.services;

import com.devsuperior.dscommerce.dto.AdminFieldErrorDTO;
import com.devsuperior.dscommerce.dto.ProductInputDiagnostic;
import com.devsuperior.dscommerce.dto.ProductInputDiagnostic.Field;
import com.devsuperior.dscommerce.dto.ProductInputDiagnostic.NormalizedValues;
import com.devsuperior.dscommerce.dto.ProductInputDiagnostic.Representation;
import com.devsuperior.dscommerce.dto.ProductWriteRequestDTO;
import com.fasterxml.jackson.annotation.JsonIgnoreType;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/** Local administrative policy only: no persistence, account/category lookup, network or request mutation. */
@Service
public class ProductInputValidationService {
    private static final Pattern DECIMAL = Pattern.compile("-?(0|[1-9][0-9]*)(\\.[0-9]+)?");

    public Result validate(ProductWriteRequestDTO input) {
        Objects.requireNonNull(input, "Product input is required");
        var errors = new ArrayList<>(input.diagnostic().errors());
        String name = examinable(input, Field.NAME) ? text(input.name(), Field.NAME, 3, 80, errors) : null;
        String description = examinable(input, Field.DESCRIPTION)
                ? text(input.description(), Field.DESCRIPTION, 10, Integer.MAX_VALUE, errors) : null;
        BigDecimal price = examinable(input, Field.PRICE) ? money(input.price(), errors) : null;
        String image = examinable(input, Field.IMAGE) ? image(input.image(), errors) : null;

        var diagnostic = new ProductInputDiagnostic(input.diagnostic().fields(), input.diagnostic().categoryElements(), errors);
        NormalizedValues normalized = null;
        // Transport only a complete candidate. This does not approve category selection/existence or authorize writing.
        if (errors.isEmpty() && name != null && description != null && price != null && image != null
                && input.categoryIds() != null && input.categoryIds().stream().allMatch(Objects::nonNull)) {
            normalized = new NormalizedValues(name, description, price, image, input.categoryIds());
        }
        return new Result(diagnostic, normalized);
    }

    private static boolean examinable(ProductWriteRequestDTO input, Field field) {
        var state = input.diagnostic().fields().getOrDefault(field, Representation.UNEXAMINED);
        // The reader already diagnosed absent/null/wrong/ambiguous fields; do not invent secondary rule errors.
        return state == Representation.READABLE || state == Representation.UNEXAMINED;
    }

    private static String text(String raw, Field field, int minimum, int maximum, List<AdminFieldErrorDTO> errors) {
        if (raw == null) {
            add(errors, field, "REQUIRED", "Required product field");
            return null;
        }
        String normalized = trimExternalWhitespace(raw);
        int count = normalized.codePointCount(0, normalized.length());
        if (count == 0) {
            add(errors, field, "REQUIRED", "Required product field");
        } else if (count < minimum || count > maximum) {
            add(errors, field, "INVALID_LENGTH", "Invalid product text length");
        } else return normalized;
        return null;
    }

    private static String trimExternalWhitespace(String raw) {
        int start = 0;
        int end = raw.length();
        while (start < end) {
            int codePoint = raw.codePointAt(start);
            if (!externalWhitespace(codePoint)) break;
            start += Character.charCount(codePoint);
        }
        while (end > start) {
            int codePoint = raw.codePointBefore(end);
            if (!externalWhitespace(codePoint)) break;
            end -= Character.charCount(codePoint);
        }
        return raw.substring(start, end);
    }

    private static boolean externalWhitespace(int codePoint) {
        return Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint);
    }

    private static BigDecimal money(String raw, List<AdminFieldErrorDTO> errors) {
        if (raw == null || raw.isEmpty()) {
            add(errors, Field.PRICE, "REQUIRED", "Required product field");
            return null;
        }
        if (!DECIMAL.matcher(raw).matches()) {
            add(errors, Field.PRICE, "INVALID_FORMAT", "Invalid decimal price format");
            return null;
        }
        BigDecimal value = new BigDecimal(raw);
        if (value.signum() <= 0) {
            add(errors, Field.PRICE, "NOT_POSITIVE", "Price must be positive");
            return null;
        }
        try {
            BigDecimal normalized = value.setScale(2, RoundingMode.UNNECESSARY);
            if (normalized.precision() <= 19) return normalized;
        } catch (ArithmeticException unrepresentable) {
            // No rounding and no continuation with a lossy value.
        }
        add(errors, Field.PRICE, "UNREPRESENTABLE_MONEY", "Price is not representable as decimal money");
        return null;
    }

    private static String image(String raw, List<AdminFieldErrorDTO> errors) {
        if (raw == null || raw.isEmpty()) {
            add(errors, Field.IMAGE, "REQUIRED", "Required product field");
            return null;
        }
        try {
            URI uri = new URI(raw).parseServerAuthority();
            String scheme = uri.getScheme();
            if (uri.isAbsolute() && !uri.isOpaque() && uri.getHost() != null
                    && ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) return raw;
        } catch (URISyntaxException invalid) {
            // Syntactic diagnosis only, without echoing received values or resolving a host.
        }
        add(errors, Field.IMAGE, "INVALID_IMAGE_URL", "Invalid absolute HTTP or HTTPS image URL");
        return null;
    }

    private static void add(List<AdminFieldErrorDTO> errors, Field field, String code, String message) {
        errors.add(new AdminFieldErrorDTO(field.jsonName(), code, message));
    }

    /** Internal local-policy result. A normalized candidate still needs reference/authorization/transaction checks. */
    @JsonIgnoreType
    public record Result(ProductInputDiagnostic diagnostic, NormalizedValues normalizedValues) { }
}
