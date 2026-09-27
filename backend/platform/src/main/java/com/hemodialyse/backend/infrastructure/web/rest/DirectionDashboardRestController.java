package com.hemodialyse.backend.infrastructure.web.rest;

import com.hemodialyse.backend.application.direction.DirectionDashboardQueryService;
import com.hemodialyse.backend.application.direction.DirectionDashboardQueryService.Overview;
import com.hemodialyse.backend.application.direction.DirectionDashboardQueryService.SocieteInfo;
import com.hemodialyse.backend.application.direction.DirectionBreakdownQueryService;
import com.hemodialyse.backend.application.direction.DirectionBreakdownQueryService.Breakdown;
import com.hemodialyse.backend.application.direction.DirectionAlertHistoryService;
import com.hemodialyse.backend.application.direction.DirectionAlertHistoryService.Entry;
import com.hemodialyse.backend.application.direction.DirectionReportPdfService;
import com.hemodialyse.backend.application.direction.DirectionIndicatorsQueryService;
import com.hemodialyse.backend.application.direction.DirectionIndicatorsQueryService.Indicators;
import com.hemodialyse.backend.infrastructure.security.DirectionAccessGuard;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
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
    private final DirectionBreakdownQueryService breakdowns;
    private final DirectionReportPdfService reports;
    private final DirectionAlertHistoryService alertHistory;

    public DirectionDashboardRestController(DirectionAccessGuard guard, DirectionDashboardQueryService queries,
                                            DirectionIndicatorsQueryService indicators,
                                            DirectionBreakdownQueryService breakdowns,
                                            DirectionReportPdfService reports,
                                            DirectionAlertHistoryService alertHistory) {
        this.reports = reports;
        this.guard = guard;
        this.queries = queries;
        this.indicators = indicators;
        this.breakdowns = breakdowns;
        this.alertHistory = alertHistory;
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

    /**
     * Répartitions par centre : patients par sexe et par âge, par caisse d'assurance (patients, séances, CA HT) et
     * traitement de l'anémie. Agrégats anonymes.
     */
    @GetMapping("/breakdown")
    public ResponseEntity<Breakdown> breakdown(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        UUID societeId = guard.requireSociete();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(breakdowns.breakdown(societeId, from, to));
    }

    /**
     * Rapport imprimable (PDF) de la période : en-tête et pied de page de la société, puis toutes les statistiques
     * du tableau de bord.
     */
    @GetMapping("/report")
    public ResponseEntity<byte[]> report(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        UUID societeId = guard.requireSociete();
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("rapport-direction.pdf").build().toString())
                .body(reports.pdf(societeId, from, to));
    }

    /**
     * Historique récent des alertes (apparitions et résolutions), du plus récemment actif au plus ancien.
     * Alimenté uniquement pendant que la direction est connectée (voir {@code DirectionRealtimeService}).
     */
    @GetMapping("/alerts/history")
    public ResponseEntity<List<Entry>> alertsHistory(
            @RequestParam(required = false, defaultValue = "50") int limit) {
        UUID societeId = guard.requireSociete();
        int bounded = Math.max(1, Math.min(limit, 200));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(alertHistory.history(societeId, bounded));
    }
}
