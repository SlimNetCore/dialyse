package com.hemodialyse.backend.domain.stock.service;

import com.hemodialyse.backend.domain.article.model.Article;
import com.hemodialyse.backend.domain.article.port.ArticleRepositoryPort;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.domain.stock.model.*;
import com.hemodialyse.backend.domain.stock.port.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/**
 * Domain Service — bon de sortie (BS) using FEFO, linked to a hemodialysis seance.
 * <p>
 * Pure domain class (no Spring/JPA dependency — hexagonal architecture, AGENTS.md §3).
 * The transactional boundary is provided by
 * {@code application.stock.BonSortieApplicationService}, guaranteeing atomicity of
 * the multi-write flow (lots + movements + PMP recalculation).
 */
public class BonSortieService implements BonSortieUseCase {

    private static final UUID DEFAULT_SEANCE_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");

    private final BonSortieRepositoryPort repo;
    private final LotRepositoryPort lotRepo;
    private final StockMovementRepositoryPort movementRepo;
    private final ArticleRepositoryPort articleRepo;
    private final StockSequencePort sequence;
    private final PmpEngine pmpEngine;
    private final PmpRecalculationCoordinator recalcCoordinator;
    private final StockEventPublisher events;

    public BonSortieService(BonSortieRepositoryPort repo,
                            LotRepositoryPort lotRepo,
                            StockMovementRepositoryPort movementRepo,
                            ArticleRepositoryPort articleRepo,
                            StockSequencePort sequence,
                            PmpEngine pmpEngine,
                            PmpRecalculationCoordinator recalcCoordinator,
                            StockEventPublisher events) {
        this.repo = repo;
        this.lotRepo = lotRepo;
        this.movementRepo = movementRepo;
        this.articleRepo = articleRepo;
        this.sequence = sequence;
        this.pmpEngine = pmpEngine;
        this.recalcCoordinator = recalcCoordinator;
        this.events = events;
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

            if (recalcCoordinator.isLocked(centerId, item.articleId())) {
                throw new IllegalStateException("Recalcul en cours pour l'article " + article.getCode() + ". Saisie temporairement bloquee.");
            }

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
        publishStockMovementChanged(centerId.value(), "SORTIE", saved.getReference(), articlesTouches.size());
        return saved;
    }

    @Override
    public BonSortie get(CenterId centerId, UUID bonId) {
        return repo.findById(bonId, centerId)
                .orElseThrow(() -> new IllegalArgumentException("Bon de sortie introuvable: " + bonId));
    }

    @Override
    public List<BonSortie> list(CenterId centerId) {
        return repo.findAll(centerId);
    }

    @Override
    public BonSortie update(CenterId centerId, UUID bonId, UUID seanceId, UUID patientId,
                            String poste, LocalDate dateSortie, List<SortieRequestItem> items, String userId) {
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("Aucune ligne de sortie");
        }
        BonSortie existing = repo.findById(bonId, centerId)
                .orElseThrow(() -> new IllegalArgumentException("Bon de sortie introuvable: " + bonId));
        String by = userId != null ? userId : "system";

        // Restore previous lot quantities and remove corresponding stock movements.
        Set<UUID> articlesTouches = new LinkedHashSet<>();
        for (LigneSortie line : existing.getLignes()) {
            if (line.lotId() == null) {
                continue;
            }
            Lot lot = lotRepo.findById(line.lotId(), centerId)
                    .orElseThrow(() -> new IllegalArgumentException("Lot introuvable: " + line.lotId()));
            lot.restituer(line.quantite());
            lotRepo.save(lot);
            movementRepo.deleteBySeanceAndArticle(centerId, existing.getSeanceId(), line.articleId());
            articlesTouches.add(line.articleId());
        }

        BonSortie updated = new BonSortie();
        updated.setId(existing.getId());
        updated.setCenterId(existing.getCenterId());
        updated.setReference(existing.getReference());
        updated.setSeanceId(seanceId != null ? seanceId : existing.getSeanceId());
        updated.setPatientId(patientId != null ? patientId : existing.getPatientId());
        updated.setPoste(poste != null ? poste : existing.getPoste());
        updated.setDateSortie(dateSortie != null ? dateSortie : existing.getDateSortie());
        updated.setCreatedBy(existing.getCreatedBy() != null ? existing.getCreatedBy() : by);
        updated.setCreatedAt(existing.getCreatedAt());

