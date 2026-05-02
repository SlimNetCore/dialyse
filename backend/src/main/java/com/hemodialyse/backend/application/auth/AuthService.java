package com.hemodialyse.backend.application.auth;

import com.hemodialyse.backend.infrastructure.security.JwtTokenProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class AuthService {

    private final JdbcTemplate jdbc;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;

    public AuthService(JdbcTemplate jdbc, JwtTokenProvider jwtTokenProvider, PasswordEncoder passwordEncoder) {
        this.jdbc = jdbc;
        this.jwtTokenProvider = jwtTokenProvider;
        this.passwordEncoder = passwordEncoder;
    }

    public LoginResult login(UUID centerId, String username, String password) {
        // 1. Load user from app_user table
        var users = jdbc.queryForList(
            "SELECT id, username, password_hash, full_name, active FROM app_user WHERE username = ?", username
        );
        if (users.isEmpty()) {
            throw new IllegalArgumentException("Identifiants invalides");
        }
        var user = users.get(0);
        boolean active = Boolean.TRUE.equals(user.get("ACTIVE"));
        if (!active) {
            throw new IllegalStateException("Compte utilisateur désactivé");
        }

        // 2. Verify password with BCrypt
        String hash = (String) user.get("PASSWORD_HASH");
        if (!passwordEncoder.matches(password, hash)) {
            throw new IllegalArgumentException("Identifiants invalides");
        }

        UUID userId = (UUID) user.get("ID");

        // 3. Verify user has access to the requested center
        Integer centerAccess = jdbc.queryForObject(
            "SELECT COUNT(1) FROM app_user_center WHERE user_id = ? AND center_id = ?",
            Integer.class, userId, centerId
        );
        if (centerAccess == null || centerAccess == 0) {
            throw new IllegalStateException("Utilisateur non autorisé sur ce centre");
        }

        // 4. Load center name
        String centerName = jdbc.queryForObject("SELECT name FROM centers WHERE id = ?", String.class, centerId);

        // 5. Load roles for this user
        List<String> roles = jdbc.queryForList(
            "SELECT r.code FROM app_role r INNER JOIN app_user_role ur ON ur.role_id = r.id WHERE ur.user_id = ?",
            String.class, userId
        );
        List<String> prefixedRoles = roles.stream().map(r -> "ROLE_" + r).toList();

        // 6. Generate JWT
        String fullName = (String) user.get("FULL_NAME");
        String token = jwtTokenProvider.generateToken(username, userId.toString(), prefixedRoles, centerId.toString());

        return new LoginResult(token, username, fullName, userId, centerId, centerName, prefixedRoles);
    }

    public LoginResult rebuildSession(UUID centerId, String username, UUID userId, List<String> roles) {
        String centerName = jdbc.queryForObject("SELECT name FROM centers WHERE id = ?", String.class, centerId);
        String fullName = jdbc.queryForObject("SELECT full_name FROM app_user WHERE id = ?", String.class, userId);
        return new LoginResult("", username, fullName, userId, centerId, centerName, roles);
    }

    public record LoginResult(String token, String username, String fullName, UUID userId, UUID centerId, String centerName, List<String> roles) {
    }
}
