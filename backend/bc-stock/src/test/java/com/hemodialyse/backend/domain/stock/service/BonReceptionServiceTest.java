package com.hemodialyse.backend.domain.stock.service;

import com.hemodialyse.backend.domain.article.model.Article;
import com.hemodialyse.backend.domain.article.port.ArticleRepositoryPort;
import com.hemodialyse.backend.domain.shared.port.TransactionRunner;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.domain.stock.model.BonCommande;
import com.hemodialyse.backend.domain.stock.model.BonReception;
import com.hemodialyse.backend.domain.stock.model.BonStatut;
import com.hemodialyse.backend.domain.stock.model.LigneReception;
import com.hemodialyse.backend.domain.stock.model.Lot;
import com.hemodialyse.backend.domain.stock.model.StockMovement;
import com.hemodialyse.backend.domain.stock.port.BonCommandeRepositoryPort;
import com.hemodialyse.backend.domain.stock.port.BonReceptionRepositoryPort;
import com.hemodialyse.backend.domain.stock.port.StockEventPublisher;
import com.hemodialyse.backend.domain.stock.port.StockMovementRepositoryPort;
import com.hemodialyse.backend.domain.stock.port.StockSequencePort;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BonReceptionServiceTest {

    @Test
    void valider_should_use_bon_reception_date_for_movement_creation() {
        CenterId centerId = CenterId.of(UUID.randomUUID());
        UUID bonReceptionId = UUID.randomUUID();
        UUID articleId = UUID.randomUUID();
        UUID lotId = UUID.randomUUID();

        InMemoryBonReceptionRepo repo = new InMemoryBonReceptionRepo();
        InMemoryLotRepo lotRepo = new InMemoryLotRepo();
        InMemoryMovementRepo movementRepo = new InMemoryMovementRepo();
        InMemoryArticleRepo articleRepo = new InMemoryArticleRepo();
        StockSequencePort sequence = (c, key) -> "BR-0001";
        StockEventPublisher events = new NoOpStockEventPublisher();
        PmpEngine pmpEngine = new PmpEngine(movementRepo, articleRepo);
        PmpRecalculationCoordinator recalcCoordinator = new PmpRecalculationCoordinator(
                pmpEngine, events, new ImmediateTransactionRunner());

        BonReceptionService service = new BonReceptionService(
                repo, new NoOpBonCommandeRepo(), lotRepo, movementRepo, articleRepo, sequence,
                pmpEngine, recalcCoordinator, events);

        BonReception bon = new BonReception();
        bon.setId(bonReceptionId);
        bon.setCenterId(centerId.value());
        bon.setReference("BR-0001");
        bon.setStatut(BonStatut.BROUILLON);
        bon.setDateReception(LocalDate.of(2026, 8, 2));
        bon.remplacerLignes(List.of(new LigneReception(UUID.randomUUID(), articleId, new BigDecimal("5"),
                new BigDecimal("12"), "LOT-001", LocalDate.of(2027, 8, 2), null, null)));
        repo.save(bon);

        Article article = new Article();
        article.setId(articleId);
        article.setCenterId(centerId.value());
        article.setCode("ART-01");
        article.setUnite("u");
        article.setActive(true);
        articleRepo.save(article);

        BonReception saved = service.valider(centerId, bonReceptionId, "user-01");

        assertEquals(bonReceptionId, saved.getId());
        List<StockMovement> movements = movementRepo.findByArticleOrdered(centerId, articleId);
        assertEquals(1, movements.size());
        assertEquals(LocalDate.of(2026, 8, 2), movements.get(0).getCreatedAt().toLocalDate());
    }

    private static final class InMemoryBonReceptionRepo implements BonReceptionRepositoryPort {
        private final Map<UUID, BonReception> data = new HashMap<>();

        @Override
        public BonReception save(BonReception bon) {
            data.put(bon.getId(), bon);
            return bon;
        }

        @Override
        public Optional<BonReception> findById(UUID id, CenterId centerId) {
            BonReception bon = data.get(id);
            if (bon == null || !centerId.value().equals(bon.getCenterId())) {
                return Optional.empty();
            }
            return Optional.of(bon);
        }

        @Override
        public List<BonReception> findAll(CenterId centerId) {
            return data.values().stream().filter(b -> centerId.value().equals(b.getCenterId())).toList();
        }
    }

    private static final class NoOpBonCommandeRepo implements BonCommandeRepositoryPort {
        @Override
        public BonCommande save(BonCommande bon) {
            return bon;
        }

        @Override
        public Optional<BonCommande> findById(UUID id, CenterId centerId) {
            return Optional.empty();
        }

        @Override
        public List<BonCommande> findAll(CenterId centerId) {
            return List.of();
        }
    }

    private static final class InMemoryLotRepo implements com.hemodialyse.backend.domain.stock.port.LotRepositoryPort {
        private final Map<UUID, Lot> data = new HashMap<>();

        @Override
        public Lot save(Lot lot) {
            data.put(lot.getId(), lot);
            return lot;
        }

        @Override
        public Optional<Lot> findById(UUID id, CenterId centerId) {
            Lot lot = data.get(id);
            if (lot == null || !centerId.value().equals(lot.getCenterId())) {
                return Optional.empty();
            }
            return Optional.of(lot);
        }

        @Override
        public List<Lot> findAvailableByArticleFefo(UUID articleId, CenterId centerId) {
            return data.values().stream()
                    .filter(l -> centerId.value().equals(l.getCenterId()) && articleId.equals(l.getArticleId()))
                    .sorted(Comparator.comparing(Lot::getDatePeremption, Comparator.nullsLast(Comparator.naturalOrder())))
                    .toList();
        }

        @Override
        public List<Lot> findExpiringBefore(CenterId centerId, LocalDate threshold) {
            return List.of();
        }

        @Override
        public List<Lot> findByArticle(UUID articleId, CenterId centerId) {
            return findAvailableByArticleFefo(articleId, centerId);
        }

        @Override
        public List<Lot> findByBonReception(UUID bonReceptionId, CenterId centerId) {
            return List.of();
        }
    }

    private static final class InMemoryMovementRepo implements StockMovementRepositoryPort {
        private final List<StockMovement> data = new ArrayList<>();

        @Override
        public StockMovement save(StockMovement movement) {
            data.add(movement);
            return movement;
        }

        @Override
        public List<StockMovement> findByArticleOrdered(CenterId centerId, UUID articleId) {
            return data.stream()
                    .filter(m -> centerId.value().equals(m.getCenterId()) && articleId.equals(m.getArticleId()))
                    .sorted(Comparator.comparing(StockMovement::getCreatedAt).thenComparing(StockMovement::getId))
                    .toList();
        }

        @Override
        public List<StockMovement> findByArticleBefore(CenterId centerId, UUID articleId, OffsetDateTime before) {
            return findByArticleOrdered(centerId, articleId).stream()
                    .filter(m -> m.getCreatedAt().isBefore(before))
                    .toList();
        }

        @Override
        public void updatePmpApres(UUID movementId, BigDecimal pmpApres) {
        }

        @Override
        public Optional<StockMovement> findFirstEntreeByLot(CenterId centerId, UUID lotId) {
            return Optional.empty();
        }

        @Override
        public List<StockMovement> findBySeanceAndArticle(CenterId centerId, UUID seanceId, UUID articleId) {
            return List.of();
        }

        @Override
        public void deleteBySeanceAndArticle(CenterId centerId, UUID seanceId, UUID articleId) {
        }

        @Override
        public void applyRecalc(List<MovementRecalc> updates) {
        }
    }

    private static final class InMemoryArticleRepo implements ArticleRepositoryPort {
        private final Map<UUID, Article> data = new HashMap<>();

        @Override
        public Optional<Article> findById(UUID articleId, CenterId centerId) {
            Article article = data.get(articleId);
            if (article == null || !centerId.value().equals(article.getCenterId())) {
                return Optional.empty();
            }
            return Optional.of(article);
        }

        @Override
        public Article save(Article article) {
            data.put(article.getId(), article);
            return article;
        }

        @Override
        public List<Article> findAllByCenter(CenterId centerId) {
            return data.values().stream().filter(a -> centerId.value().equals(a.getCenterId())).toList();
        }
    }

    private static final class NoOpStockEventPublisher implements StockEventPublisher {
        @Override
        public void stockMovementChanged(UUID centerId, String mouvement, String reference, int articleCount) {
        }

        @Override
        public void recalcLocksChanged(UUID centerId, List<UUID> articleIds, String status) {
        }
    }

    private static final class ImmediateTransactionRunner implements TransactionRunner {
        @Override
        public void run(Runnable work) {
            work.run();
        }
    }
}

