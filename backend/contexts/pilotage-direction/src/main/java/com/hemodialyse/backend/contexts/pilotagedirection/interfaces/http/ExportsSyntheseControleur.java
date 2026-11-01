package com.hemodialyse.backend.contexts.pilotagedirection.interfaces.http;

import com.hemodialyse.backend.contexts.pilotagedirection.application.ExportSyntheseUseCases;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.ExportSynthese;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.ExportSyntheseId;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.FormatExport;
import com.hemodialyse.backend.contexts.pilotagedirection.interfaces.http.dto.GenererExportRequete;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/pilotage/exports-synthese")
public class ExportsSyntheseControleur {

    private final ExportSyntheseUseCases useCases;

    public ExportsSyntheseControleur(ExportSyntheseUseCases useCases) {
        this.useCases = useCases;
    }

    @GetMapping("/{exportId}")
    public ResponseEntity<ExportSynthese> telecharger(
            @PathVariable UUID exportId,
            @RequestParam UUID centreId) {
        return ResponseEntity.ok(useCases.telecharger(new ExportSyntheseId(exportId),
                new CentreId(centreId)));
    }

    @PostMapping
    public ResponseEntity<ExportSyntheseId> generer(@RequestBody GenererExportRequete req) {
        ExportSyntheseId id = useCases.generer(
                new ExportSyntheseUseCases.GenererExportCommande(
                        new CentreId(req.centreId()), req.periode(),
                        FormatExport.valueOf(req.format())), Instant.now());
        return ResponseEntity.ok(id);
    }
}
