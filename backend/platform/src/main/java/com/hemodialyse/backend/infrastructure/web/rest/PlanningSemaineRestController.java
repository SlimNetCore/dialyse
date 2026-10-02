package com.hemodialyse.backend.infrastructure.web.rest;

import com.hemodialyse.backend.application.planning.PlanningSemaineQueryService;
import com.hemodialyse.backend.domain.planning.model.PlanningSemaine.SemainePlanning;
import com.hemodialyse.backend.infrastructure.security.CenterAccessGuard;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Planning réel d'une semaine : qui dialyse où, jours fermés et conflits. Lecture seule, bornée au centre.
 */
@RestController
@RequestMapping("/api/v1/planning/semaine")
@PreAuthorize("hasAnyRole('ADMIN','SECRETAIRE','MEDECIN','INFIRMIER')")
public class PlanningSemaineRestController {

    private final PlanningSemaineQueryService service;
    private final CenterAccessGuard centerAccessGuard;

    public PlanningSemaineRestController(PlanningSemaineQueryService service, CenterAccessGuard centerAccessGuard) {
        this.service = service;
        this.centerAccessGuard = centerAccessGuard;
    }

    /**
     * @param date un jour de la semaine voulue (défaut : aujourd'hui, UTC)
     */
    @GetMapping
    public ResponseEntity<SemainePlanning> semaine(
            @RequestParam(required = false) UUID centerId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        UUID centre = centerAccessGuard.requireCenter(centerId).value();
        LocalDate jour = date != null ? date : LocalDate.now(ZoneOffset.UTC);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.semaine(centre, jour));
    }
}
