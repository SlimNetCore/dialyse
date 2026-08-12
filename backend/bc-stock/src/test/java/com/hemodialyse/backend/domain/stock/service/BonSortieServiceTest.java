package com.hemodialyse.backend.domain.stock.service;

import com.hemodialyse.backend.domain.article.model.Article;
import com.hemodialyse.backend.domain.article.port.ArticleRepositoryPort;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.domain.stock.model.BonSortie;
import com.hemodialyse.backend.domain.stock.model.LigneSortie;
import com.hemodialyse.backend.domain.stock.model.Lot;
import com.hemodialyse.backend.domain.stock.model.SortieRequestItem;
import com.hemodialyse.backend.domain.stock.model.StockMovement;
import com.hemodialyse.backend.domain.stock.port.BonSortieRepositoryPort;
import com.hemodialyse.backend.domain.stock.port.SeanceBillingStatusPort;
import com.hemodialyse.backend.domain.stock.port.StockEventPublisher;
import com.hemodialyse.backend.domain.stock.port.StockMovementRepositoryPort;
import com.hemodialyse.backend.domain.stock.port.StockSequencePort;
import com.hemodialyse.backend.domain.stock.port.LotRepositoryPort;
import com.hemodialyse.backend.domain.shared.port.TransactionRunner;
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
import static org.junit.jupiter.api.Assertions.assertThrows;

class BonSortieServiceTest {

    @Test
    void update_should_keep_date_when_linked_seance_and_no_new_date_provided() {
        CenterId centerId = CenterId.of(UUID.randomUUID());
        UUID bonId = UUID.randomUUID();
        UUID seanceId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        UUID articleId = UUID.randomUUID();
        UUID lotId = UUID.randomUUID();

        InMemoryBonSortieRepo repo = new InMemoryBonSortieRepo();
        InMemoryLotRepo lotRepo = new InMemoryLotRepo();
        InMemoryMovementRepo movementRepo = new InMemoryMovementRepo();
        InMemoryArticleRepo articleRepo = new InMemoryArticleRepo();
        StockSequencePort sequence = (c, key) -> "BS-0001";
        StockEventPublisher events = new NoOpStockEventPublisher();
        InMemorySeanceBillingStatusPort seanceBillingStatus = new InMemorySeanceBillingStatusPort();
        PmpEngine pmpEngine = new PmpEngine(movementRepo, articleRepo);
        PmpRecalculationCoordinator recalcCoordinator = new PmpRecalculationCoordinator(
                pmpEngine, events, new ImmediateTransactionRunner());

        BonSortieService service = new BonSortieService(
                repo, lotRepo, movementRepo, articleRepo, sequence, pmpEngine, recalcCoordinator, events, seanceBillingStatus
        );

        BonSortie existing = new BonSortie();
        existing.setId(bonId);
        existing.setCenterId(centerId.value());
        existing.setReference("BS-0001");
        existing.setSeanceId(seanceId);
        existing.setPatientId(patientId);
        existing.setDateSortie(LocalDate.of(2026, 8, 2));
        existing.ajouterLigne(new LigneSortie(UUID.randomUUID(), articleId, lotId, new BigDecimal("2"), new BigDecimal("10")));

        Lot lot = new Lot();
        lot.setId(lotId);
        lot.setCenterId(centerId.value());
        lot.setArticleId(articleId);
        lot.setNumeroLot("LOT-001");
        lot.setQuantiteRestante(new BigDecimal("8"));

        Article article = new Article();
        article.setId(articleId);
        article.setCenterId(centerId.value());
        article.setCode("ART-01");
        article.setUnite("u");
        article.setPmpCourant(new BigDecimal("11"));
        article.setActive(true);

        repo.save(existing);
        lotRepo.save(lot);
        articleRepo.save(article);
        movementRepo.save(StockMovement.sortieLot(centerId.value(), articleId, seanceId, lotId,
                new BigDecimal("2"), new BigDecimal("10"), "u1"));

        BonSortie updated = service.update(
                centerId,
                bonId,
                seanceId,
                patientId,
                "SEANCE",
                null,
                List.of(new SortieRequestItem(articleId, lotId, new BigDecimal("3"))),
                "inf-01"
        );

        assertEquals(LocalDate.of(2026, 8, 2), updated.getDateSortie());
        assertEquals(bonId, updated.getId());
        assertEquals(1, updated.getLignes().size());
        assertEquals(0, new BigDecimal("7").compareTo(lot.getQuantiteRestante()));
        assertEquals(1, movementRepo.findBySeanceAndArticle(centerId, seanceId, articleId).size());
    }

