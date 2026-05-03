package com.hemodialyse.backend.infrastructure.web.rest;

import com.hemodialyse.backend.infrastructure.persistence.entity.AppUserJpaEntity;
import com.hemodialyse.backend.infrastructure.persistence.repository.AppUserJpaRepository;
import com.hemodialyse.backend.infrastructure.persistence.spec.UserSpecifications;
import com.hemodialyse.backend.infrastructure.web.dto.request.CreateUserRequest;
import com.hemodialyse.backend.infrastructure.web.dto.request.UpdateUserRequest;
import com.hemodialyse.backend.infrastructure.web.dto.request.UserSearchRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@PreAuthorize("hasRole('ADMIN')")
public class UserRestController {

    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;
    private final AppUserJpaRepository userRepository;

    public UserRestController(JdbcTemplate jdbc, PasswordEncoder passwordEncoder, AppUserJpaRepository userRepository) {
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
        this.userRepository = userRepository;
    }

    @GetMapping
    public ResponseEntity<?> listUsers(@RequestParam(required = false) UUID centerId) {
        return ResponseEntity.ok(loadUsers(centerId));
    }

    @PostMapping("/search")
    public ResponseEntity<?> searchUsers(@RequestBody @Valid UserSearchRequest req) {
        int page = req.page();
        int size = req.size();
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Order.asc("username")));
        var result = userRepository.findAll(UserSpecifications.from(req), pageable);

        List<Map<String, Object>> items = result.getContent().stream().map(this::toUserPayload).toList();
        return ResponseEntity.ok(Map.of("items", items, "total", result.getTotalElements(), "page", page, "size", size));
    }

    private List<Map<String, Object>> loadUsers(UUID centerId) {
        if (centerId == null) {
            return userRepository.findAll(Sort.by(Sort.Order.asc("username"))).stream().map(this::toUserPayload).toList();
        }
        UserSearchRequest request = new UserSearchRequest(centerId, 0, 1000, null, null, null, null, null, null, null);
        return userRepository.findAll(UserSpecifications.from(request), Sort.by(Sort.Order.asc("username"))).stream()
                .map(this::toUserPayload)
                .toList();
    }

    private Map<String, Object> toUserPayload(AppUserJpaEntity user) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", user.getId());
        payload.put("username", user.getUsername());
        payload.put("email", user.getEmail());
        payload.put("full_name", user.getFullName());
        payload.put("active", user.isActive());
        payload.put("created_at", user.getCreatedAt());
        payload.put("roles", user.getRoles().stream()
                .map(r -> Map.<String, Object>of("id", r.getId(), "code", r.getCode(), "name", r.getName()))
                .toList());
        payload.put("centers", user.getCenters().stream()
                .map(c -> Map.<String, Object>of("id", c.getId(), "name", c.getName()))
                .toList());
        return payload;
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

        if (req.roleIds() != null) {
            jdbc.update("DELETE FROM app_user_role WHERE user_id = ?", id);
            for (UUID roleId : req.roleIds()) {
                jdbc.update("INSERT INTO app_user_role (user_id, role_id) VALUES (?,?)", id, roleId);
            }
        }
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

