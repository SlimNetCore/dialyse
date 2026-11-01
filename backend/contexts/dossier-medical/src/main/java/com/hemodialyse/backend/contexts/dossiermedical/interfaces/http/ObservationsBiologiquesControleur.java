package com.hemodialyse.backend.contexts.dossiermedical.interfaces.http;

import com.hemodialyse.backend.contexts.dossiermedical.application.ObservationBiologiqueUseCases;
import com.hemodialyse.backend.contexts.dossiermedical.domain.ObservationBiologique;
import com.hemodialyse.backend.contexts.dossiermedical.domain.ObservationBiologiqueId;
import com.hemodialyse.backend.contexts.dossiermedical.interfaces.http.dto.EnregistrerObservationRequete;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.PagedResult;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/dossier-medical/observations-biologiques")
public class ObservationsBiologiquesControleur {

    private final ObservationBiologiqueUseCases useCases;

    public ObservationsBiologiquesControleur(ObservationBiologiqueUseCases useCases) {
        this.useCases = useCases;
    }

    @GetMapping
    public ResponseEntity<PagedResult<ObservationBiologique>> lister(
            @RequestParam UUID centreId,
            @RequestParam UUID patientId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(useCases.listerParPatient(new CentreId(centreId), patientId, page, size));
    }

    @GetMapping("/{observationId}")
    public ResponseEntity<ObservationBiologique> consulter(
            @PathVariable UUID observationId,
            @RequestParam UUID centreId) {
        return ResponseEntity.ok(useCases.consulter(new ObservationBiologiqueId(observationId),
                new CentreId(centreId)));
    }

    @PostMapping
    public ResponseEntity<ObservationBiologiqueId> enregistrer(
            @RequestBody EnregistrerObservationRequete req) {
        ObservationBiologiqueId id = useCases.enregistrer(
                new ObservationBiologiqueUseCases.EnregistrerObservationCommande(
                        req.patientId(), new CentreId(req.centreId()), req.code(), req.libelle(),
                        req.valeur(), req.unite(), req.seuilBas(), req.seuilHaut(),
                        req.dateObservation(), req.prescripteurId(), req.laboratoire(),
                        req.commentaire()), Instant.now());
        return ResponseEntity.ok(id);
    }
}
