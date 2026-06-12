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
        if (seanceId == null) {
            throw new IllegalArgumentException("Le lien vers la seance d'hemodialyse est obligatoire");
        }
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("Aucun article a sortir");
        }
        String by = userId != null ? userId : "system";
        String reference = sequence.next(centerId, "SEQ_BS");
        BonSortie bon = BonSortie.create(centerId.value(), reference, seanceId, patientId, poste, dateSortie, by);

        Set<UUID> articlesTouches = new LinkedHashSet<>();

        for (SortieRequestItem item : items) {
            if (item.articleId() == null || item.quantite() == null || item.quantite().signum() <= 0) {
                throw new IllegalArgumentException("Ligne de sortie invalide");
            }
            Article article = articleRepo.findById(item.articleId(), centerId)
                    .orElseThrow(() -> new IllegalArgumentException("Article introuvable: " + item.articleId()));
            BigDecimal pmpCourant = article.getPmpCourant() != null ? article.getPmpCourant() : BigDecimal.ZERO;

            BigDecimal restant = item.quantite();
            List<Lot> lotsFefo = lotRepo.findAvailableByArticleFefo(item.articleId(), centerId);

            for (Lot lot : lotsFefo) {
                if (restant.signum() <= 0) {
                    break;
                }
                BigDecimal dispo = lot.getQuantiteRestante() != null ? lot.getQuantiteRestante() : BigDecimal.ZERO;
                if (dispo.signum() <= 0) {
                    continue;
                }
                BigDecimal aPrelever = dispo.min(restant);

                lot.consommer(aPrelever);
                lotRepo.save(lot);

                bon.ajouterLigne(new LigneSortie(UUID.randomUUID(), item.articleId(), lot.getId(), aPrelever, pmpCourant));

                movementRepo.save(StockMovement.sortieLot(centerId.value(), item.articleId(), seanceId,
                        lot.getId(), aPrelever, pmpCourant, by));

                restant = restant.subtract(aPrelever);
            }

            if (restant.signum() > 0) {
                throw new IllegalStateException("Stock insuffisant (FEFO) pour l'article " + article.getCode());
            }
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

