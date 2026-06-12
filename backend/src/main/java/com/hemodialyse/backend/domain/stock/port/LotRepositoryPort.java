package com.hemodialyse.backend.domain.stock.port;

import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.domain.stock.model.Lot;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LotRepositoryPort {
    Lot save(Lot lot);

    Optional<Lot> findById(UUID id, CenterId centerId);

    /**
     * FEFO ordering: lots with remaining quantity for an article, earliest expiry first.
     */
    List<Lot> findAvailableByArticleFefo(UUID articleId, CenterId centerId);

    /**
     * Lots expiring on or before the given date (expiration alerts).
     */
    List<Lot> findExpiringBefore(CenterId centerId, LocalDate threshold);

    List<Lot> findByArticle(UUID articleId, CenterId centerId);
}

