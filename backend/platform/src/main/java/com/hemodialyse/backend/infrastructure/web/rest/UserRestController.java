package com.hemodialyse.backend.infrastructure.web.rest;

import com.hemodialyse.backend.application.license.LicenseService;
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
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
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
    private final LicenseService licenseService;

    public UserRestController(JdbcTemplate jdbc, PasswordEncoder passwordEncoder, AppUserJpaRepository userRepository,
                              LicenseService licenseService) {
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
        this.userRepository = userRepository;
        this.licenseService = licenseService;
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
        var users = jdbc.queryForList(
                "SELECT id AS \"id\", username AS \"username\", email AS \"email\", " +
                        "full_name AS \"full_name\", active AS \"active\", created_at AS \"created_at\" " +
                        "FROM app_user WHERE id = ?", id);
        if (users.isEmpty()) return ResponseEntity.notFound().build();
        var u = users.get(0);
        u.put("roles", jdbc.queryForList(
                "SELECT r.id AS \"id\", r.code AS \"code\", r.name AS \"name\" " +
                        "FROM app_role r INNER JOIN app_user_role ur ON ur.role_id = r.id WHERE ur.user_id = ?", id));
        u.put("centers", jdbc.queryForList(
                "SELECT c.id AS \"id\", c.name AS \"name\" " +
                        "FROM centers c INNER JOIN app_user_center uc ON uc.center_id = c.id WHERE uc.user_id = ?", id));
        return ResponseEntity.ok(u);
    }

    @PostMapping
    public ResponseEntity<?> createUser(@RequestBody CreateUserRequest req, Authentication authentication) {
        Integer count = jdbc.queryForObject("SELECT COUNT(1) FROM app_user WHERE username = ?", Integer.class, req.username());
        if (count != null && count > 0) {
            return ResponseEntity.badRequest().body(Map.of("error", "Nom d'utilisateur déjà existant"));
        }

        if (req.active() && req.centerIds() != null) {
            for (UUID centerId : req.centerIds()) {
                licenseService.assertSeatAvailable(centerId);
            }
        }

        List<UUID> assignableRoleIds = filterAssignableRoleIds(req.roleIds(), authentication);

        UUID id = UUID.randomUUID();
        String hash = passwordEncoder.encode(req.password());
        jdbc.update("INSERT INTO app_user (id, username, password_hash, email, full_name, active) VALUES (?,?,?,?,?,?)",
                id, req.username(), hash, req.email(), req.fullName(), req.active());

        for (UUID roleId : assignableRoleIds) {
            jdbc.update("INSERT INTO app_user_role (user_id, role_id) VALUES (?,?)", id, roleId);
        }
        if (req.centerIds() != null) {
            for (UUID centerId : req.centerIds()) {
                jdbc.update("INSERT INTO app_user_center (user_id, center_id) VALUES (?,?)", id, centerId);
            }
        }
        return ResponseEntity.ok(Map.of("id", id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateUser(@PathVariable UUID id, @RequestBody UpdateUserRequest req, Authentication authentication) {
        if (req.password() != null && !req.password().isBlank()) {
            String hash = passwordEncoder.encode(req.password());
            jdbc.update("UPDATE app_user SET email=?, full_name=?, active=?, password_hash=? WHERE id=?",
                    req.email(), req.fullName(), req.active(), hash, id);
        } else {
            jdbc.update("UPDATE app_user SET email=?, full_name=?, active=? WHERE id=?",
                    req.email(), req.fullName(), req.active(), id);
        }

        if (req.roleIds() != null) {
            List<UUID> assignableRoleIds = filterAssignableRoleIds(req.roleIds(), authentication);
            // A non-SUPERADMIN caller must not be able to strip an existing SUPERADMIN
            // grant from a target user either — preserve it rather than silently drop it.
            boolean callerIsSuperAdmin = hasAuthority(authentication, "ROLE_SUPERADMIN");
            if (!callerIsSuperAdmin) {
                List<UUID> existingSuperAdminRoleIds = jdbc.queryForList(
                        "SELECT ur.role_id FROM app_user_role ur INNER JOIN app_role r ON r.id = ur.role_id " +
                                "WHERE ur.user_id = ? AND r.code = 'SUPERADMIN'", UUID.class, id);
                assignableRoleIds.addAll(existingSuperAdminRoleIds);
            }
            jdbc.update("DELETE FROM app_user_role WHERE user_id = ?", id);
            for (UUID roleId : assignableRoleIds) {
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

    /**
     * SUPERADMIN is reserved for the vendor's own account and must never be
     * grantable through the regular user-management screen — otherwise any center
     * ADMIN could self-elevate. Strips it out of the requested role list unless the
     * caller already holds it.
     */
    private List<UUID> filterAssignableRoleIds(List<UUID> requestedRoleIds, Authentication authentication) {
        List<UUID> result = new ArrayList<>();
        if (requestedRoleIds == null) {
            return result;
        }
        boolean callerIsSuperAdmin = hasAuthority(authentication, "ROLE_SUPERADMIN");
        for (UUID roleId : requestedRoleIds) {
            if (!callerIsSuperAdmin && "SUPERADMIN".equals(jdbc.queryForObject(
                    "SELECT code FROM app_role WHERE id = ?", String.class, roleId))) {
                continue;
            }
            result.add(roleId);
        }
        return result;
    }

    private boolean hasAuthority(Authentication authentication, String authority) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> authority.equals(a.getAuthority()));
    }
}

