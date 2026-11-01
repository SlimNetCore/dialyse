package com.hemodialyse.backend.contexts.pilotagedirection.interfaces.http;

import com.hemodialyse.backend.contexts.pilotagedirection.application.TableauDeBordUseCases;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.TableauDeBord;
import com.hemodialyse.backend.contexts.pilotagedirection.interfaces.http.dto.ActualiserTableauRequete;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/pilotage/tableau-de-bord")
public class TableauxDeBordControleur {

    private final TableauDeBordUseCases useCases;

    public TableauxDeBordControleur(TableauDeBordUseCases useCases) {
        this.useCases = useCases;
    }

    @GetMapping
    public ResponseEntity<TableauDeBord> consulter(@RequestParam UUID centreId) {
        return ResponseEntity.ok(useCases.consulter(new CentreId(centreId)));
    }

    @PostMapping
    public ResponseEntity<TableauDeBord> actualiser(@RequestBody ActualiserTableauRequete req) {
        TableauDeBord tableau = useCases.actualiser(
                new TableauDeBordUseCases.ActualiserTableauDeBordCommande(
                        new CentreId(req.centreId()), req.indicateurs()), Instant.now());
        return ResponseEntity.ok(tableau);
    }
}
