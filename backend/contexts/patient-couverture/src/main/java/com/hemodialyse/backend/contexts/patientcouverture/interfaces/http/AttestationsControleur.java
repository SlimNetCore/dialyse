package com.hemodialyse.backend.contexts.patientcouverture.interfaces.http;

import com.hemodialyse.backend.contexts.patientcouverture.application.AttestationUseCases;
import com.hemodialyse.backend.contexts.patientcouverture.domain.AttestationDroit;
import com.hemodialyse.backend.contexts.patientcouverture.domain.AttestationDroitId;
import com.hemodialyse.backend.contexts.patientcouverture.domain.PatientId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.PagedResult;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/patient-couverture/attestations")
public class AttestationsControleur {
    private final AttestationUseCases useCases;

    public AttestationsControleur(AttestationUseCases useCases) {
        this.useCases = useCases;
    }

    @PostMapping
    public ResponseEntity<AttestationDroitId> enregistrer(@RequestBody EnregistrerRequest req) {
        AttestationDroitId id = useCases.enregistrer(new AttestationUseCases.EnregistrerAttestationCommande(
                new PatientId(req.patientId()), new CentreId(req.centreId()),
                req.dateDebut(), req.dateFin()), Instant.now());
        return ResponseEntity.ok(id);
    }

    @GetMapping
    public ResponseEntity<PagedResult<AttestationDroit>> lister(@RequestParam UUID centreId,
                                                                @RequestParam(defaultValue = "0") int page,
                                                                @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(useCases.lister(new CentreId(centreId), page, size));
    }

    @GetMapping("/patient/{patientId}")
    public ResponseEntity<?> listerParPatient(@PathVariable UUID patientId, @RequestParam UUID centreId) {
        return ResponseEntity.ok(useCases.listerParPatient(new PatientId(patientId), new CentreId(centreId)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AttestationDroit> consulter(@PathVariable UUID id, @RequestParam UUID centreId) {
        return ResponseEntity.ok(useCases.consulter(new AttestationDroitId(id), new CentreId(centreId)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> supprimer(@PathVariable UUID id, @RequestParam UUID centreId) {
        useCases.supprimer(new AttestationDroitId(id), new CentreId(centreId));
        return ResponseEntity.noContent().build();
    }

    public record EnregistrerRequest(UUID patientId, UUID centreId, LocalDate dateDebut, LocalDate dateFin) {
    }
}