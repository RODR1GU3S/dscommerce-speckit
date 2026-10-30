package com.devsuperior.dscommerce.security;

import com.devsuperior.dscommerce.validation.RegistrationInputRules;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Base64;
import java.util.regex.Pattern;

/** Argon2 is private to registration; this component is not another PasswordEncoder bean. */
@Component
public class RegistrationPasswordHasher {

    private static final int SALT_LENGTH = 16;
    private static final int HASH_LENGTH = 32;
    private static final int PARALLELISM = 1;
    private static final int MEMORY_KIB = 19456;
    private static final int ITERATIONS = 2;
    private static final int ENCODED_LENGTH = 97;
    private static final String PREFIX = "$argon2id$v=19$m=19456,t=2,p=1$";
    private static final Pattern FORMAT = Pattern.compile(Pattern.quote(PREFIX)
            + "([A-Za-z0-9+/]{22})\\$([A-Za-z0-9+/]{43})");

    private final Argon2PasswordEncoder encoder = new Argon2PasswordEncoder(
            SALT_LENGTH, HASH_LENGTH, PARALLELISM, MEMORY_KIB, ITERATIONS);

    /** Only a new registration uses this method; the exact validated String reaches the encoder. */
    public String hash(String rawPassword) {
        if (!RegistrationInputRules.isValidPassword(rawPassword)) {
            throw new IllegalArgumentException("Invalid customer registration data");
        }
        try {
            String encoded = encoder.encode(rawPassword);
            if (!supports(encoded)) {
                throw new IllegalStateException("Unsupported generated registration password format");
            }
            return encoded;
        } catch (RuntimeException failure) {
            // Encoder/parser exception chains may carry secret material. Do not retain or log them.
            throw new IllegalStateException("Customer registration password could not be processed");
        }
    }

    /** Package-local verification path; registration rules must never restrict a login candidate. */
    boolean matches(String rawPassword, String encodedPassword) {
        if (rawPassword == null || !supports(encodedPassword)) { return false; }
        try {
            return encoder.matches(rawPassword, encodedPassword);
        } catch (IllegalStateException failure) {
            // A accepts malformed UTF-16, but it cannot match a valid registration password.
            // Classify only BC 1.86's conversion signal; other encoder failures must propagate.
            if (isMalformedCandidateFailure(rawPassword, failure)) { return false; }
            throw failure;
        }
    }

    private static boolean isMalformedCandidateFailure(String rawPassword, IllegalStateException failure) {
        if (RegistrationInputRules.hasValidUnicode(rawPassword)
                || failure.getClass() != IllegalStateException.class
                || failure.getCause() != null || failure.getSuppressed().length != 0) {
            return false;
        }
        var trace = failure.getStackTrace();
        if (trace.length == 0 || !"org.bouncycastle.util.Strings".equals(trace[0].getClassName())
                || !"toUTF8ByteArray".equals(trace[0].getMethodName())) {
            return false;
        }
        // Match fixed library literals only; never log or retain diagnostic material.
        String message = failure.getMessage();
        return "invalid UTF-16 high surrogate".equals(message)
                || "invalid UTF-16 low surrogate".equals(message)
                || "invalid UTF-16 codepoint (truncated surrogate pair)".equals(message);
    }

    static boolean supports(String encodedPassword) {
        // Close the profile and total size before allocating groups, decoding or invoking the KDF.
        if (encodedPassword == null || encodedPassword.length() != ENCODED_LENGTH
                || !encodedPassword.startsWith(PREFIX)) {
            return false;
        }
        var matcher = FORMAT.matcher(encodedPassword);
        if (!matcher.matches()) { return false; }
        return canonicalBase64(matcher.group(1), SALT_LENGTH)
                && canonicalBase64(matcher.group(2), HASH_LENGTH);
    }

    private static boolean canonicalBase64(String encoded, int expectedBytes) {
        try {
            byte[] decoded = Base64.getDecoder().decode(encoded);
            return decoded.length == expectedBytes
                    && Base64.getEncoder().withoutPadding().encodeToString(decoded).equals(encoded);
        } catch (IllegalArgumentException invalidEncoding) {
            return false;
        }
    }
}
