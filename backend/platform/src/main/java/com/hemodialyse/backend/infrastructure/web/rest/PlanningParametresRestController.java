package com.hemodialyse.backend.infrastructure.web.rest;

import com.hemodialyse.backend.application.planning.PlanningParametresService;
import com.hemodialyse.backend.domain.planning.model.JourSemaine;
import com.hemodialyse.backend.domain.planning.model.PlanningParametres;
import com.hemodialyse.backend.infrastructure.security.CenterAccessGuard;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Paramétrage du planning d'un centre : jours d'ouverture et salles d'isolement. Lecture pour le personnel, écriture
 * réservée à l'administrateur.
 */
@RestController
@RequestMapping("/api/v1/planning/parametres")
public class PlanningParametresRestController {

    private final PlanningParametresService service;
    private final CenterAccessGuard centerAccessGuard;

    public PlanningParametresRestController(PlanningParametresService service, CenterAccessGuard centerAccessGuard) {
        this.service = service;
        this.centerAccessGuard = centerAccessGuard;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','SECRETAIRE','MEDECIN','INFIRMIER')")
    public ResponseEntity<ParametresResponse> lire(@RequestParam(required = false) UUID centerId) {
        UUID centre = centerAccessGuard.requireCenter(centerId).value();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(ParametresResponse.de(service.lire(centre)));
    }

    @PutMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ParametresResponse> enregistrer(
            @RequestParam(required = false) UUID centerId, @RequestBody ParametresRequest request) {
        UUID centre = centerAccessGuard.requireCenter(centerId).value();
        Set<JourSemaine> jours = request.joursOuverts() == null || request.joursOuverts().isEmpty()
                ? Set.of() : EnumSet.copyOf(request.joursOuverts());
        Set<UUID> salles = request.sallesIsolement() == null ? Set.of() : Set.copyOf(request.sallesIsolement());
        return ResponseEntity.ok(ParametresResponse.de(service.enregistrer(centre, jours, salles)));
    }

    public record ParametresRequest(List<JourSemaine> joursOuverts, List<UUID> sallesIsolement) {
    }

    public record ParametresResponse(List<JourSemaine> joursOuverts, List<UUID> sallesIsolement) {
        static ParametresResponse de(PlanningParametres p) {
            return new ParametresResponse(
                    EnumSet.copyOf(p.joursOuverts()).stream().toList(),
                    p.sallesIsolement().stream().sorted().toList());
        }
    }
}
