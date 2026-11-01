package com.hemodialyse.backend.contexts.dossiermedical.interfaces.http;

import com.hemodialyse.backend.contexts.dossiermedical.application.SerologieUseCases;
import com.hemodialyse.backend.contexts.dossiermedical.domain.ResultatSerologie;
import com.hemodialyse.backend.contexts.dossiermedical.domain.Serologie;
import com.hemodialyse.backend.contexts.dossiermedical.domain.SerologieId;
import com.hemodialyse.backend.contexts.dossiermedical.domain.TypeSerologie;
import com.hemodialyse.backend.contexts.dossiermedical.interfaces.http.dto.EnregistrerSerologieRequete;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.PagedResult;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/dossier-medical/serologies")
public class SerologiesControleur {

    private final SerologieUseCases useCases;

    public SerologiesControleur(SerologieUseCases useCases) {
        this.useCases = useCases;
    }

    @GetMapping
    public ResponseEntity<PagedResult<Serologie>> lister(
            @RequestParam UUID centreId,
            @RequestParam UUID patientId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(useCases.listerParPatient(new CentreId(centreId), patientId, page, size));
    }

    @GetMapping("/{serologieId}")
    public ResponseEntity<Serologie> consulter(
            @PathVariable UUID serologieId,
            @RequestParam UUID centreId) {
        return ResponseEntity.ok(useCases.consulter(new SerologieId(serologieId), new CentreId(centreId)));
    }

    @PostMapping
    public ResponseEntity<SerologieId> enregistrer(
            @RequestBody EnregistrerSerologieRequete req) {
        SerologieId id = useCases.enregistrer(new SerologieUseCases.EnregistrerSerologieCommande(
                req.patientId(), new CentreId(req.centreId()), req.prescripteurId(),
                req.datePrelevement(), TypeSerologie.valueOf(req.typeSerologie()),
                ResultatSerologie.valueOf(req.resultat()), req.valeur(), req.unite(),
                req.dateResultat(), req.laboratoire(), req.commentaire()), Instant.now());
        return ResponseEntity.ok(id);
    }
}
