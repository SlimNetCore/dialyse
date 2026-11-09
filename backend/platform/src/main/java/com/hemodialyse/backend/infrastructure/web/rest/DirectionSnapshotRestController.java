package com.hemodialyse.backend.infrastructure.web.rest;

import com.hemodialyse.backend.application.direction.DirectionReportPdfService;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.InstantanesMensuelsDeLaDirection;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.InstantanesMensuelsDeLaDirection.Info;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.InstantanesMensuelsDeLaDirection.Instantane;
import com.hemodialyse.backend.infrastructure.security.DirectionAccessGuard;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Instantanés mensuels et rapports PDF de la direction. Société tirée de la session (jamais d'un paramètre),
 * revérifiée à chaque appel ; lecture seule hormis la production d'un instantané de mois écoulé, idempotente.
 * PD2 (ADR 0103) : les instantanés sont servis par le contexte Pilotage direction ({@link InstantanesMensuelsDeLaDirection}) ; le contrôleur
 * reste en plateforme avec le jeton, la garde de société ({@link DirectionAccessGuard}) et le rendu PDF (comme les routes du lot A, ADR 0102).
 */
@RestController
@RequestMapping("/api/v1/direction/snapshots")
@PreAuthorize("hasRole('DIRECTION')")
public class DirectionSnapshotRestController {

    private final DirectionAccessGuard guard;
    private final InstantanesMensuelsDeLaDirection snapshots;
    private final DirectionReportPdfService reports;

    public DirectionSnapshotRestController(DirectionAccessGuard guard, InstantanesMensuelsDeLaDirection snapshots,
                                           DirectionReportPdfService reports) {
        this.guard = guard;
        this.snapshots = snapshots;
        this.reports = reports;
    }

    @GetMapping
    public ResponseEntity<List<Info>> list() {
        UUID societeId = guard.requireSociete();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(snapshots.lister(societeId));
    }

    @GetMapping("/{mois}")
    public ResponseEntity<Instantane> get(@PathVariable String mois) {
        UUID societeId = guard.requireSociete();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(snapshots.lire(societeId, mois));
    }

    /**
     * Fige le mois s'il ne l'est pas encore (mois écoulé uniquement) ; idempotent.
     */
    @PostMapping("/{mois}")
    public ResponseEntity<Instantane> ensure(@PathVariable String mois) {
        UUID societeId = guard.requireSociete();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(snapshots.figer(societeId, mois));
    }

    @GetMapping("/{mois}/report")
    public ResponseEntity<byte[]> report(@PathVariable String mois) {
        UUID societeId = guard.requireSociete();
        Instantane snapshot = snapshots.lire(societeId, mois);
        byte[] pdf = reports.pdf(societeId, snapshot);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("rapport-direction-" + snapshot.mois() + ".pdf").build().toString())
                .body(pdf);
    }
}
