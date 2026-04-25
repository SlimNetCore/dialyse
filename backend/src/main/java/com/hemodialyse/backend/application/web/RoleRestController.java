package com.hemodialyse.backend.application.web;

import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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

    private static boolean hasValue(String v) {
        return v != null && !v.isBlank();
    }

    private static void addLike(StringBuilder where, List<Object> params, String expression, String value) {
        if (!hasValue(value)) return;
        where.append(where.isEmpty() ? " WHERE " : " AND ");
        where.append("LOWER(").append(expression).append(") LIKE ? ");
        params.add("%" + value.toLowerCase() + "%");
    }

    @GetMapping
    public ResponseEntity<?> listRoles(@RequestParam(required = false) Integer page,
                                       @RequestParam(required = false) Integer size,
                                       @RequestParam(required = false) String search,
                                       @RequestParam(required = false) String code,
                                       @RequestParam(required = false) String name,
                                       @RequestParam(required = false) String description) {
        boolean pagedRequest = page != null || size != null || hasValue(search) || hasValue(code) || hasValue(name) || hasValue(description);
        if (!pagedRequest) {
            return ResponseEntity.ok(jdbc.queryForList("SELECT id, code, name, description FROM app_role ORDER BY code"));
        }

        int p = Math.max(page == null ? 0 : page, 0);
        int s = Math.max(size == null ? 10 : size, 1);

        StringBuilder where = new StringBuilder();
        List<Object> params = new ArrayList<>();

        if (hasValue(search)) {
            where.append(where.isEmpty() ? " WHERE " : " AND ");
            where.append("(LOWER(code) LIKE ? OR LOWER(name) LIKE ? OR LOWER(COALESCE(description,'')) LIKE ?)");
            String term = "%" + search.toLowerCase() + "%";
            params.add(term);
            params.add(term);
            params.add(term);
        }

        addLike(where, params, "code", code);
        addLike(where, params, "name", name);
        addLike(where, params, "COALESCE(description,'')", description);

        long total = jdbc.queryForObject("SELECT COUNT(1) FROM app_role" + where, Long.class, params.toArray());

        List<Object> dataParams = new ArrayList<>(params);
        dataParams.add(s);
        dataParams.add(p * s);
        var items = jdbc.queryForList(
                "SELECT id, code, name, description FROM app_role" + where + " ORDER BY code LIMIT ? OFFSET ?",
                dataParams.toArray()
        );

        return ResponseEntity.ok(Map.of("items", items, "total", total, "page", p, "size", s));
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

