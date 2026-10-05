package com.hemodialyse.backend.domain.stock.service;

import com.hemodialyse.backend.domain.article.model.Article;
import com.hemodialyse.backend.domain.article.model.TypeTraitementAnemie;
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

import static org.junit.jupiter.api.Assertions.*;

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
        List<StockMovement> movements = movementRepo.findBySeanceAndArticle(centerId, seanceId, articleId);
        assertEquals(1, movements.size());
        assertEquals(LocalDate.of(2026, 8, 2), movements.get(0).getCreatedAt().toLocalDate());
    }

    @Test
    void the_first_article_creates_the_session_bon_and_the_next_ones_update_the_same_bon() {
        SeanceBonFixture f = new SeanceBonFixture();

        BonSortie first = f.add(f.articleA, "2");
        BonSortie second = f.add(f.articleB, "3");
        BonSortie third = f.add(f.articleA, "1");

        assertEquals(1, f.repo.findBySeance(f.seanceId, f.centerId).size(), "une seule sortie par séance");
        assertEquals(first.getId(), second.getId());
        assertEquals(first.getId(), third.getId());
        assertEquals("BS-1", third.getReference());
        assertEquals(1, f.sequenceCalls, "un seul numéro de pièce consommé");
        assertEquals(0, new BigDecimal("3").compareTo(f.quantiteLigne(third, f.articleA)));
        assertEquals(0, new BigDecimal("3").compareTo(f.quantiteLigne(third, f.articleB)));
        assertEquals(0, new BigDecimal("7").compareTo(f.lotA.getQuantiteRestante()));
        assertEquals(0, new BigDecimal("7").compareTo(f.lotB.getQuantiteRestante()));
    }

    @Test
    void movements_follow_the_bon_without_duplicates_when_the_same_article_is_added_again() {
        SeanceBonFixture f = new SeanceBonFixture();

        f.add(f.articleA, "1");
        f.add(f.articleA, "1");
        f.add(f.articleA, "1");

        BigDecimal sortie = f.movementRepo.findBySeanceAndArticle(f.centerId, f.seanceId, f.articleA).stream()
                .map(StockMovement::getQuantite).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, new BigDecimal("3").compareTo(sortie));
        assertEquals(1, f.movementRepo.findBySeanceAndArticle(f.centerId, f.seanceId, f.articleA).size(),
                "les mouvements sont reconstruits, pas empilés");
    }

    @Test
    void setting_a_quantity_replaces_it_on_the_same_bon_and_zero_removes_the_line() {
        SeanceBonFixture f = new SeanceBonFixture();
        f.add(f.articleA, "4");
        f.add(f.articleB, "2");

        BonSortie lowered = f.set(f.articleA, "1");
        assertEquals(0, BigDecimal.ONE.compareTo(f.quantiteLigne(lowered, f.articleA)));
        assertEquals(0, new BigDecimal("9").compareTo(f.lotA.getQuantiteRestante()), "le stock rendu est restitué");

        BonSortie removed = f.set(f.articleB, "0");
        assertEquals(lowered.getId(), removed.getId());
        assertEquals(0, BigDecimal.ZERO.compareTo(f.quantiteLigne(removed, f.articleB)));
        assertEquals(0, new BigDecimal("10").compareTo(f.lotB.getQuantiteRestante()));
        assertEquals(0, f.movementRepo.findBySeanceAndArticle(f.centerId, f.seanceId, f.articleB).size());
    }

    @Test
    void an_unchanged_quantity_leaves_everything_as_is() {
        SeanceBonFixture f = new SeanceBonFixture();
        BonSortie bon = f.add(f.articleA, "2");

        BonSortie same = f.set(f.articleA, "2");

        assertEquals(bon.getId(), same.getId());
        assertEquals(0, new BigDecimal("8").compareTo(f.lotA.getQuantiteRestante()));
    }

    @Test
    void insufficient_stock_is_refused_with_the_missing_quantity() {
        SeanceBonFixture f = new SeanceBonFixture();

        IllegalStateException e = assertThrows(IllegalStateException.class, () -> f.add(f.articleA, "11"));

        assertTrue(e.getMessage().contains("Stock insuffisant"));
    }

    @Test
    void a_billed_session_can_no_longer_change_its_bon() {
        SeanceBonFixture f = new SeanceBonFixture();
        f.add(f.articleA, "1");
        f.billing.markBilled(f.centerId, f.seanceId);

        assertThrows(IllegalStateException.class, () -> f.add(f.articleA, "1"));
        assertThrows(IllegalStateException.class, () -> f.set(f.articleA, "0"));
    }

    @Test
    void quantities_are_validated() {
        SeanceBonFixture f = new SeanceBonFixture();

        assertThrows(IllegalArgumentException.class, () -> f.add(f.articleA, "0"));
        assertThrows(IllegalArgumentException.class, () -> f.set(f.articleA, "-1"));
    }

    /**
     * Fixture d'une séance avec deux articles en stock (un lot chacun) et un service câblé sur des fakes mémoire.
     */
    private static final class SeanceBonFixture {
        final CenterId centerId = CenterId.of(UUID.randomUUID());
        final UUID seanceId = UUID.randomUUID();
        final UUID patientId = UUID.randomUUID();
        final UUID articleA = UUID.randomUUID();
        final UUID articleB = UUID.randomUUID();
        final InMemoryBonSortieRepo repo = new InMemoryBonSortieRepo();
        final InMemoryLotRepo lotRepo = new InMemoryLotRepo();
        final InMemoryMovementRepo movementRepo = new InMemoryMovementRepo();
        final InMemorySeanceBillingStatusPort billing = new InMemorySeanceBillingStatusPort();
        final Lot lotA = lot(articleA, "A", "10");
        final Lot lotB = lot(articleB, "B", "10");
        final BonSortieService service;
        int sequenceCalls = 0;

        SeanceBonFixture() {
            InMemoryArticleRepo articleRepo = new InMemoryArticleRepo();
            articleRepo.save(article(articleA, "ART-A"));
            articleRepo.save(article(articleB, "ART-B"));
            lotRepo.save(lotA);
            lotRepo.save(lotB);
            StockEventPublisher events = new NoOpStockEventPublisher();
            PmpEngine pmpEngine = new PmpEngine(movementRepo, articleRepo);
            PmpRecalculationCoordinator coordinator = new PmpRecalculationCoordinator(
                    pmpEngine, events, new ImmediateTransactionRunner());
            service = new BonSortieService(repo, lotRepo, movementRepo, articleRepo,
                    (c, key) -> "BS-" + (++sequenceCalls), pmpEngine, coordinator, events, billing);
        }

        private Lot lot(UUID articleId, String numero, String quantite) {
            Lot lot = new Lot();
            lot.setId(UUID.randomUUID());
            lot.setCenterId(centerId.value());
            lot.setArticleId(articleId);
            lot.setNumeroLot(numero);
            lot.setQuantiteRestante(new BigDecimal(quantite));
            return lot;
        }

        private Article article(UUID id, String code) {
            Article article = new Article();
            article.setId(id);
            article.setCenterId(centerId.value());
            article.setCode(code);
            article.setUnite("u");
            article.setPmpCourant(BigDecimal.TEN);
            article.setActive(true);
            return article;
        }

        BonSortie add(UUID articleId, String quantite) {
            return service.addSeanceConsommation(centerId, seanceId, patientId, LocalDate.of(2026, 10, 4),
                    articleId, new BigDecimal(quantite), "inf-01");
        }

        BonSortie set(UUID articleId, String quantite) {
            return service.setSeanceConsommation(centerId, seanceId, patientId, LocalDate.of(2026, 10, 4),
                    articleId, new BigDecimal(quantite), "inf-01");
        }

        BigDecimal quantiteLigne(BonSortie bon, UUID articleId) {
            return bon.getLignes().stream().filter(l -> l.articleId().equals(articleId))
                    .map(LigneSortie::quantite).reduce(BigDecimal.ZERO, BigDecimal::add);
        }
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

        @Override
        public List<Article> findAllByCenterAndTypeTraitementAnemie(CenterId centerId, TypeTraitementAnemie type) {
            return data.values().stream()
                    .filter(a -> centerId.value().equals(a.getCenterId()))
                    .filter(a -> a.getTypeTraitementAnemie() == type)
                    .toList();
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

