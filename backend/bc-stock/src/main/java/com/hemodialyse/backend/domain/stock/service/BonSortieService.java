package com.hemodialyse.backend.domain.stock.service;

import com.hemodialyse.backend.domain.article.model.Article;
import com.hemodialyse.backend.domain.article.port.ArticleRepositoryPort;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.domain.stock.exception.SeanceBilledStockModificationException;
import com.hemodialyse.backend.domain.stock.exception.SeanceStockExitDateImmutableException;
import com.hemodialyse.backend.domain.stock.model.*;
import com.hemodialyse.backend.domain.stock.port.*;
import com.hemodialyse.backend.domain.stock.port.SeanceBillingStatusPort;

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
    /**
     * Poste du bon de sortie unique qui regroupe les consommables d'une séance.
     */
    private static final String POSTE_SEANCE = "SEANCE";

    private final BonSortieRepositoryPort repo;
    private final LotRepositoryPort lotRepo;
    private final StockMovementRepositoryPort movementRepo;
    private final ArticleRepositoryPort articleRepo;
    private final StockSequencePort sequence;
    private final PmpEngine pmpEngine;
    private final PmpRecalculationCoordinator recalcCoordinator;
    private final StockEventPublisher events;
    private final SeanceBillingStatusPort seanceBillingStatusPort;

    public BonSortieService(BonSortieRepositoryPort repo,
                            LotRepositoryPort lotRepo,
                            StockMovementRepositoryPort movementRepo,
                            ArticleRepositoryPort articleRepo,
                            StockSequencePort sequence,
                            PmpEngine pmpEngine,
                            PmpRecalculationCoordinator recalcCoordinator,
                            StockEventPublisher events,
                            SeanceBillingStatusPort seanceBillingStatusPort) {
        this.repo = repo;
        this.lotRepo = lotRepo;
        this.movementRepo = movementRepo;
        this.articleRepo = articleRepo;
        this.sequence = sequence;
        this.pmpEngine = pmpEngine;
        this.recalcCoordinator = recalcCoordinator;
        this.events = events;
        this.seanceBillingStatusPort = seanceBillingStatusPort;
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
                    lot.getId(), item.quantite(), pmpApplique, by, bon.getDateSortie()));

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
        UUID effectiveSeanceId = seanceId != null ? seanceId : existing.getSeanceId();

        if (isLinkedToSeance(effectiveSeanceId) && seanceBillingStatusPort.isBilled(centerId, effectiveSeanceId)) {
            throw new SeanceBilledStockModificationException();
        }
        if (isLinkedToSeance(effectiveSeanceId)
                && dateSortie != null
                && existing.getDateSortie() != null
                && !dateSortie.equals(existing.getDateSortie())) {
            throw new SeanceStockExitDateImmutableException();
        }

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
        updated.setSeanceId(effectiveSeanceId);
        updated.setPatientId(patientId != null ? patientId : existing.getPatientId());
        updated.setPoste(poste != null ? poste : existing.getPoste());
        updated.setDateSortie(isLinkedToSeance(effectiveSeanceId) ? existing.getDateSortie() : (dateSortie != null ? dateSortie : existing.getDateSortie()));
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
                    lot.getId(), item.quantite(), pmpApplique, by, updated.getDateSortie()));
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
    public BonSortie addSeanceConsommation(CenterId centerId, UUID seanceId, UUID patientId, LocalDate dateSeance,
                                           UUID articleId, BigDecimal quantite, String userId) {
        if (quantite == null || quantite.signum() <= 0) {
            throw new IllegalArgumentException("La quantite doit etre strictement positive");
        }
        return applySeanceQuantity(centerId, seanceId, patientId, dateSeance, articleId, quantite, true, userId);
    }

    @Override
    public BonSortie setSeanceConsommation(CenterId centerId, UUID seanceId, UUID patientId, LocalDate dateSeance,
                                           UUID articleId, BigDecimal quantite, String userId) {
        if (quantite == null || quantite.signum() < 0) {
            throw new IllegalArgumentException("La quantite ne peut pas etre negative");
        }
        return applySeanceQuantity(centerId, seanceId, patientId, dateSeance, articleId, quantite, false, userId);
    }

    /**
     * Une séance a un seul bon de sortie « SEANCE » : créé à la première ligne, puis mis à jour (lignes, lots et
     * mouvements recalculés par FEFO) à chaque ajout, modification ou retrait — jamais un bon par article. Les lots de
     * l'ancien contenu sont d'abord restitués pour que la nouvelle sélection FEFO voie le stock réel.
     */
    private BonSortie applySeanceQuantity(CenterId centerId, UUID seanceId, UUID patientId, LocalDate dateSeance,
                                          UUID articleId, BigDecimal quantite, boolean increment, String userId) {
        if (!isLinkedToSeance(seanceId)) {
            throw new IllegalArgumentException("Une seance est requise pour ce bon de sortie");
        }
        if (seanceBillingStatusPort.isBilled(centerId, seanceId)) {
            throw new SeanceBilledStockModificationException();
        }
        String by = userId != null ? userId : "system";
        BonSortie existing = repo.findBySeance(seanceId, centerId).stream()
                .filter(b -> POSTE_SEANCE.equals(b.getPoste()))
                .findFirst().orElse(null);

        Map<UUID, BigDecimal> wanted = new LinkedHashMap<>();
        if (existing != null) {
            for (LigneSortie line : existing.getLignes()) {
                wanted.merge(line.articleId(), line.quantite(), BigDecimal::add);
            }
        }
        BigDecimal current = wanted.getOrDefault(articleId, BigDecimal.ZERO);
        BigDecimal target = increment ? current.add(quantite) : quantite;
        if (target.compareTo(current) == 0) {
            return existing;
        }
        if (target.compareTo(current) > 0) {
            Article article = articleRepo.findById(articleId, centerId)
                    .orElseThrow(() -> new IllegalArgumentException("Article introuvable: " + articleId));
            if (!article.isActive()) {
                throw new IllegalStateException("Article inactif: " + article.getCode());
            }
        }
        if (target.signum() == 0) {
            wanted.remove(articleId);
        } else {
            wanted.put(articleId, target);
        }

        Set<UUID> touched = new LinkedHashSet<>();
        touched.add(articleId);
        BonSortie bon;
        if (existing != null) {
            for (LigneSortie line : existing.getLignes()) {
                touched.add(line.articleId());
                if (line.lotId() != null) {
                    lotRepo.findById(line.lotId(), centerId).ifPresent(lot -> {
                        lot.restituer(line.quantite());
                        lotRepo.save(lot);
                    });
                }
            }
            for (UUID touchedArticle : touched) {
                movementRepo.deleteBySeanceAndArticle(centerId, seanceId, touchedArticle);
            }
            bon = new BonSortie();
            bon.setId(existing.getId());
            bon.setCenterId(existing.getCenterId());
            bon.setReference(existing.getReference());
            bon.setSeanceId(existing.getSeanceId());
            bon.setPatientId(existing.getPatientId());
            bon.setPoste(existing.getPoste());
            bon.setDateSortie(existing.getDateSortie());
            bon.setCreatedBy(existing.getCreatedBy());
            bon.setCreatedAt(existing.getCreatedAt());
        } else {
            bon = BonSortie.create(centerId.value(), sequence.next(centerId, "SEQ_BS"), seanceId, patientId,
                    POSTE_SEANCE, dateSeance, by);
        }

        for (Map.Entry<UUID, BigDecimal> line : wanted.entrySet()) {
            allocateFefo(centerId, bon, line.getKey(), line.getValue(), by);
        }

        BonSortie saved = repo.save(bon);
        for (UUID touchedArticle : touched) {
            pmpEngine.recalculerArticle(centerId, touchedArticle);
        }
        publishStockMovementChanged(centerId.value(), existing == null ? "SORTIE" : "CORRECTION",
                saved.getReference(), touched.size());
        return saved;
    }

    /**
     * Prélève la quantité sur les lots par FEFO et ajoute les lignes et mouvements correspondants au bon.
     */
    private void allocateFefo(CenterId centerId, BonSortie bon, UUID articleId, BigDecimal quantite, String by) {
        Article article = articleRepo.findById(articleId, centerId)
                .orElseThrow(() -> new IllegalArgumentException("Article introuvable: " + articleId));
        if (recalcCoordinator.isLocked(centerId, articleId)) {
            throw new IllegalStateException("Recalcul en cours pour l'article " + article.getCode());
        }
        BigDecimal pmpApplique = article.getPmpCourant() != null ? article.getPmpCourant() : BigDecimal.ZERO;
        BigDecimal remaining = quantite;
        for (Lot lot : lotRepo.findAvailableByArticleFefo(articleId, centerId)) {
            if (remaining.signum() <= 0) break;
            BigDecimal dispo = lot.getQuantiteRestante() != null ? lot.getQuantiteRestante() : BigDecimal.ZERO;
            if (dispo.signum() <= 0) continue;
            BigDecimal take = remaining.min(dispo);
            lot.consommer(take);
            lotRepo.save(lot);
            bon.ajouterLigne(new LigneSortie(UUID.randomUUID(), articleId, lot.getId(), take, pmpApplique));
            movementRepo.save(StockMovement.sortieLot(centerId.value(), articleId, bon.getSeanceId(), lot.getId(),
                    take, pmpApplique, by, bon.getDateSortie()));
            remaining = remaining.subtract(take);
        }
        if (remaining.signum() > 0) {
            throw new IllegalStateException(
                    "Stock insuffisant pour l'article " + article.getCode()
                            + " (manque: " + remaining + " " + article.getUnite() + ")");
        }
    }

    @Override
    public BonSortie createViaFefo(CenterId centerId, UUID seanceId, UUID patientId, String poste,
                                   LocalDate dateSortie, UUID articleId, BigDecimal quantite, String userId) {
        if (isLinkedToSeance(seanceId) && seanceBillingStatusPort.isBilled(centerId, seanceId)) {
            throw new SeanceBilledStockModificationException();
        }
        Article article = articleRepo.findById(articleId, centerId)
                .orElseThrow(() -> new IllegalArgumentException("Article introuvable: " + articleId));
        if (!article.isActive()) {
            throw new IllegalStateException("Article inactif: " + article.getCode());
        }
        if (recalcCoordinator.isLocked(centerId, articleId)) {
            throw new IllegalStateException("Recalcul en cours pour l'article " + article.getCode());
        }

        List<Lot> fefoLots = lotRepo.findAvailableByArticleFefo(articleId, centerId);
        BigDecimal remaining = quantite;
        List<SortieRequestItem> items = new ArrayList<>();
        for (Lot lot : fefoLots) {
            if (remaining.signum() <= 0) break;
            BigDecimal dispo = lot.getQuantiteRestante() != null ? lot.getQuantiteRestante() : BigDecimal.ZERO;
            if (dispo.signum() <= 0) continue;
            BigDecimal take = remaining.min(dispo);
            items.add(new SortieRequestItem(articleId, lot.getId(), take));
            remaining = remaining.subtract(take);
        }
        if (remaining.signum() > 0) {
            throw new IllegalStateException(
                    "Stock insuffisant pour l'article " + article.getCode()
                            + " (manque: " + remaining + " " + article.getUnite() + ")");
        }

        return create(centerId, seanceId, patientId, poste, dateSortie, items, userId);
    }

    private void publishStockMovementChanged(UUID centerId, String mouvement, String reference, int articleCount) {
        events.stockMovementChanged(centerId, mouvement, reference, articleCount);
    }

    private boolean isLinkedToSeance(UUID seanceId) {
        return seanceId != null && !DEFAULT_SEANCE_ID.equals(seanceId);
    }
}

