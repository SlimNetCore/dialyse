package com.hemodialyse.backend.contexts.pilotagedirection.interfaces.http;

import com.hemodialyse.backend.contexts.pilotagedirection.application.AlerteDirectionUseCases;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.AlerteDirection;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.AlerteDirectionId;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.TypeAlerte;
import com.hemodialyse.backend.contexts.pilotagedirection.interfaces.http.dto.AcquitterAlerteRequete;
import com.hemodialyse.backend.contexts.pilotagedirection.interfaces.http.dto.GenererAlerteRequete;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.PagedResult;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/pilotage/alertes")
public class AlertesDirectionControleur {

    private final AlerteDirectionUseCases useCases;

    public AlertesDirectionControleur(AlerteDirectionUseCases useCases) {
        this.useCases = useCases;
    }

    @GetMapping
    public ResponseEntity<PagedResult<AlerteDirection>> lister(
            @RequestParam UUID centreId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(useCases.listerParCentre(new CentreId(centreId), page, size));
    }

    @GetMapping("/{alerteId}")
    public ResponseEntity<AlerteDirection> consulter(
            @PathVariable UUID alerteId,
            @RequestParam UUID centreId) {
        return ResponseEntity.ok(useCases.consulter(new AlerteDirectionId(alerteId),
                new CentreId(centreId)));
    }

    @PostMapping
    public ResponseEntity<AlerteDirectionId> generer(@RequestBody GenererAlerteRequete req) {
        AlerteDirectionId id = useCases.generer(
                new AlerteDirectionUseCases.GenererAlerteCommande(
                        new CentreId(req.centreId()), TypeAlerte.valueOf(req.type()),
                        req.seuil(), req.message(), req.date()), Instant.now());
        return ResponseEntity.ok(id);
    }

    @PostMapping("/{alerteId}/acquittement")
    public ResponseEntity<AlerteDirection> acquitter(
            @PathVariable UUID alerteId,
            @RequestBody AcquitterAlerteRequete req) {
        return ResponseEntity.ok(useCases.acquitter(new AlerteDirectionId(alerteId),
                new CentreId(req.centreId()), Instant.now()));
    }
}
