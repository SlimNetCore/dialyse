package com.hemodialyse.backend.application.web;

import com.hemodialyse.backend.application.notification.NotificationService;
import com.hemodialyse.backend.domain.insurance.port.AttestationUseCase;
import com.hemodialyse.backend.domain.pec.port.PecUseCase;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.hemodialyse.backend.application.query.PecReadQueryService;

import java.time.LocalDate;
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

    record CreatePecRequest(UUID patientId, UUID centerId, String userId,
                            LocalDate dateDebutDemande, LocalDate dateFinDemande, UUID forfaitDemandeId) {}

    record ValidatePecRequest(UUID centerId, String userId,
                              LocalDate dateDebutEffectif, LocalDate dateFinEffectif, UUID forfaitEffectifId) {}

    @PostMapping
    public ResponseEntity<?> create(@RequestBody CreatePecRequest r) {
        var pec = pecUseCase.create(CenterId.of(r.centerId()), r.patientId(), r.dateDebutDemande(), r.dateFinDemande(), r.forfaitDemandeId());
        return ResponseEntity.ok(Map.of("id", pec.getId(), "status", pec.getStatus()));
    }

    @GetMapping
    public ResponseEntity<?> listByCenter(@RequestParam UUID centerId) {
        return ResponseEntity.ok(pecUseCase.listByCenter(CenterId.of(centerId)));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{pecId}/validate")
    public ResponseEntity<?> validate(@PathVariable UUID pecId, @RequestBody ValidatePecRequest r) {
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
    record CreateAttestationRequest(UUID patientId, UUID centerId, LocalDate dateDebut, LocalDate dateFin) {}

    @PostMapping("/attestations")
    public ResponseEntity<?> createAttestation(@RequestBody CreateAttestationRequest r) {
        var a = attestationUseCase.create(CenterId.of(r.centerId()), r.patientId(), r.dateDebut(), r.dateFin());
        return ResponseEntity.ok(Map.of("id", a.getId(), "dateDebut", a.getDateDebut(), "dateFin", a.getDateFin()));
    }

    @GetMapping("/attestations/{patientId}")
    public ResponseEntity<?> listAttestations(@PathVariable UUID patientId, @RequestParam UUID centerId) {
        return ResponseEntity.ok(attestationUseCase.listByPatient(CenterId.of(centerId), patientId));
    }

    @GetMapping("/attestations-center")
    public ResponseEntity<?> listAttestationsByCenter(@RequestParam UUID centerId) {
        return ResponseEntity.ok(readQueryService.listAttestationsByCenter(centerId));
    }

    @GetMapping("/pec-center")
    public ResponseEntity<?> listPecByCenterDetailed(@RequestParam UUID centerId) {
        return ResponseEntity.ok(readQueryService.listPecByCenterDetailed(centerId));
    }
}
