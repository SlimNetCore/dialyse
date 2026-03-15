package com.hemodialyse.backend.infrastructure.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * At startup, ensure seed users have proper BCrypt password hashes.
 * This handles the case where V9 migration inserted placeholder hashes.
 */
@Component
public class SeedPasswordInitializer implements CommandLineRunner {

    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;

    public SeedPasswordInitializer(JdbcTemplate jdbc, PasswordEncoder encoder) {
        this.jdbc = jdbc;
        this.encoder = encoder;
    }

    @Override
    public void run(String... args) {
        ensurePassword("admin", "admin123");
        ensurePassword("medecin", "medecin123");
    }

    private void ensurePassword(String username, String clearPassword) {
        var users = jdbc.queryForList("SELECT id, password_hash FROM app_user WHERE username = ?", username);
        if (users.isEmpty()) return;

        String currentHash = (String) users.get(0).get("PASSWORD_HASH");
        // If the hash is not a valid BCrypt hash or doesn't match, re-hash
        if (currentHash == null || !currentHash.startsWith("$2a$") || !encoder.matches(clearPassword, currentHash)) {
            String newHash = encoder.encode(clearPassword);
            jdbc.update("UPDATE app_user SET password_hash = ? WHERE username = ?", newHash, username);
        }
    }
}

