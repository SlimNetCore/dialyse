package com.hemodialyse.backend.infrastructure.web.rest;

import com.hemodialyse.backend.application.supervision.SupervisionBaseService;
import com.hemodialyse.backend.application.supervision.TriRequetes;
import com.hemodialyse.backend.infrastructure.web.dto.response.PagedResponse;
import com.hemodialyse.backend.infrastructure.web.dto.response.SupervisionBaseResponses.Requete;
import com.hemodialyse.backend.infrastructure.web.dto.response.SupervisionBaseResponses.Statut;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Performance de la base pour le propriétaire de la plateforme (SUPERADMIN uniquement) : requêtes les plus coûteuses
 * relevées par {@code pg_stat_statements}. Données techniques de toute la plateforme, hors périmètre d'un centre.
 */
@RestController
@RequestMapping("/api/v1/supervision/base-donnees")
@PreAuthorize("hasRole('SUPERADMIN')")
public class SupervisionBaseRestController {

    private final SupervisionBaseService supervision;

    public SupervisionBaseRestController(SupervisionBaseService supervision) {
        this.supervision = supervision;
    }

    @GetMapping("/statut")
    public ResponseEntity<Statut> statut() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Statut.from(supervision.statut()));
    }

    @GetMapping("/requetes")
    public ResponseEntity<PagedResponse<Requete>> requetes(
            @RequestParam(required = false) String tri,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var resultat = supervision.requetes(TriRequetes.depuis(tri), page, size);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(PagedResponse.from(resultat, Requete::from));
    }

    /**
     * Remet les compteurs à zéro (tracé automatiquement au journal d'audit, comme toute écriture).
     */
    @PostMapping("/reinitialisation")
    public ResponseEntity<Void> reinitialiser() {
        supervision.reinitialiser();
        return ResponseEntity.noContent().build();
    }
}
