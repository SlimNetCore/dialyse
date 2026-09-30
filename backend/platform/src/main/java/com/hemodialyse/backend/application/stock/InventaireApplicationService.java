package com.hemodialyse.backend.application.stock;

import com.hemodialyse.backend.domain.article.port.ArticleRepositoryPort;
import com.hemodialyse.backend.domain.shared.PagedResult;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.domain.stock.model.ComptageImporte;
import com.hemodialyse.backend.domain.stock.model.Inventaire;
import com.hemodialyse.backend.domain.stock.model.ResultatImportComptage;
import com.hemodialyse.backend.domain.stock.port.InventaireEventPublisher;
import com.hemodialyse.backend.domain.stock.port.InventaireRepositoryPort;
import com.hemodialyse.backend.domain.stock.port.InventaireUseCase;
import com.hemodialyse.backend.domain.stock.port.LotRepositoryPort;
import com.hemodialyse.backend.domain.stock.port.StockSequencePort;
import com.hemodialyse.backend.domain.stock.service.InventaireService;
import com.hemodialyse.backend.domain.stock.service.PmpEngine;
import com.hemodialyse.backend.domain.stock.service.PmpRecalculationCoordinator;
import com.hemodialyse.backend.infrastructure.persistence.adapter.StockMovementRepositoryAdapter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Application Service — frontière transactionnelle de l'inventaire : la clôture (lots, clôture des mouvements,
 * stock de départ, recalcul des articles) est tout-ou-rien.
 */
@Service
@Transactional
public class InventaireApplicationService implements InventaireUseCase {

    private final InventaireService delegate;

    public InventaireApplicationService(InventaireRepositoryPort repo, ArticleRepositoryPort articles, LotRepositoryPort lots,
                                        StockMovementRepositoryAdapter rawMovements, PmpEngine pmpEngine,
                                        PmpRecalculationCoordinator recalcCoordinator, StockSequencePort sequence,
                                        InventaireEventPublisher events) {
        this.delegate = new InventaireService(repo, articles, lots, rawMovements, pmpEngine, recalcCoordinator, sequence,
                events, Clock.systemDefaultZone());
    }

    @Override
    public Inventaire ouvrir(CenterId centerId, LocalDate dateInventaire, String commentaire, String user) {
        return delegate.ouvrir(centerId, dateInventaire, commentaire, user);
    }

    @Override
    @Transactional(readOnly = true)
    public Inventaire get(CenterId centerId, UUID inventaireId) {
        return delegate.get(centerId, inventaireId);
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResult<InventaireRepositoryPort.InventaireResume> list(CenterId centerId, int page, int size) {
        return delegate.list(centerId, page, size);
    }

    @Override
    @Transactional(readOnly = true)
    public EtatInventaire etat(CenterId centerId) {
        return delegate.etat(centerId);
    }

    @Override
    public Inventaire compter(CenterId centerId, UUID inventaireId, UUID ligneId, BigDecimal quantite, String motif, String user) {
        return delegate.compter(centerId, inventaireId, ligneId, quantite, motif, user);
    }

    @Override
    public Inventaire ajouterLigne(CenterId centerId, UUID inventaireId, UUID articleId, String numeroLot,
                                   LocalDate datePeremption, BigDecimal quantite, String motif, String user) {
        return delegate.ajouterLigne(centerId, inventaireId, articleId, numeroLot, datePeremption, quantite, motif, user);
    }

    @Override
    public Inventaire retirerLigne(CenterId centerId, UUID inventaireId, UUID ligneId) {
        return delegate.retirerLigne(centerId, inventaireId, ligneId);
    }

    @Override
    public ResultatImportComptage importerComptage(CenterId centerId, UUID inventaireId, List<ComptageImporte> lignes,
                                                   String user) {
        return delegate.importerComptage(centerId, inventaireId, lignes, user);
    }

    @Override
    public Inventaire reporterTheorique(CenterId centerId, UUID inventaireId, String user) {
        return delegate.reporterTheorique(centerId, inventaireId, user);
    }

    @Override
    public Inventaire cloturer(CenterId centerId, UUID inventaireId, String user) {
        return delegate.cloturer(centerId, inventaireId, user);
    }

    @Override
    public Inventaire annuler(CenterId centerId, UUID inventaireId, String user) {
        return delegate.annuler(centerId, inventaireId, user);
    }
}




