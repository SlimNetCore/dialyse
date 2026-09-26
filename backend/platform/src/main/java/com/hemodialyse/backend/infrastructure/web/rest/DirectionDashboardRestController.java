package com.hemodialyse.backend.infrastructure.web.rest;

import com.hemodialyse.backend.application.direction.DirectionDashboardQueryService;
import com.hemodialyse.backend.application.direction.DirectionDashboardQueryService.Overview;
import com.hemodialyse.backend.application.direction.DirectionDashboardQueryService.SocieteInfo;
import com.hemodialyse.backend.application.direction.DirectionIndicatorsQueryService;
import com.hemodialyse.backend.application.direction.DirectionIndicatorsQueryService.Indicators;
import com.hemodialyse.backend.infrastructure.security.DirectionAccessGuard;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Tableaux de bord de la direction d'une société : lecture seule, agrégats anonymes uniquement.
 * <p>
 * La société vient du jeton de la session (jamais d'un paramètre) et est revérifiée à chaque appel ; ces routes sont
 * en outre les seules que le rôle DIRECTION peut appeler ({@code RoleScopeFilter}).
 */
@RestController
@RequestMapping("/api/v1/direction")
@PreAuthorize("hasRole('DIRECTION')")
public class DirectionDashboardRestController {

    private final DirectionAccessGuard guard;
    private final DirectionDashboardQueryService queries;
    private final DirectionIndicatorsQueryService indicators;

    public DirectionDashboardRestController(DirectionAccessGuard guard, DirectionDashboardQueryService queries,
                                            DirectionIndicatorsQueryService indicators) {
        this.guard = guard;
        this.queries = queries;
        this.indicators = indicators;
    }

    /**
     * Société de la direction connectée et liste de ses centres (pour les filtres).
     */
    @GetMapping("/societe")
    public ResponseEntity<SocieteInfo> societe() {
        UUID societeId = guard.requireSociete();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(queries.societe(societeId));
    }

    /**
     * Vue d'ensemble par centre et consolidée sur la période (par défaut : depuis le 1er janvier).
     */
    @GetMapping("/overview")
    public ResponseEntity<Overview> overview(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        UUID societeId = guard.requireSociete();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(queries.overview(societeId, from, to));
    }

    /**
     * Indicateurs médicaux (cibles KDIGO), de stock et alertes : agrégats anonymes par centre et consolidés.
     */
    @GetMapping("/indicators")
    public ResponseEntity<Indicators> indicators(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        UUID societeId = guard.requireSociete();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(indicators.indicators(societeId, from, to));
    }
}
