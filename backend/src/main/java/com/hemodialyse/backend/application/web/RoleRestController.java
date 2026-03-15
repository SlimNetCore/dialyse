package com.hemodialyse.backend.application.web;

import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1/roles")
@PreAuthorize("hasRole('ADMIN')")
public class RoleRestController {

    private final JdbcTemplate jdbc;

    public RoleRestController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    record CreateRoleRequest(String code, String name, String description) {}
    record UpdateRoleRequest(String code, String name, String description) {}

    @GetMapping
    public ResponseEntity<?> listRoles() {
        return ResponseEntity.ok(jdbc.queryForList("SELECT id, code, name, description FROM app_role ORDER BY code"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getRole(@PathVariable UUID id) {
        var roles = jdbc.queryForList("SELECT id, code, name, description FROM app_role WHERE id = ?", id);
        if (roles.isEmpty()) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(roles.get(0));
    }

    @PostMapping
    public ResponseEntity<?> createRole(@RequestBody CreateRoleRequest req) {
        Integer count = jdbc.queryForObject("SELECT COUNT(1) FROM app_role WHERE code = ?", Integer.class, req.code());
        if (count != null && count > 0) {
            return ResponseEntity.badRequest().body(Map.of("error", "Code rôle déjà existant"));
        }
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO app_role (id, code, name, description) VALUES (?,?,?,?)", id, req.code(), req.name(), req.description());
        return ResponseEntity.ok(Map.of("id", id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateRole(@PathVariable UUID id, @RequestBody UpdateRoleRequest req) {
        jdbc.update("UPDATE app_role SET code=?, name=?, description=? WHERE id=?", req.code(), req.name(), req.description(), id);
        return ResponseEntity.ok(Map.of("id", id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteRole(@PathVariable UUID id) {
        jdbc.update("DELETE FROM app_role WHERE id = ?", id);
        return ResponseEntity.ok(Map.of("deleted", true));
    }
}

