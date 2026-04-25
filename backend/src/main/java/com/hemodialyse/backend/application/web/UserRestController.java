package com.hemodialyse.backend.application.web;

import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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

    private static boolean hasValue(String v) {
        return v != null && !v.isBlank();
    }

    private static void addLike(StringBuilder where, List<Object> params, String expression, String value) {
        if (!hasValue(value)) return;
        where.append(where.isEmpty() ? " WHERE " : " AND ");
        where.append("LOWER(").append(expression).append(") LIKE ? ");
        params.add("%" + value.toLowerCase() + "%");
    }

    private static void appendUsersFilters(StringBuilder where, List<Object> params,
                                           UUID centerId, String search, String username,
                                           String fullName, String email, String roles,
                                           String centers, String active) {
        if (centerId != null) {
            where.append(where.isEmpty() ? " WHERE " : " AND ");
            where.append("uc.center_id = ? ");
            params.add(centerId);
        }

        if (hasValue(search)) {
            where.append(where.isEmpty() ? " WHERE " : " AND ");
            where.append("(LOWER(u.username) LIKE ? OR LOWER(u.full_name) LIKE ? OR LOWER(u.email) LIKE ?)");
            String s = "%" + search.toLowerCase() + "%";
            params.add(s);
            params.add(s);
            params.add(s);
        }

        addLike(where, params, "u.username", username);
        addLike(where, params, "u.full_name", fullName);
        addLike(where, params, "u.email", email);
        addLike(where, params, "r.name", roles);
        addLike(where, params, "c.name", centers);

        if (hasValue(active)) {
            where.append(where.isEmpty() ? " WHERE " : " AND ");
            where.append("u.active = ? ");
            params.add(Boolean.parseBoolean(active));
        }
    }

    @GetMapping
    public ResponseEntity<?> listUsers(@RequestParam(required = false) UUID centerId,
                                       @RequestParam(required = false) Integer page,
                                       @RequestParam(required = false) Integer size,
                                       @RequestParam(required = false) String search,
                                       @RequestParam(required = false) String username,
                                       @RequestParam(required = false) String fullName,
                                       @RequestParam(required = false) String email,
                                       @RequestParam(required = false) String roles,
                                       @RequestParam(required = false) String centers,
                                       @RequestParam(required = false) String active) {
        boolean pagedRequest = page != null || size != null ||
                hasValue(search) || hasValue(username) || hasValue(fullName) || hasValue(email) ||
                hasValue(roles) || hasValue(centers) || hasValue(active);

        if (!pagedRequest) {
            return ResponseEntity.ok(loadUsers(centerId));
        }

        int p = Math.max(page == null ? 0 : page, 0);
        int s = Math.max(size == null ? 10 : size, 1);

        StringBuilder where = new StringBuilder();
        List<Object> params = new ArrayList<>();
        appendUsersFilters(where, params, centerId, search, username, fullName, email, roles, centers, active);

        long total = jdbc.queryForObject("SELECT COUNT(DISTINCT u.id) FROM app_user u " +
                "LEFT JOIN app_user_center uc ON uc.user_id = u.id " +
                "LEFT JOIN app_user_role ur ON ur.user_id = u.id " +
                "LEFT JOIN app_role r ON r.id = ur.role_id " +
                "LEFT JOIN centers c ON c.id = uc.center_id " + where, Long.class, params.toArray());

        List<Object> dataParams = new ArrayList<>(params);
        dataParams.add(s);
        dataParams.add(p * s);
        var users = jdbc.queryForList(
                "SELECT DISTINCT u.id, u.username, u.email, u.full_name, u.active, u.created_at " +
                        "FROM app_user u " +
                        "LEFT JOIN app_user_center uc ON uc.user_id = u.id " +
                        "LEFT JOIN app_user_role ur ON ur.user_id = u.id " +
                        "LEFT JOIN app_role r ON r.id = ur.role_id " +
                        "LEFT JOIN centers c ON c.id = uc.center_id " +
                        where + " ORDER BY u.username LIMIT ? OFFSET ?",
                dataParams.toArray()
        );

        enrichUsers(users);
        return ResponseEntity.ok(Map.of("items", users, "total", total, "page", p, "size", s));
    }

    private List<Map<String, Object>> loadUsers(UUID centerId) {
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
        enrichUsers(users);
        return users;
    }

    private void enrichUsers(List<Map<String, Object>> users) {
        for (var u : users) {
            UUID uid = (UUID) u.get("ID");
            u.put("roles", jdbc.queryForList("SELECT r.id, r.code, r.name FROM app_role r INNER JOIN app_user_role ur ON ur.role_id = r.id WHERE ur.user_id = ?", uid));
            u.put("centers", jdbc.queryForList("SELECT c.id, c.name FROM centers c INNER JOIN app_user_center uc ON uc.center_id = c.id WHERE uc.user_id = ?", uid));
        }
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

