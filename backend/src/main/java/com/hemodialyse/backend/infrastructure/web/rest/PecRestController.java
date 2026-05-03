package com.hemodialyse.backend.infrastructure.web.rest;

import com.hemodialyse.backend.application.notification.NotificationService;
import com.hemodialyse.backend.application.query.PecReadQueryService;
import com.hemodialyse.backend.domain.insurance.port.AttestationUseCase;
import com.hemodialyse.backend.domain.pec.port.PecUseCase;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.infrastructure.web.dto.request.AttestationSearchRequest;
import com.hemodialyse.backend.infrastructure.web.dto.request.CreateAttestationRequest;
import com.hemodialyse.backend.infrastructure.web.dto.request.CreatePecRequest;
import com.hemodialyse.backend.infrastructure.web.dto.request.PecSearchRequest;
import com.hemodialyse.backend.infrastructure.web.dto.request.ValidatePecRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/pec")
public class PecRestController {

    private final PecUseCase pecUseCase;
    private final AttestationUseCase attestationUseCase;
    private final NotificationService notificationService;
    private final PecReadQueryService readQueryService;

    public PecRestController(PecUseCase pecUseCase, AttestationUseCase attestationUseCase,
                             NotificationService notificationService, PecReadQueryService readQueryService) {
        this.pecUseCase = pecUseCase;
        this.attestationUseCase = attestationUseCase;
        this.notificationService = notificationService;
        this.readQueryService = readQueryService;
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody @Valid CreatePecRequest r) {
        var pec = pecUseCase.create(CenterId.of(r.centerId()), r.patientId(), r.dateDebutDemande(), r.dateFinDemande(), r.forfaitDemandeId());
        return ResponseEntity.ok(Map.of("id", pec.getId(), "status", pec.getStatus()));
    }

    @GetMapping
    public ResponseEntity<?> listByCenter(@RequestParam UUID centerId) {
        return ResponseEntity.ok(pecUseCase.listByCenter(CenterId.of(centerId)));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{pecId}/validate")
    public ResponseEntity<?> validate(@PathVariable UUID pecId, @RequestBody @Valid ValidatePecRequest r) {
        var pec = pecUseCase.validate(CenterId.of(r.centerId()), pecId, r.dateDebutEffectif(), r.dateFinEffectif(), r.forfaitEffectifId());
        notificationService.notifyPecValidated(r.centerId(), pecId, "Patient");
        return ResponseEntity.ok(Map.of("id", pec.getId(), "status", pec.getStatus()));
    }

    @PostMapping("/{pecId}/close")
    public ResponseEntity<?> close(@PathVariable UUID pecId, @RequestBody Map<String, String> body) {
        UUID centerId = UUID.fromString(body.get("centerId"));
        var pec = pecUseCase.close(CenterId.of(centerId), pecId);
        notificationService.notifyPecClosed(centerId, pecId, "Patient");
        return ResponseEntity.ok(Map.of("id", pec.getId(), "status", pec.getStatus()));
    }

    @GetMapping("/{pecId}/session-allowed")
    public ResponseEntity<?> sessionAllowed(@PathVariable UUID pecId, @RequestParam UUID centerId, @RequestParam String userId) {
        boolean allowed = pecUseCase.canCreateSession(CenterId.of(centerId), pecId);
        return ResponseEntity.ok(Map.of("allowed", allowed));
    }

    @GetMapping("/patient/{patientId}")
    public ResponseEntity<?> listByPatient(@PathVariable UUID patientId, @RequestParam UUID centerId) {
        return ResponseEntity.ok(pecUseCase.listByPatient(CenterId.of(centerId), patientId));
    }

    // Attestation endpoints
    @PostMapping("/attestations")
    public ResponseEntity<?> createAttestation(@RequestBody @Valid CreateAttestationRequest r) {
        var a = attestationUseCase.create(CenterId.of(r.centerId()), r.patientId(), r.dateDebut(), r.dateFin());
        return ResponseEntity.ok(Map.of("id", a.getId(), "dateDebut", a.getDateDebut(), "dateFin", a.getDateFin()));
    }

    @GetMapping("/attestations/{patientId}")
    public ResponseEntity<?> listAttestations(@PathVariable UUID patientId, @RequestParam UUID centerId) {
        return ResponseEntity.ok(attestationUseCase.listByPatient(CenterId.of(centerId), patientId));
    }

    @DeleteMapping("/{pecId}")
    public ResponseEntity<?> deletePec(@PathVariable UUID pecId, @RequestParam UUID centerId) {
        pecUseCase.delete(CenterId.of(centerId), pecId);
        return ResponseEntity.ok(Map.of("deleted", true));
    }

    @DeleteMapping("/attestations/{attestationId}")
    public ResponseEntity<?> deleteAttestation(@PathVariable UUID attestationId, @RequestParam UUID centerId) {
        attestationUseCase.delete(CenterId.of(centerId), attestationId);
        return ResponseEntity.ok(Map.of("deleted", true));
    }

    @PostMapping("/attestations-center/search")
    public ResponseEntity<?> searchAttestationsByCenter(@RequestBody @Valid AttestationSearchRequest request) {
        return listAttestationsByCenterCriteria(request);
    }

    @PostMapping("/pec-center/search")
    public ResponseEntity<?> searchPecByCenterDetailed(@RequestBody @Valid PecSearchRequest request) {
        return listPecByCenterCriteria(request);
    }

    private ResponseEntity<?> listAttestationsByCenterCriteria(AttestationSearchRequest request) {
        var result = readQueryService.listAttestationsByCenterPaged(request);
        return ResponseEntity.ok(Map.of(
                "items", result.items(),
                "total", result.total(),
                "page", result.page(),
                "size", result.size()
        ));
    }

    private ResponseEntity<?> listPecByCenterCriteria(PecSearchRequest request) {
        var result = readQueryService.listPecByCenterDetailedPaged(request);
        return ResponseEntity.ok(Map.of(
                "items", result.items(),
                "total", result.total(),
                "page", result.page(),
                "size", result.size()
        ));
    }
}








