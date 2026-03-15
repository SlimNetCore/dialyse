package com.hemodialyse.backend.infrastructure.web;

import com.hemodialyse.backend.application.pec.PecService;
import com.hemodialyse.backend.domain.pec.PriseEnCharge;
import com.hemodialyse.backend.domain.shared.TenantScope;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/pec")
public class PecController {

    private final PecService service;

    public PecController(PecService service) {
        this.service = service;
    }

    record CreatePecRequest(UUID patientId, UUID centerId, String userId, LocalDate dateDebutDemande, LocalDate dateFinDemande) {}
    record ActionRequest(UUID centerId, String userId) {}

    @PostMapping
    public ResponseEntity<?> create(@RequestBody CreatePecRequest request) {
        TenantScope scope = new TenantScope(request.centerId(), request.userId(), Set.of("AGENT_ASSURANCE"));
        PriseEnCharge pec = service.create(scope, request.patientId(), request.dateDebutDemande(), request.dateFinDemande());
        return ResponseEntity.ok(Map.of("id", pec.getId(), "status", pec.getStatus()));
    }

    @PostMapping("/{id}/validate")
    public ResponseEntity<?> validate(@PathVariable UUID id, @RequestBody ActionRequest request) {
        TenantScope scope = new TenantScope(request.centerId(), request.userId(), Set.of("AGENT_ASSURANCE"));
        PriseEnCharge pec = service.validate(scope, id);
        return ResponseEntity.ok(Map.of("id", pec.getId(), "status", pec.getStatus()));
    }

    @PostMapping("/{id}/close")
    public ResponseEntity<?> close(@PathVariable UUID id, @RequestBody ActionRequest request) {
        TenantScope scope = new TenantScope(request.centerId(), request.userId(), Set.of("AGENT_ASSURANCE"));
        PriseEnCharge pec = service.close(scope, id);
        return ResponseEntity.ok(Map.of("id", pec.getId(), "status", pec.getStatus()));
    }

    @GetMapping("/{id}/session-allowed")
    public ResponseEntity<?> sessionAllowed(@PathVariable UUID id, @RequestParam UUID centerId, @RequestParam String userId) {
        TenantScope scope = new TenantScope(centerId, userId, Set.of("LECTURE"));
        return ResponseEntity.ok(Map.of("allowed", service.canCreateSession(scope, id)));
    }
}

