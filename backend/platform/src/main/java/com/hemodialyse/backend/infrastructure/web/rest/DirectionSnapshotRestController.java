package com.hemodialyse.backend.infrastructure.web.rest;

import com.hemodialyse.backend.application.direction.DirectionReportPdfService;
import com.hemodialyse.backend.application.direction.DirectionSnapshotService;
import com.hemodialyse.backend.application.direction.DirectionSnapshotService.Info;
import com.hemodialyse.backend.application.direction.DirectionSnapshotService.Snapshot;
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
 */
@RestController
@RequestMapping("/api/v1/direction/snapshots")
@PreAuthorize("hasRole('DIRECTION')")
public class DirectionSnapshotRestController {

    private final DirectionAccessGuard guard;
    private final DirectionSnapshotService snapshots;
    private final DirectionReportPdfService reports;

    public DirectionSnapshotRestController(DirectionAccessGuard guard, DirectionSnapshotService snapshots,
                                           DirectionReportPdfService reports) {
        this.guard = guard;
        this.snapshots = snapshots;
        this.reports = reports;
    }

    @GetMapping
    public ResponseEntity<List<Info>> list() {
        UUID societeId = guard.requireSociete();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(snapshots.list(societeId));
    }

    @GetMapping("/{mois}")
    public ResponseEntity<Snapshot> get(@PathVariable String mois) {
        UUID societeId = guard.requireSociete();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(snapshots.get(societeId, mois));
    }

    /**
     * Fige le mois s'il ne l'est pas encore (mois écoulé uniquement) ; idempotent.
     */
    @PostMapping("/{mois}")
    public ResponseEntity<Snapshot> ensure(@PathVariable String mois) {
        UUID societeId = guard.requireSociete();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(snapshots.ensure(societeId, mois));
    }

    @GetMapping("/{mois}/report")
    public ResponseEntity<byte[]> report(@PathVariable String mois) {
        UUID societeId = guard.requireSociete();
        Snapshot snapshot = snapshots.get(societeId, mois);
        byte[] pdf = reports.pdf(societeId, snapshot);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("rapport-direction-" + snapshot.mois() + ".pdf").build().toString())
                .body(pdf);
    }
}
