package com.hemodialyse.backend.contexts.patientcouverture.interfaces.http;

import com.hemodialyse.backend.contexts.patientcouverture.application.PecUseCases;
import com.hemodialyse.backend.contexts.patientcouverture.domain.Pec;
import com.hemodialyse.backend.contexts.patientcouverture.domain.PecId;
import com.hemodialyse.backend.contexts.patientcouverture.domain.PatientId;
import com.hemodialyse.backend.contexts.patientcouverture.interfaces.http.dto.CloturerRequest;
import com.hemodialyse.backend.contexts.patientcouverture.interfaces.http.dto.CreerRequest;
import com.hemodialyse.backend.contexts.patientcouverture.interfaces.http.dto.ValiderRequest;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.PagedResult;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/patient-couverture/pec")
public class PecControleur {
    private final PecUseCases useCases;

    public PecControleur(PecUseCases useCases) {
        this.useCases = useCases;
    }

    @PostMapping
    public ResponseEntity<PecId> creer(@RequestBody CreerRequest req) {
        PecId id = useCases.creer(new PecUseCases.CreerPecCommande(
                new PatientId(req.patientId()), new CentreId(req.centreId()),
                req.dateDebutDemande(), req.dateFinDemande(), req.forfaitDemandeId(), req.auteurId()), Instant.now());
        return ResponseEntity.ok(id);
    }

    @GetMapping
    public ResponseEntity<PagedResult<Pec>> lister(@RequestParam UUID centreId,
                                                   @RequestParam(defaultValue = "0") int page,
                                                   @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(useCases.lister(new CentreId(centreId), page, size));
    }

    @GetMapping("/patient/{patientId}")
    public ResponseEntity<?> listerParPatient(@PathVariable UUID patientId, @RequestParam UUID centreId) {
        return ResponseEntity.ok(useCases.listerParPatient(new PatientId(patientId), new CentreId(centreId)));
    }

    @GetMapping("/{pecId}")
    public ResponseEntity<Pec> consulter(@PathVariable UUID pecId, @RequestParam UUID centreId) {
        return ResponseEntity.ok(useCases.consulter(new PecId(pecId), new CentreId(centreId)));
    }

    @PostMapping("/{pecId}/validate")
    public ResponseEntity<Void> valider(@PathVariable UUID pecId, @RequestBody ValiderRequest req) {
        useCases.valider(new PecUseCases.ValiderPecCommande(
                new PecId(pecId), new CentreId(req.centreId()),
                req.dateDebutEffectif(), req.dateFinEffectif(), req.forfaitEffectifId()));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{pecId}/close")
    public ResponseEntity<Void> cloturer(@PathVariable UUID pecId, @RequestBody CloturerRequest req) {
        useCases.cloturer(new PecUseCases.CloturerPecCommande(new PecId(pecId), new CentreId(req.centreId())));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{pecId}/session-allowed")
    public ResponseEntity<Boolean> sessionAllowed(@PathVariable UUID pecId, @RequestParam UUID centreId) {
        return ResponseEntity.ok(useCases.autoriseSeance(new PecId(pecId), new CentreId(centreId)));
    }

    @DeleteMapping("/{pecId}")
    public ResponseEntity<Void> supprimer(@PathVariable UUID pecId, @RequestParam UUID centreId) {
        useCases.supprimer(new PecId(pecId), new CentreId(centreId));
        return ResponseEntity.noContent().build();
    }
}