    @Test
    void update_should_throw_when_changing_date_of_linked_seance_exit() {
        CenterId centerId = CenterId.of(UUID.randomUUID());
        UUID bonId = UUID.randomUUID();
        UUID seanceId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        UUID articleId = UUID.randomUUID();
        UUID lotId = UUID.randomUUID();

        InMemoryBonSortieRepo repo = new InMemoryBonSortieRepo();
        InMemoryLotRepo lotRepo = new InMemoryLotRepo();
        InMemoryMovementRepo movementRepo = new InMemoryMovementRepo();
        InMemoryArticleRepo articleRepo = new InMemoryArticleRepo();
        StockSequencePort sequence = (c, key) -> "BS-000X";
        StockEventPublisher events = new NoOpStockEventPublisher();
        InMemorySeanceBillingStatusPort seanceBillingStatus = new InMemorySeanceBillingStatusPort();
        PmpEngine pmpEngine = new PmpEngine(movementRepo, articleRepo);
        PmpRecalculationCoordinator recalcCoordinator = new PmpRecalculationCoordinator(
                pmpEngine, events, new ImmediateTransactionRunner());

        BonSortieService service = new BonSortieService(
                repo, lotRepo, movementRepo, articleRepo, sequence, pmpEngine, recalcCoordinator, events, seanceBillingStatus
        );

        BonSortie existing = new BonSortie();
        existing.setId(bonId);
        existing.setCenterId(centerId.value());
        existing.setReference("BS-000X");
        existing.setSeanceId(seanceId);
        existing.setPatientId(patientId);
        existing.setDateSortie(LocalDate.of(2026, 8, 2));
        repo.save(existing);

        assertThrows(IllegalStateException.class, () -> service.update(
                centerId,
                bonId,
                seanceId,
                patientId,
                "SEANCE",
                LocalDate.of(2026, 8, 3),
                List.of(new SortieRequestItem(articleId, lotId, BigDecimal.ONE)),
                "inf-03"
        ));
    }

    @Test
    void update_should_throw_when_linked_seance_is_billed() {
        CenterId centerId = CenterId.of(UUID.randomUUID());
        UUID bonId = UUID.randomUUID();
        UUID seanceId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        UUID articleId = UUID.randomUUID();
        UUID lotId = UUID.randomUUID();

        InMemoryBonSortieRepo repo = new InMemoryBonSortieRepo();
        InMemoryLotRepo lotRepo = new InMemoryLotRepo();
        InMemoryMovementRepo movementRepo = new InMemoryMovementRepo();
        InMemoryArticleRepo articleRepo = new InMemoryArticleRepo();
        StockSequencePort sequence = (c, key) -> "BS-0002";
        StockEventPublisher events = new NoOpStockEventPublisher();
        InMemorySeanceBillingStatusPort seanceBillingStatus = new InMemorySeanceBillingStatusPort();
        seanceBillingStatus.markBilled(centerId, seanceId);
        PmpEngine pmpEngine = new PmpEngine(movementRepo, articleRepo);
        PmpRecalculationCoordinator recalcCoordinator = new PmpRecalculationCoordinator(
                pmpEngine, events, new ImmediateTransactionRunner());

        BonSortieService service = new BonSortieService(
                repo, lotRepo, movementRepo, articleRepo, sequence, pmpEngine, recalcCoordinator, events, seanceBillingStatus
        );

        BonSortie existing = new BonSortie();
        existing.setId(bonId);
        existing.setCenterId(centerId.value());
        existing.setReference("BS-0002");
        existing.setSeanceId(seanceId);
        existing.setPatientId(patientId);
        existing.setDateSortie(LocalDate.of(2026, 8, 2));
        repo.save(existing);

        assertThrows(IllegalStateException.class, () -> service.update(
                centerId,
                bonId,
                seanceId,
                patientId,
                "SEANCE",
                LocalDate.of(2026, 8, 3),
                List.of(new SortieRequestItem(articleId, lotId, BigDecimal.ONE)),
                "inf-02"
        ));
    }

    private static final class InMemoryBonSortieRepo implements BonSortieRepositoryPort {
        private final Map<UUID, BonSortie> data = new HashMap<>();

        @Override
        public BonSortie save(BonSortie bon) {
            data.put(bon.getId(), bon);
            return bon;
        }

        @Override
        public Optional<BonSortie> findById(UUID id, CenterId centerId) {
            BonSortie b = data.get(id);
            if (b == null || !centerId.value().equals(b.getCenterId())) {
                return Optional.empty();
            }
            return Optional.of(b);
        }

        @Override
        public List<BonSortie> findAll(CenterId centerId) {
            return data.values().stream().filter(b -> centerId.value().equals(b.getCenterId())).toList();
        }

        @Override
        public List<BonSortie> findBySeance(UUID seanceId, CenterId centerId) {
            return data.values().stream()
                    .filter(b -> centerId.value().equals(b.getCenterId()) && seanceId.equals(b.getSeanceId()))
                    .toList();
        }
    }

    private static final class InMemoryLotRepo implements LotRepositoryPort {
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
            return data.stream()
                    .filter(m -> centerId.value().equals(m.getCenterId()) && seanceId.equals(m.getSeanceId()) && articleId.equals(m.getArticleId()))
                    .toList();
        }

        @Override
        public void deleteBySeanceAndArticle(CenterId centerId, UUID seanceId, UUID articleId) {
            data.removeIf(m -> centerId.value().equals(m.getCenterId()) && seanceId.equals(m.getSeanceId()) && articleId.equals(m.getArticleId()));
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

    private static final class InMemorySeanceBillingStatusPort implements SeanceBillingStatusPort {
        private final Map<String, Boolean> billedFlags = new HashMap<>();

        @Override
        public boolean isBilled(CenterId centerId, UUID seanceId) {
            return billedFlags.getOrDefault(key(centerId, seanceId), false);
        }

        void markBilled(CenterId centerId, UUID seanceId) {
            billedFlags.put(key(centerId, seanceId), true);
        }

        private String key(CenterId centerId, UUID seanceId) {
            return centerId.value() + "::" + seanceId;
        }
    }

    private static final class ImmediateTransactionRunner implements TransactionRunner {
        @Override
        public void run(Runnable work) {
            work.run();
        }
    }
}

