package com.hemodialyse.backend.application.stock;

import com.hemodialyse.backend.domain.article.port.ArticleRepositoryPort;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.domain.stock.model.BonReception;
import com.hemodialyse.backend.domain.stock.model.LigneReception;
import com.hemodialyse.backend.domain.stock.port.BonCommandeRepositoryPort;
import com.hemodialyse.backend.domain.stock.port.BonReceptionRepositoryPort;
import com.hemodialyse.backend.domain.stock.port.BonReceptionUseCase;
import com.hemodialyse.backend.domain.stock.port.LotRepositoryPort;
import com.hemodialyse.backend.domain.stock.port.StockEventPublisher;
import com.hemodialyse.backend.domain.stock.port.StockMovementRepositoryPort;
import com.hemodialyse.backend.domain.stock.port.StockSequencePort;
import com.hemodialyse.backend.domain.stock.service.BonReceptionService;
import com.hemodialyse.backend.domain.stock.service.PmpEngine;
import com.hemodialyse.backend.domain.stock.service.PmpRecalculationCoordinator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Application Service — transactional boundary for the bon de réception use cases.
 * <p>
 * The pure {@link BonReceptionService} holds the business rules (no Spring/JPA,
 * hexagonal architecture — AGENTS.md §3). This facade owns the {@code @Transactional}
 * boundary, guaranteeing atomicity of the multi-write validation flow
 * (lots + ENTREE movements + PMP recalculation). It is the single Spring bean
 * exposed for the {@link BonReceptionUseCase} port.
 */
@Service
@Transactional
public class BonReceptionApplicationService implements BonReceptionUseCase {

    private final BonReceptionService delegate;

    public BonReceptionApplicationService(BonReceptionRepositoryPort repo,
                                          BonCommandeRepositoryPort bonCommandeRepo,
                                          LotRepositoryPort lotRepo,
                                          StockMovementRepositoryPort movementRepo,
                                          ArticleRepositoryPort articleRepo,
                                          StockSequencePort sequence,
                                          PmpEngine pmpEngine,
                                          PmpRecalculationCoordinator recalcCoordinator,
                                          StockEventPublisher events) {
        this.delegate = new BonReceptionService(repo, bonCommandeRepo, lotRepo, movementRepo,
                articleRepo, sequence, pmpEngine, recalcCoordinator, events);
    }

    @Override
    public BonReception create(CenterId centerId, UUID bonCommandeId, UUID fournisseurId,
                               LocalDate dateReception, List<LigneReception> lignes, String userId) {
        return delegate.create(centerId, bonCommandeId, fournisseurId, dateReception, lignes, userId);
    }

    @Override
    public BonReception update(CenterId centerId, UUID bonId, UUID fournisseurId,
                               LocalDate dateReception, List<LigneReception> lignes) {
        return delegate.update(centerId, bonId, fournisseurId, dateReception, lignes);
    }

    @Override
    public BonReception valider(CenterId centerId, UUID bonId, String userId) {
        return delegate.valider(centerId, bonId, userId);
    }

    @Override
    public BonReception fromBonCommande(CenterId centerId, UUID bonCommandeId, String userId) {
        return delegate.fromBonCommande(centerId, bonCommandeId, userId);
    }

    @Override
    @Transactional(readOnly = true)
    public BonReception get(CenterId centerId, UUID bonId) {
        return delegate.get(centerId, bonId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<BonReception> list(CenterId centerId) {
        return delegate.list(centerId);
    }
}

