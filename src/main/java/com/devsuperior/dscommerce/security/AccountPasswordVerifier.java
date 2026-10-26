package com.devsuperior.dscommerce.security;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/** Selects verification solely by the complete supported stored format; never upgrades a hash. */
@Component
public class AccountPasswordVerifier {

    private static final Pattern BCRYPT = Pattern.compile(
            "\\$2[aby]?\\$(?:0[4-9]|[12][0-9]|3[01])\\$[./A-Za-z0-9]{53}");

    private final PasswordEncoder bcrypt;
    private final RegistrationPasswordHasher registration;

    public AccountPasswordVerifier(PasswordEncoder bcrypt, RegistrationPasswordHasher registration) {
        this.bcrypt = bcrypt;
        this.registration = registration;
    }

    /** False is a mismatch/unsupported format; a recognized encoder failure remains an exception. */
    public boolean matches(String rawPassword, String storedHash) {
        if (rawPassword == null || storedHash == null) { return false; }
        boolean legacy = supportsBcrypt(storedHash);
        if (!legacy && !RegistrationPasswordHasher.supports(storedHash)) { return false; }
        try {
            // No registration size/Unicode/whitespace rule is applied to the historical login domain.
            return legacy ? bcrypt.matches(rawPassword, storedHash) : registration.matches(rawPassword, storedHash);
        } catch (RuntimeException failure) {
            // A recognized encoder's operational failure is not a credentials mismatch.
            // T025 connects this safe failure to the existing authentication 500 boundary.
            throw new IllegalStateException("Authentication could not be completed");
        }
    }

    private static boolean supportsBcrypt(String storedHash) {
        return (storedHash.length() == 59 || storedHash.length() == 60) && BCRYPT.matcher(storedHash).matches();
    }
}
