package com.hemodialyse.backend.contexts.pilotagedirection.interfaces.http;

import com.hemodialyse.backend.contexts.pilotagedirection.application.InstantaneMensuelUseCases;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.InstantaneMensuel;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.InstantaneMensuelId;
import com.hemodialyse.backend.contexts.pilotagedirection.interfaces.http.dto.CapturerInstantaneRequete;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.Mois;
import com.hemodialyse.backend.contexts.sharedkernel.domain.PagedResult;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/pilotage/instantanes-mensuels")
public class InstantanesMensuelsControleur {

    private final InstantaneMensuelUseCases useCases;

    public InstantanesMensuelsControleur(InstantaneMensuelUseCases useCases) {
        this.useCases = useCases;
    }

    @GetMapping
    public ResponseEntity<PagedResult<InstantaneMensuel>> lister(
            @RequestParam UUID centreId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(useCases.listerParCentre(new CentreId(centreId), page, size));
    }

    @GetMapping("/{instantaneId}")
    public ResponseEntity<InstantaneMensuel> consulter(
            @PathVariable UUID instantaneId,
            @RequestParam UUID centreId) {
        return ResponseEntity.ok(useCases.consulter(new InstantaneMensuelId(instantaneId),
                new CentreId(centreId)));
    }

    @PostMapping
    public ResponseEntity<InstantaneMensuelId> capturer(@RequestBody CapturerInstantaneRequete req) {
        InstantaneMensuelId id = useCases.capturer(
                new InstantaneMensuelUseCases.CapturerInstantaneCommande(
                        new CentreId(req.centreId()), Mois.de(req.annee(), req.mois()),
                        req.donnees()), Instant.now());
        return ResponseEntity.ok(id);
    }
}
