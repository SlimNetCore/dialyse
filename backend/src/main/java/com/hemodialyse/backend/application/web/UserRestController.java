package com.hemodialyse.backend.application.web;

import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1/users")
@PreAuthorize("hasRole('ADMIN')")
public class UserRestController {

    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;

    public UserRestController(JdbcTemplate jdbc, PasswordEncoder passwordEncoder) {
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
    }

    record CreateUserRequest(String username, String password, String email, String fullName, boolean active, List<UUID> roleIds, List<UUID> centerIds) {}
    record UpdateUserRequest(String email, String fullName, boolean active, String password, List<UUID> roleIds, List<UUID> centerIds) {}

    @GetMapping
    public ResponseEntity<?> listUsers(@RequestParam(required = false) UUID centerId) {
        String sql;
        List<Map<String, Object>> users;
        if (centerId != null) {
            sql = "SELECT u.id, u.username, u.email, u.full_name, u.active, u.created_at " +
                  "FROM app_user u INNER JOIN app_user_center uc ON uc.user_id = u.id WHERE uc.center_id = ? ORDER BY u.username";
            users = jdbc.queryForList(sql, centerId);
        } else {
            sql = "SELECT id, username, email, full_name, active, created_at FROM app_user ORDER BY username";
            users = jdbc.queryForList(sql);
        }
        // Enrich with roles and centers
        for (var u : users) {
            UUID uid = (UUID) u.get("ID");
            u.put("roles", jdbc.queryForList("SELECT r.id, r.code, r.name FROM app_role r INNER JOIN app_user_role ur ON ur.role_id = r.id WHERE ur.user_id = ?", uid));
            u.put("centers", jdbc.queryForList("SELECT c.id, c.name FROM centers c INNER JOIN app_user_center uc ON uc.center_id = c.id WHERE uc.user_id = ?", uid));
        }
        return ResponseEntity.ok(users);
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getUser(@PathVariable UUID id) {
        var users = jdbc.queryForList("SELECT id, username, email, full_name, active, created_at FROM app_user WHERE id = ?", id);
        if (users.isEmpty()) return ResponseEntity.notFound().build();
        var u = users.get(0);
        u.put("roles", jdbc.queryForList("SELECT r.id, r.code, r.name FROM app_role r INNER JOIN app_user_role ur ON ur.role_id = r.id WHERE ur.user_id = ?", id));
        u.put("centers", jdbc.queryForList("SELECT c.id, c.name FROM centers c INNER JOIN app_user_center uc ON uc.center_id = c.id WHERE uc.user_id = ?", id));
        return ResponseEntity.ok(u);
    }

    @PostMapping
    public ResponseEntity<?> createUser(@RequestBody CreateUserRequest req) {
        // Check uniqueness
        Integer count = jdbc.queryForObject("SELECT COUNT(1) FROM app_user WHERE username = ?", Integer.class, req.username());
        if (count != null && count > 0) {
            return ResponseEntity.badRequest().body(Map.of("error", "Nom d'utilisateur déjà existant"));
        }

        UUID id = UUID.randomUUID();
        String hash = passwordEncoder.encode(req.password());
        jdbc.update("INSERT INTO app_user (id, username, password_hash, email, full_name, active) VALUES (?,?,?,?,?,?)",
                id, req.username(), hash, req.email(), req.fullName(), req.active());

        if (req.roleIds() != null) {
            for (UUID roleId : req.roleIds()) {
                jdbc.update("INSERT INTO app_user_role (user_id, role_id) VALUES (?,?)", id, roleId);
            }
        }
        if (req.centerIds() != null) {
            for (UUID centerId : req.centerIds()) {
                jdbc.update("INSERT INTO app_user_center (user_id, center_id) VALUES (?,?)", id, centerId);
            }
        }
        return ResponseEntity.ok(Map.of("id", id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateUser(@PathVariable UUID id, @RequestBody UpdateUserRequest req) {
        if (req.password() != null && !req.password().isBlank()) {
            String hash = passwordEncoder.encode(req.password());
            jdbc.update("UPDATE app_user SET email=?, full_name=?, active=?, password_hash=? WHERE id=?",
                    req.email(), req.fullName(), req.active(), hash, id);
        } else {
            jdbc.update("UPDATE app_user SET email=?, full_name=?, active=? WHERE id=?",
                    req.email(), req.fullName(), req.active(), id);
        }

        // Sync roles
        if (req.roleIds() != null) {
            jdbc.update("DELETE FROM app_user_role WHERE user_id = ?", id);
            for (UUID roleId : req.roleIds()) {
                jdbc.update("INSERT INTO app_user_role (user_id, role_id) VALUES (?,?)", id, roleId);
            }
        }
        // Sync centers
        if (req.centerIds() != null) {
            jdbc.update("DELETE FROM app_user_center WHERE user_id = ?", id);
            for (UUID centerId : req.centerIds()) {
                jdbc.update("INSERT INTO app_user_center (user_id, center_id) VALUES (?,?)", id, centerId);
            }
        }
        return ResponseEntity.ok(Map.of("id", id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteUser(@PathVariable UUID id) {
        jdbc.update("DELETE FROM app_user WHERE id = ?", id);
        return ResponseEntity.ok(Map.of("deleted", true));
    }
}

