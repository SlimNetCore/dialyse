package com.hemodialyse.backend.infrastructure.web.rest;

import com.hemodialyse.backend.infrastructure.persistence.repository.AppRoleJpaRepository;
import com.hemodialyse.backend.infrastructure.persistence.spec.RoleSpecifications;
import com.hemodialyse.backend.infrastructure.web.dto.request.CreateRoleRequest;
import com.hemodialyse.backend.infrastructure.web.dto.request.RoleSearchRequest;
import com.hemodialyse.backend.infrastructure.web.dto.request.UpdateRoleRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/roles")
@PreAuthorize("hasRole('ADMIN')")
public class RoleRestController {

    private final JdbcTemplate jdbc;
    private final AppRoleJpaRepository roleRepository;

    public RoleRestController(JdbcTemplate jdbc, AppRoleJpaRepository roleRepository) {
        this.jdbc = jdbc;
        this.roleRepository = roleRepository;
    }

    @GetMapping
    public ResponseEntity<?> listRoles() {
        return ResponseEntity.ok(jdbc.queryForList("SELECT id, code, name, description FROM app_role ORDER BY code"));
    }

    @PostMapping("/search")
    public ResponseEntity<?> searchRoles(@RequestBody @Valid RoleSearchRequest req) {
        int page = req.page();
        int size = req.size();
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Order.asc("code")));
        var result = roleRepository.findAll(RoleSpecifications.from(req), pageable);

        List<Map<String, Object>> items = result.getContent().stream()
                .map(r -> Map.<String, Object>of(
                        "id", r.getId(),
                        "code", r.getCode(),
                        "name", r.getName(),
                        "description", r.getDescription()
                ))
                .toList();

        return ResponseEntity.ok(Map.of("items", items, "total", result.getTotalElements(), "page", page, "size", size));
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

