package com.hemodialyse.backend.contexts.seance.interfaces.http;

import com.hemodialyse.backend.contexts.seance.application.DerogationRegularisationUseCases;
import com.hemodialyse.backend.contexts.seance.domain.DerogationId;
import com.hemodialyse.backend.contexts.seance.domain.DerogationRegularisation;
import com.hemodialyse.backend.contexts.seance.domain.SeanceId;
import com.hemodialyse.backend.contexts.seance.interfaces.http.dto.CreerDerogationRequest;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/derogations-regularisation")
public class DerogationsRegularisationControleur {
    private final DerogationRegularisationUseCases useCases;

    public DerogationsRegularisationControleur(DerogationRegularisationUseCases useCases) {
        this.useCases = useCases;
    }

    @PostMapping
    public ResponseEntity<DerogationId> creer(@RequestBody CreerDerogationRequest req) {
        DerogationId id = useCases.creer(new DerogationRegularisationUseCases.CreerDerogationCommande(
                new SeanceId(req.seanceId()), new CentreId(req.centreId()), req.motif(), req.autorite()));
        return ResponseEntity.ok(id);
    }

    @GetMapping("/{id}")
    public ResponseEntity<DerogationRegularisation> consulter(@PathVariable UUID id, @RequestParam UUID centreId) {
        return ResponseEntity.ok(useCases.consulter(new DerogationId(id), new CentreId(centreId)));
    }

    @GetMapping("/seance/{seanceId}")
    public ResponseEntity<DerogationRegularisation> consulterParSeance(@PathVariable UUID seanceId, @RequestParam UUID centreId) {
        return ResponseEntity.ok(useCases.consulterParSeance(new SeanceId(seanceId), new CentreId(centreId)));
    }
}