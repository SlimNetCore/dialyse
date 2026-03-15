package com.hemodialyse.backend.application.web;

import com.hemodialyse.backend.application.port.in.AttestationUseCase;
import com.hemodialyse.backend.application.port.in.PecUseCase;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/pec")
public class PecRestController {

    private final PecUseCase pecUseCase;
    private final AttestationUseCase attestationUseCase;

    public PecRestController(PecUseCase pecUseCase, AttestationUseCase attestationUseCase) {
        this.pecUseCase = pecUseCase;
        this.attestationUseCase = attestationUseCase;
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

    @PostMapping("/{pecId}/validate")
    public ResponseEntity<?> validate(@PathVariable UUID pecId, @RequestBody ValidatePecRequest r) {
        var pec = pecUseCase.validate(CenterId.of(r.centerId()), pecId, r.dateDebutEffectif(), r.dateFinEffectif(), r.forfaitEffectifId());
        return ResponseEntity.ok(Map.of("id", pec.getId(), "status", pec.getStatus()));
    }

    @PostMapping("/{pecId}/close")
    public ResponseEntity<?> close(@PathVariable UUID pecId, @RequestBody Map<String, String> body) {
        var pec = pecUseCase.close(CenterId.of(UUID.fromString(body.get("centerId"))), pecId);
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
}

