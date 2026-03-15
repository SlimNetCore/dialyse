package com.hemodialyse.backend.application.auth;

import com.hemodialyse.backend.infrastructure.security.JwtTokenProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class AuthService {

    private final JdbcTemplate jdbcTemplate;
    private final JwtTokenProvider jwtTokenProvider;
    private final String demoUser;
    private final String demoPassword;

    public AuthService(
        JdbcTemplate jdbcTemplate,
        JwtTokenProvider jwtTokenProvider,
        @Value("${app.auth.demo-user:admin}") String demoUser,
        @Value("${app.auth.demo-password:admin123}") String demoPassword
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.jwtTokenProvider = jwtTokenProvider;
        this.demoUser = demoUser;
        this.demoPassword = demoPassword;
    }

    public LoginResult login(UUID centerId, String username, String password) {
        // Validate credentials (demo mode)
        if (!demoUser.equals(username) || !demoPassword.equals(password)) {
            throw new IllegalArgumentException("Identifiants invalides");
        }

        // Verify user has access to the center
        Integer assignment = jdbcTemplate.queryForObject(
            "SELECT COUNT(1) FROM user_center_assignment WHERE user_id = ? AND center_id = ?",
            Integer.class,
            username,
            centerId
        );

        if (assignment == null || assignment == 0) {
            throw new IllegalStateException("Utilisateur non autorise sur ce centre");
        }

        // Get center name
        String centerName = jdbcTemplate.queryForObject(
            "SELECT name FROM centers WHERE id = ?",
            String.class,
            centerId
        );

        // Get user role for this center
        String roleCode = jdbcTemplate.queryForObject(
            "SELECT role_code FROM user_center_assignment WHERE user_id = ? AND center_id = ?",
            String.class,
            username,
            centerId
        );

        List<String> roles = List.of("ROLE_" + (roleCode != null ? roleCode : "ADMIN"));

        // Generate JWT token using JwtTokenProvider
        String token = jwtTokenProvider.generateToken(username, username, roles, centerId.toString());

        return new LoginResult(token, username, centerId, centerName, roles);
    }

    public record LoginResult(String token, String username, UUID centerId, String centerName, List<String> roles) {
    }
}
