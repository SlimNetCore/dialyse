package com.hemodialyse.backend.infrastructure.web.rest;

import com.hemodialyse.backend.domain.comptabilite.port.ComptabiliteStockUseCase;
import com.hemodialyse.backend.domain.comptabilite.port.ComptabiliteStockUseCase.Synchronisation;
import com.hemodialyse.backend.infrastructure.security.CenterAccessGuard;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Comptabilisation du stock d'un centre (slice C6, encore legacy). Le plan comptable, les journaux, le compte client des
 * payeurs, le mapping, les règles de TVA, les écritures, les modèles et pièces, les ventes, les règlements et la
 * reconstitution ({@code /rebuild}) sont servis par le contexte {@code contexts/comptabilite} (slices C1 à C5).
 * Le centre de chaque requête est confronté à celui de la session ({@link CenterAccessGuard}).
 */
@RestController
@RequestMapping("/api/v1/comptabilite")
public class ComptabiliteRestController {

    /**
     * Plus longue période qu'une demande de comptabilisation du stock peut couvrir.
     */
    static final int JOURS_SYNCHRONISATION_MAX = 366;

    private final ComptabiliteStockUseCase comptabiliteStock;
    private final CenterAccessGuard centerAccessGuard;

    public ComptabiliteRestController(ComptabiliteStockUseCase comptabiliteStock,
                                      CenterAccessGuard centerAccessGuard) {
        this.comptabiliteStock = comptabiliteStock;
        this.centerAccessGuard = centerAccessGuard;
    }

    /**
     * Centre de la session ; refuse un centre demandé qui n'est pas le sien.
     */
    private UUID centre(UUID demande) {
        return centerAccessGuard.requireCenter(demande).value();
    }

    // ─── Comptabilisation du stock ───────────────────────────────────────────

    /**
     * Met la comptabilité en accord avec le stock sur une période (rejouable sans doublon).
     */
    @PreAuthorize("hasAnyRole('ADMIN')")
    @PostMapping("/stock/synchroniser")
    public ResponseEntity<Synchronisation> synchroniserStock(@RequestBody @Valid RebuildEcrituresRequest req) {
        if (req.to().isBefore(req.from()) || req.from().plusDays(JOURS_SYNCHRONISATION_MAX).isBefore(req.to())) {
            throw new IllegalArgumentException("La période à comptabiliser doit couvrir de 1 à "
                    + JOURS_SYNCHRONISATION_MAX + " jours");
        }
        return ResponseEntity.ok(comptabiliteStock.synchroniser(centre(req.centerId()), req.from(), req.to(),
                LocalDate.now(ZoneOffset.UTC)));
    }

    // ─── Request records ─────────────────────────────────────────────────────

    record RebuildEcrituresRequest(
            UUID centerId,
            @NotNull LocalDate from,
            @NotNull LocalDate to) {
    }
}
