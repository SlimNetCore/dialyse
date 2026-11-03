package com.hemodialyse.backend.infrastructure.web.rest;

import com.hemodialyse.backend.application.notification.NotificationService;
import com.hemodialyse.backend.application.query.PecReadQueryService;
import com.hemodialyse.backend.domain.insurance.port.AttestationUseCase;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.infrastructure.web.dto.request.*;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * Routes d'ATTESTATION de droits sous {@code /api/v1/pec} (slice PC4 : encore le legacy, non reprises). Les routes de PRISE EN
 * CHARGE du même préfixe (création, listes, validation, clôture, {@code session-allowed}, suppression, recherche) sont servies par
 * {@code contexts/patient-couverture} (PC3, {@code PecControleur}).
 */
@RestController
@RequestMapping("/api/v1/pec")
public class PecRestController {

    private final AttestationUseCase attestationUseCase;
    private final NotificationService notificationService;
    private final PecReadQueryService readQueryService;

    public PecRestController(AttestationUseCase attestationUseCase, NotificationService notificationService,
                             PecReadQueryService readQueryService) {
        this.attestationUseCase = attestationUseCase;
        this.notificationService = notificationService;
        this.readQueryService = readQueryService;
    }

    // Attestation endpoints
    @PostMapping("/attestations")
    public ResponseEntity<?> createAttestation(@RequestBody @Valid CreateAttestationRequest r) {
        var a = attestationUseCase.create(CenterId.of(r.centerId()), r.patientId(), r.dateDebut(), r.dateFin());
        notificationService.notifyAttestationCreated(r.centerId(), a.getId(), a.getPatientId());
        return ResponseEntity.ok(Map.of("id", a.getId(), "dateDebut", a.getDateDebut(), "dateFin", a.getDateFin()));
    }

    @GetMapping("/attestations/{patientId}")
    public ResponseEntity<?> listAttestations(@PathVariable UUID patientId, @RequestParam UUID centerId) {
        return ResponseEntity.ok(attestationUseCase.listByPatient(CenterId.of(centerId), patientId));
    }

    @DeleteMapping("/attestations/{attestationId}")
    public ResponseEntity<?> deleteAttestation(@PathVariable UUID attestationId,
                                               @RequestParam UUID centerId,
                                               @RequestParam(required = false) UUID patientId) {
        attestationUseCase.delete(CenterId.of(centerId), attestationId);
        notificationService.notifyAttestationDeleted(centerId, attestationId, patientId);
        return ResponseEntity.ok(Map.of("deleted", true));
    }

    @PostMapping("/attestations-center/search")
    public ResponseEntity<?> searchAttestationsByCenter(@RequestBody @Valid AttestationSearchRequest request) {
        return listAttestationsByCenterCriteria(request);
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
}
