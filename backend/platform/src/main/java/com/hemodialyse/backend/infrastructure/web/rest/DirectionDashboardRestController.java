package com.hemodialyse.backend.infrastructure.web.rest;

import com.hemodialyse.backend.application.direction.DirectionAbsencesQueryService;
import com.hemodialyse.backend.application.direction.DirectionAbsencesQueryService.AbsencesOverview;
import com.hemodialyse.backend.application.direction.DirectionCapaciteQueryService;
import com.hemodialyse.backend.application.direction.DirectionCapaciteQueryService.CapaciteOverview;
import com.hemodialyse.backend.application.direction.DirectionGmaoQueryService;
import com.hemodialyse.backend.application.direction.DirectionGmaoQueryService.GmaoOverview;
import com.hemodialyse.backend.application.direction.DirectionStockGroupesQueryService;
import com.hemodialyse.backend.application.direction.DirectionStockGroupesQueryService.StockGroupesOverview;
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
 * Lectures de la direction d'une société encore hébergées par la plateforme : {@code /gmao}, {@code /stock-groupes}, {@code /capacite} et
 * {@code /absences} (PL10b2 et PL10c les confieront à GMAO, Stock, Planification et Absences). La société vient du jeton de la session
 * (jamais d'un paramètre) et est revérifiée à chaque appel.
 * <p>
 * PL10b1 (ADR 0151) : {@code /societe}, {@code /overview}, {@code /breakdown}, {@code /indicators} et {@code /alerts/history} sont servies
 * par le Pilotage direction ({@code TableauDeBordDeLaDirectionControleur}) ; {@code /report} et {@code /snapshots} l'étaient déjà (PL9, PL10a).
 * Mêmes URL, mêmes rôles, mêmes codes.
 */
@RestController
@RequestMapping("/api/v1/direction")
@PreAuthorize("hasRole('DIRECTION')")
public class DirectionDashboardRestController {

    private final DirectionAccessGuard guard;
    private final DirectionGmaoQueryService gmaoQueries;
    private final DirectionStockGroupesQueryService stockGroupes;
    private final DirectionAbsencesQueryService absences;
    private final DirectionCapaciteQueryService capacites;

    public DirectionDashboardRestController(DirectionAccessGuard guard,
                                            DirectionGmaoQueryService gmaoQueries,
                                            DirectionStockGroupesQueryService stockGroupes,
                                            DirectionAbsencesQueryService absences,
                                            DirectionCapaciteQueryService capacites) {
        this.capacites = capacites;
        this.absences = absences;
        this.stockGroupes = stockGroupes;
        this.guard = guard;
        this.gmaoQueries = gmaoQueries;
    }

    /**
     * Aide à la décision GMAO : coût de maintenance, état du parc d'équipements et temps
     * d'indisponibilité, par centre et consolidés (module GMAO v2).
     */
    @GetMapping("/gmao")
    public ResponseEntity<GmaoOverview> gmao(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        UUID societeId = guard.requireSociete();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(gmaoQueries.gmao(societeId, from, to));
    }

    /**
     * Valorisation du stock des groupes d'articles (ex. « KIT CNAS ») sur la période : stock début/fin, entrées,
     * sorties, par centre et consolidée pour la société.
     */
    @GetMapping("/stock-groupes")
    public ResponseEntity<StockGroupesOverview> stockGroupes(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        UUID societeId = guard.requireSociete();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(stockGroupes.groupes(societeId, from, to));
    }

    /**
     * Capacité théorique des centres (générateurs, postes actifs, séries) et taux d'occupation de la file active sur la
     * période : par centre et consolidée pour la société.
     */
    @GetMapping("/capacite")
    public ResponseEntity<CapaciteOverview> capacite(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        UUID societeId = guard.requireSociete();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(capacites.capacite(societeId, from, to));
    }

    /**
     * Absences des patients sur la période : nombre, motifs, valorisation HT, taux d'absentéisme et part du CA HT,
     * par centre et consolidées pour la société.
     */
    @GetMapping("/absences")
    public ResponseEntity<AbsencesOverview> absences(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        UUID societeId = guard.requireSociete();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(absences.absences(societeId, from, to));
    }
}
