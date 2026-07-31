package com.hemodialyse.backend.application.stock;

import com.hemodialyse.backend.domain.article.port.ArticleRepositoryPort;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.domain.stock.model.BonSortie;
import com.hemodialyse.backend.domain.stock.model.SortieRequestItem;
import com.hemodialyse.backend.domain.stock.port.BonSortieRepositoryPort;
import com.hemodialyse.backend.domain.stock.port.BonSortieUseCase;
import com.hemodialyse.backend.domain.stock.port.LotRepositoryPort;
import com.hemodialyse.backend.domain.stock.port.StockEventPublisher;
import com.hemodialyse.backend.domain.stock.port.StockMovementRepositoryPort;
import com.hemodialyse.backend.domain.stock.port.StockSequencePort;
import com.hemodialyse.backend.domain.stock.service.BonSortieService;
import com.hemodialyse.backend.domain.stock.service.PmpEngine;
import com.hemodialyse.backend.domain.stock.service.PmpRecalculationCoordinator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Application Service — transactional boundary for the bon de sortie use cases.
 * <p>
 * The pure {@link BonSortieService} holds the FEFO business rules (no Spring/JPA,
 * hexagonal architecture — AGENTS.md §3). This facade owns the {@code @Transactional}
 * boundary, guaranteeing atomicity of the multi-write flow (lots + SORTIE movements
 * + PMP recalculation). It is the single Spring bean exposed for the
 * {@link BonSortieUseCase} port.
 */
@Service
@Transactional
public class BonSortieApplicationService implements BonSortieUseCase {

    private final BonSortieService delegate;

    public BonSortieApplicationService(BonSortieRepositoryPort repo,
                                       LotRepositoryPort lotRepo,
                                       StockMovementRepositoryPort movementRepo,
                                       ArticleRepositoryPort articleRepo,
                                       StockSequencePort sequence,
                                       PmpEngine pmpEngine,
                                       PmpRecalculationCoordinator recalcCoordinator,
                                       StockEventPublisher events) {
        this.delegate = new BonSortieService(repo, lotRepo, movementRepo, articleRepo,
                sequence, pmpEngine, recalcCoordinator, events);
    }

    @Override
    public BonSortie create(CenterId centerId, UUID seanceId, UUID patientId, String poste,
                            LocalDate dateSortie, List<SortieRequestItem> items, String userId) {
        return delegate.create(centerId, seanceId, patientId, poste, dateSortie, items, userId);
    }

    @Override
    @Transactional(readOnly = true)
    public BonSortie get(CenterId centerId, UUID bonId) {
        return delegate.get(centerId, bonId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<BonSortie> list(CenterId centerId) {
        return delegate.list(centerId);
    }
}

