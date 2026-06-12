package com.hemodialyse.backend.domain.stock.service;

import com.hemodialyse.backend.domain.article.model.Article;
import com.hemodialyse.backend.domain.article.port.ArticleRepositoryPort;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.domain.stock.model.*;
import com.hemodialyse.backend.domain.stock.port.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional
public class BonSortieService implements BonSortieUseCase {

    private static final UUID DEFAULT_SEANCE_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");

    private final BonSortieRepositoryPort repo;
    private final LotRepositoryPort lotRepo;
    private final StockMovementRepositoryPort movementRepo;
    private final ArticleRepositoryPort articleRepo;
    private final StockSequencePort sequence;
    private final PmpEngine pmpEngine;

    public BonSortieService(BonSortieRepositoryPort repo,
                            LotRepositoryPort lotRepo,
                            StockMovementRepositoryPort movementRepo,
                            ArticleRepositoryPort articleRepo,
                            StockSequencePort sequence,
                            PmpEngine pmpEngine) {
        this.repo = repo;
        this.lotRepo = lotRepo;
        this.movementRepo = movementRepo;
        this.articleRepo = articleRepo;
        this.sequence = sequence;
        this.pmpEngine = pmpEngine;
    }

    @Override
    public BonSortie create(CenterId centerId, UUID seanceId, UUID patientId, String poste,
                            LocalDate dateSortie, List<SortieRequestItem> items, String userId) {
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("Aucun article a sortir");
        }
        String by = userId != null ? userId : "system";
        UUID effectiveSeanceId = seanceId != null ? seanceId : DEFAULT_SEANCE_ID;
        String reference = sequence.next(centerId, "SEQ_BS");
        BonSortie bon = BonSortie.create(centerId.value(), reference, effectiveSeanceId, patientId, poste, dateSortie, by);

        Set<UUID> articlesTouches = new LinkedHashSet<>();

        for (SortieRequestItem item : items) {
            if (item.articleId() == null || item.lotId() == null || item.quantite() == null || item.quantite().signum() <= 0) {
                throw new IllegalArgumentException("Ligne de sortie invalide");
            }
            Article article = articleRepo.findById(item.articleId(), centerId)
                    .orElseThrow(() -> new IllegalArgumentException("Article introuvable: " + item.articleId()));

            Lot lot = lotRepo.findById(item.lotId(), centerId)
                    .orElseThrow(() -> new IllegalArgumentException("Lot introuvable: " + item.lotId()));
            if (!item.articleId().equals(lot.getArticleId())) {
                throw new IllegalArgumentException("Le lot choisi n'appartient pas a l'article " + article.getCode());
            }

            BigDecimal dispo = lot.getQuantiteRestante() != null ? lot.getQuantiteRestante() : BigDecimal.ZERO;
            if (dispo.compareTo(item.quantite()) < 0) {
                throw new IllegalStateException("Stock insuffisant sur le lot " + lot.getNumeroLot());
            }

            // Regle metier: une sortie est toujours valorisee au dernier PMP courant de l'article.
            BigDecimal pmpApplique = article.getPmpCourant() != null ? article.getPmpCourant() : BigDecimal.ZERO;
            lot.consommer(item.quantite());
            lotRepo.save(lot);

            bon.ajouterLigne(new LigneSortie(UUID.randomUUID(), item.articleId(), lot.getId(), item.quantite(), pmpApplique));

            movementRepo.save(StockMovement.sortieLot(centerId.value(), item.articleId(), effectiveSeanceId,
                    lot.getId(), item.quantite(), pmpApplique, by));

            articlesTouches.add(item.articleId());
        }

        BonSortie saved = repo.save(bon);

        for (UUID articleId : articlesTouches) {
            pmpEngine.recalculerArticle(centerId, articleId);
        }
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public BonSortie get(CenterId centerId, UUID bonId) {
        return repo.findById(bonId, centerId)
                .orElseThrow(() -> new IllegalArgumentException("Bon de sortie introuvable: " + bonId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<BonSortie> list(CenterId centerId) {
        return repo.findAll(centerId);
    }
}

