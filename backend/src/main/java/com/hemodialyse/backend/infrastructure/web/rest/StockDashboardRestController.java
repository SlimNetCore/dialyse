package com.hemodialyse.backend.infrastructure.web.rest;

import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.domain.stock.port.StockDashboardUseCase;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/stock/dashboard")
public class StockDashboardRestController {

    private final StockDashboardUseCase useCase;

    public StockDashboardRestController(StockDashboardUseCase useCase) {
        this.useCase = useCase;
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
}


