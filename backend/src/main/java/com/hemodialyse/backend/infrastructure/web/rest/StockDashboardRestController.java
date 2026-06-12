package com.hemodialyse.backend.infrastructure.web.rest;

import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.domain.stock.port.StockDashboardUseCase;
import com.hemodialyse.backend.domain.stock.service.PmpRecalculationCoordinator;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/stock/dashboard")
public class StockDashboardRestController {

    private final StockDashboardUseCase useCase;
    private final PmpRecalculationCoordinator recalcCoordinator;

    public StockDashboardRestController(StockDashboardUseCase useCase,
                                        PmpRecalculationCoordinator recalcCoordinator) {
        this.useCase = useCase;
        this.recalcCoordinator = recalcCoordinator;
    }

    @PreAuthorize("hasAnyRole('ADMIN','PHARMACIEN','INFIRMIER')")
    @GetMapping("/stock-valorise")
    public ResponseEntity<?> stockValorise(@RequestParam UUID centerId) {
        return ResponseEntity.ok(useCase.stockValorise(CenterId.of(centerId)));
    }

    @PreAuthorize("hasAnyRole('ADMIN','PHARMACIEN')")
    @GetMapping("/tracabilite/{lotId}")
    public ResponseEntity<?> tracabilite(@PathVariable UUID lotId, @RequestParam UUID centerId) {
        return ResponseEntity.ok(useCase.tracabilite(CenterId.of(centerId), lotId));
    }

    @PreAuthorize("hasAnyRole('ADMIN','PHARMACIEN','INFIRMIER')")
    @GetMapping("/alertes")
    public ResponseEntity<?> alertes(@RequestParam UUID centerId) {
        return ResponseEntity.ok(useCase.alertes(CenterId.of(centerId)));
    }

    @PreAuthorize("hasAnyRole('ADMIN','PHARMACIEN','INFIRMIER')")
    @GetMapping("/pmp-explain/{articleId}")
    public ResponseEntity<?> pmpExplain(@PathVariable UUID articleId, @RequestParam UUID centerId) {
        return ResponseEntity.ok(useCase.pmpExplain(CenterId.of(centerId), articleId));
    }

    @PreAuthorize("hasAnyRole('ADMIN','PHARMACIEN')")
    @PostMapping("/pmp-recalc/start")
    public ResponseEntity<?> startPmpRecalc(@RequestBody StartPmpRecalcRequest req) {
        UUID jobId = recalcCoordinator.start(CenterId.of(req.centerId()), req.articleIds());
        return ResponseEntity.ok(new StartPmpRecalcResponse(jobId));
    }

    @PreAuthorize("hasAnyRole('ADMIN','PHARMACIEN','INFIRMIER')")
    @GetMapping("/pmp-recalc/jobs/{jobId}")
    public ResponseEntity<?> recalcStatus(@PathVariable UUID jobId) {
        return ResponseEntity.ok(recalcCoordinator.get(jobId));
    }

    @PreAuthorize("hasAnyRole('ADMIN','PHARMACIEN','INFIRMIER')")
    @GetMapping("/pmp-recalc/locks")
    public ResponseEntity<?> recalcLocks(@RequestParam UUID centerId) {
        return ResponseEntity.ok(recalcCoordinator.lockedArticles(CenterId.of(centerId)));
    }

    private record StartPmpRecalcRequest(UUID centerId, List<UUID> articleIds) {
    }

    private record StartPmpRecalcResponse(UUID jobId) {
    }
}