        for (SortieRequestItem item : items) {
            if (item.articleId() == null || item.lotId() == null || item.quantite() == null || item.quantite().signum() <= 0) {
                throw new IllegalArgumentException("Ligne de sortie invalide");
            }
            Article article = articleRepo.findById(item.articleId(), centerId)
                    .orElseThrow(() -> new IllegalArgumentException("Article introuvable: " + item.articleId()));

            if (recalcCoordinator.isLocked(centerId, item.articleId())) {
                throw new IllegalStateException("Recalcul en cours pour l'article " + article.getCode() + ". Saisie temporairement bloquee.");
            }

            Lot lot = lotRepo.findById(item.lotId(), centerId)
                    .orElseThrow(() -> new IllegalArgumentException("Lot introuvable: " + item.lotId()));
            if (!item.articleId().equals(lot.getArticleId())) {
                throw new IllegalArgumentException("Le lot choisi n'appartient pas a l'article " + article.getCode());
            }

            BigDecimal dispo = lot.getQuantiteRestante() != null ? lot.getQuantiteRestante() : BigDecimal.ZERO;
            if (dispo.compareTo(item.quantite()) < 0) {
                throw new IllegalStateException("Stock insuffisant sur le lot " + lot.getNumeroLot());
            }

            BigDecimal pmpApplique = article.getPmpCourant() != null ? article.getPmpCourant() : BigDecimal.ZERO;
            lot.consommer(item.quantite());
            lotRepo.save(lot);
            updated.ajouterLigne(new LigneSortie(UUID.randomUUID(), item.articleId(), lot.getId(), item.quantite(), pmpApplique));
            movementRepo.save(StockMovement.sortieLot(centerId.value(), item.articleId(), updated.getSeanceId(),
                    lot.getId(), item.quantite(), pmpApplique, by));
            articlesTouches.add(item.articleId());
        }

        BonSortie saved = repo.save(updated);
        for (UUID articleId : articlesTouches) {
            pmpEngine.recalculerArticle(centerId, articleId);
        }
        publishStockMovementChanged(centerId.value(), "CORRECTION", saved.getReference(), articlesTouches.size());
        return saved;
    }

    @Override
    public void reverseArticleConsommation(CenterId centerId, UUID seanceId, UUID articleId, String userId) {
        // Find all SORTIE movements for this article+seance
        List<StockMovement> movements = movementRepo.findBySeanceAndArticle(centerId, seanceId, articleId);
        if (movements.isEmpty()) {
            return; // Nothing to reverse
        }
        // Restore each lot's quantity
        for (StockMovement m : movements) {
            if (m.getLotId() != null) {
                lotRepo.findById(m.getLotId(), centerId).ifPresent(lot -> {
                    lot.restituer(m.getQuantite());
                    lotRepo.save(lot);
                });
            }
        }
        // Delete the movements
        movementRepo.deleteBySeanceAndArticle(centerId, seanceId, articleId);
        // Trigger PMP recalculation to restore coherency
        pmpEngine.recalculerArticle(centerId, articleId);
        publishStockMovementChanged(centerId.value(), "CORRECTION", "SEANCE-CORR", 1);
    }

    @Override
    public void addArticleConsommation(CenterId centerId, UUID seanceId, UUID patientId,
                                       LocalDate dateSeance, UUID articleId, BigDecimal quantite, String userId) {
        String by = userId != null ? userId : "system";
        Article article = articleRepo.findById(articleId, centerId)
                .orElseThrow(() -> new IllegalArgumentException("Article introuvable: " + articleId));
        if (!article.isActive()) {
            throw new IllegalStateException("Article inactif: " + article.getCode());
        }
        if (recalcCoordinator.isLocked(centerId, articleId)) {
            throw new IllegalStateException("Recalcul en cours pour l'article " + article.getCode());
        }

        // FEFO selection
        List<Lot> fefoLots = lotRepo.findAvailableByArticleFefo(articleId, centerId);
        BigDecimal remaining = quantite;
        List<StockMovement> newMovements = new java.util.ArrayList<>();

        for (Lot lot : fefoLots) {
            if (remaining.signum() <= 0) break;
            BigDecimal dispo = lot.getQuantiteRestante() != null ? lot.getQuantiteRestante() : BigDecimal.ZERO;
            if (dispo.signum() <= 0) continue;
            BigDecimal take = remaining.min(dispo);
            lot.consommer(take);
            lotRepo.save(lot);
            BigDecimal pmpApplique = article.getPmpCourant() != null ? article.getPmpCourant() : BigDecimal.ZERO;
            StockMovement m = StockMovement.sortieLot(centerId.value(), articleId, seanceId, lot.getId(), take, pmpApplique, by);
            newMovements.add(m);
            remaining = remaining.subtract(take);
        }

        if (remaining.signum() > 0) {
            throw new IllegalStateException(
                    "Stock insuffisant pour l'article " + article.getCode()
                            + " (manque: " + remaining + " " + article.getUnite() + ")");
        }

        for (StockMovement m : newMovements) {
            movementRepo.save(m);
        }
        pmpEngine.recalculerArticle(centerId, articleId);
        publishStockMovementChanged(centerId.value(), "SORTIE", "SEANCE-UPDATE", 1);
    }

    private void publishStockMovementChanged(UUID centerId, String mouvement, String reference, int articleCount) {
        events.stockMovementChanged(centerId, mouvement, reference, articleCount);
    }
}

