package com.hemodialyse.backend.domain.stock.port;

import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.domain.stock.model.BonSortie;
import com.hemodialyse.backend.domain.stock.model.SortieRequestItem;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Primary port: bon de sortie (BS) using FEFO, linked to a hemodialysis seance.
 */
public interface BonSortieUseCase {
    BonSortie create(CenterId centerId, UUID seanceId, UUID patientId, String poste,
                     LocalDate dateSortie, List<SortieRequestItem> items, String userId);

    BonSortie get(CenterId centerId, UUID bonId);

    List<BonSortie> list(CenterId centerId);

    BonSortie update(CenterId centerId, UUID bonId, UUID seanceId, UUID patientId,
                     String poste, LocalDate dateSortie, List<SortieRequestItem> items, String userId);

    /**
     * Cancel all stock exits already recorded for a given article+seance pair.
     * Lot quantities are restored (FEFO reversal), movements deleted, and PMP recalculated.
     * Called before updating a consommable quantity on a validated seance.
     */
    void reverseArticleConsommation(CenterId centerId, UUID seanceId, UUID articleId, String userId);

    /**
     * Issue new FEFO stock exits for an article on an already-validated seance.
     * Equivalent to calling {@code create()} but scoped to a single article and
     * carrying the seance context. Callers should call
     * {@link #reverseArticleConsommation} first when this is an update (not a pure add).
     */
    void addArticleConsommation(CenterId centerId, UUID seanceId, UUID patientId,
                                LocalDate dateSeance, UUID articleId, BigDecimal quantite, String userId);
}

