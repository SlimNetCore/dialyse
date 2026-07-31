package com.hemodialyse.backend.domain.seance.service;

import com.hemodialyse.backend.domain.article.model.Article;
import com.hemodialyse.backend.domain.article.port.ArticleRepositoryPort;
import com.hemodialyse.backend.domain.patient.model.Patient;
import com.hemodialyse.backend.domain.patient.port.PatientRepositoryPort;
import com.hemodialyse.backend.domain.patient.vo.PatientId;
import com.hemodialyse.backend.domain.seance.model.Seance;
import com.hemodialyse.backend.domain.seance.model.SeanceArticleConsumption;
import com.hemodialyse.backend.domain.seance.model.SeanceListItem;
import com.hemodialyse.backend.domain.seance.model.SeanceStatus;
import com.hemodialyse.backend.domain.seance.port.SeanceRepositoryPort;
import com.hemodialyse.backend.domain.seance.port.VoletMedicalRepositoryPort;
import com.hemodialyse.backend.domain.seance.port.VoletParamedicalRepositoryPort;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.domain.stock.model.BonSortie;
import com.hemodialyse.backend.domain.stock.model.Lot;
import com.hemodialyse.backend.domain.stock.model.SortieRequestItem;
import com.hemodialyse.backend.domain.stock.port.BonSortieUseCase;
import com.hemodialyse.backend.domain.stock.port.LotRepositoryPort;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class SeanceDomainServiceTest {

    private static SeanceDomainService buildService(
            SeanceRepositoryPort seanceRepo,
            PatientRepositoryPort patientRepo,
            ArticleRepositoryPort articleRepo,
            LotRepositoryPort lotRepo,
            BonSortieUseCase bonSortieUseCase) {
        return new SeanceDomainService(seanceRepo, patientRepo, articleRepo, lotRepo, bonSortieUseCase,
                mock(VoletParamedicalRepositoryPort.class), mock(VoletMedicalRepositoryPort.class));
    }

    @Test
    void validate_without_consommables_should_succeed_without_stock_movement() {
        CenterId centerId = CenterId.of(UUID.randomUUID());
        UUID patientId = UUID.randomUUID();
        UUID seanceId = UUID.randomUUID();
        InMemorySeanceRepository seanceRepo = new InMemorySeanceRepository();
        InMemoryPatientRepository patientRepo = new InMemoryPatientRepository(patientId, centerId);
        InMemoryArticleRepository articleRepo = new InMemoryArticleRepository();
        InMemoryLotRepository lotRepo = new InMemoryLotRepository();
        SpyBonSortieUseCase spyBonSortie = new SpyBonSortieUseCase();
        Seance seance = new Seance(seanceId, patientId, centerId.value(), LocalDate.now());
        seanceRepo.save(seance);

        SeanceDomainService service = buildService(seanceRepo, patientRepo, articleRepo, lotRepo, spyBonSortie);
        Seance validated = service.validate(centerId, seanceId, "inf-01", List.of());

        assertEquals(SeanceStatus.VALIDEE, validated.getStatus());
        assertFalse(spyBonSortie.called, "BonSortie should NOT be created when no consommables");
    }

    @Test
    void validate_with_consommables_should_create_bon_sortie_via_fefo() {
        CenterId centerId = CenterId.of(UUID.randomUUID());
        UUID patientId = UUID.randomUUID();
        UUID seanceId = UUID.randomUUID();
        UUID articleId = UUID.randomUUID();
        UUID lotId = UUID.randomUUID();

        InMemorySeanceRepository seanceRepo = new InMemorySeanceRepository();
        InMemoryPatientRepository patientRepo = new InMemoryPatientRepository(patientId, centerId);
        InMemoryArticleRepository articleRepo = new InMemoryArticleRepository();
        articleRepo.addArticle(articleId, centerId.value(), "ART-01", new BigDecimal("10"));

        InMemoryLotRepository lotRepo = new InMemoryLotRepository();
        lotRepo.addLot(articleId, centerId.value(), lotId, new BigDecimal("10"), null);

        SpyBonSortieUseCase spyBonSortie = new SpyBonSortieUseCase();
        Seance seance = new Seance(seanceId, patientId, centerId.value(), LocalDate.now());
        seanceRepo.save(seance);

        SeanceDomainService service = buildService(seanceRepo, patientRepo, articleRepo, lotRepo, spyBonSortie);
        Seance validated = service.validate(centerId, seanceId, "inf-01",
                List.of(new SeanceArticleConsumption(articleId, new BigDecimal("2"))));

        assertEquals(SeanceStatus.VALIDEE, validated.getStatus());
        assertTrue(spyBonSortie.called, "BonSortie should be created");
        assertEquals(1, spyBonSortie.lastItems.size());
        assertEquals(articleId, spyBonSortie.lastItems.getFirst().articleId());
        assertEquals(new BigDecimal("2"), spyBonSortie.lastItems.getFirst().quantite());
        assertEquals(lotId, spyBonSortie.lastItems.getFirst().lotId());
    }

    @Test
    void validate_should_fail_when_no_fefo_lot_available() {
        CenterId centerId = CenterId.of(UUID.randomUUID());
        UUID patientId = UUID.randomUUID();
        UUID seanceId = UUID.randomUUID();
        UUID articleId = UUID.randomUUID();

        InMemorySeanceRepository seanceRepo = new InMemorySeanceRepository();
        InMemoryPatientRepository patientRepo = new InMemoryPatientRepository(patientId, centerId);
        InMemoryArticleRepository articleRepo = new InMemoryArticleRepository();
        articleRepo.addArticle(articleId, centerId.value(), "ART-02", new BigDecimal("0"));
        InMemoryLotRepository lotRepo = new InMemoryLotRepository(); // no lots

        SpyBonSortieUseCase spyBonSortie = new SpyBonSortieUseCase();
        seanceRepo.save(new Seance(seanceId, patientId, centerId.value(), LocalDate.now()));

        SeanceDomainService service = buildService(seanceRepo, patientRepo, articleRepo, lotRepo, spyBonSortie);
        assertThrows(IllegalStateException.class, () -> service.validate(centerId, seanceId, "inf-01",
                List.of(new SeanceArticleConsumption(articleId, new BigDecimal("3")))));
        assertFalse(spyBonSortie.called);
    }

    @Test
    void validate_should_span_multiple_lots_via_fefo_when_needed() {
        CenterId centerId = CenterId.of(UUID.randomUUID());
        UUID patientId = UUID.randomUUID();
        UUID seanceId = UUID.randomUUID();
        UUID articleId = UUID.randomUUID();
        UUID lot1 = UUID.randomUUID();
        UUID lot2 = UUID.randomUUID();

        InMemorySeanceRepository seanceRepo = new InMemorySeanceRepository();
        InMemoryPatientRepository patientRepo = new InMemoryPatientRepository(patientId, centerId);
        InMemoryArticleRepository articleRepo = new InMemoryArticleRepository();
        articleRepo.addArticle(articleId, centerId.value(), "ART-03", new BigDecimal("8"));

        InMemoryLotRepository lotRepo = new InMemoryLotRepository();
        lotRepo.addLot(articleId, centerId.value(), lot1, new BigDecimal("3"), LocalDate.now().plusDays(10)); // expires sooner → FEFO first
        lotRepo.addLot(articleId, centerId.value(), lot2, new BigDecimal("5"), LocalDate.now().plusDays(20));

        SpyBonSortieUseCase spyBonSortie = new SpyBonSortieUseCase();
        seanceRepo.save(new Seance(seanceId, patientId, centerId.value(), LocalDate.now()));

        SeanceDomainService service = buildService(seanceRepo, patientRepo, articleRepo, lotRepo, spyBonSortie);
        Seance validated = service.validate(centerId, seanceId, "inf-01",
                List.of(new SeanceArticleConsumption(articleId, new BigDecimal("5"))));

        assertEquals(SeanceStatus.VALIDEE, validated.getStatus());
        assertTrue(spyBonSortie.called);
        // 3 from lot1, 2 from lot2
        assertEquals(2, spyBonSortie.lastItems.size());
        BigDecimal totalQty = spyBonSortie.lastItems.stream()
                .map(SortieRequestItem::quantite)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(new BigDecimal("5"), totalQty);
    }

    @Test
    void signByMedecin_should_succeed_after_infirmier_validation() {
        CenterId centerId = CenterId.of(UUID.randomUUID());
        UUID patientId = UUID.randomUUID();
        UUID seanceId = UUID.randomUUID();
        InMemorySeanceRepository seanceRepo = new InMemorySeanceRepository();
        InMemoryPatientRepository patientRepo = new InMemoryPatientRepository(patientId, centerId);
        Seance seance = new Seance(seanceId, patientId, centerId.value(), LocalDate.now());
        seance.validerParInfirmier("inf-01");
        seanceRepo.save(seance);
        SeanceDomainService service = buildService(seanceRepo, patientRepo,
                new InMemoryArticleRepository(), new InMemoryLotRepository(), new SpyBonSortieUseCase());

        Seance signed = service.signByMedecin(centerId, seanceId, "med-01");
        assertEquals(SeanceStatus.SIGNEE, signed.getStatus());
        assertNotNull(signed.getSignedByMedecinAt());
        assertEquals("med-01", signed.getSignedByMedecinUserId());
    }

    @Test
    void signByMedecin_should_fail_when_seance_not_validated() {
        CenterId centerId = CenterId.of(UUID.randomUUID());
        UUID patientId = UUID.randomUUID();
        UUID seanceId = UUID.randomUUID();
        InMemorySeanceRepository seanceRepo = new InMemorySeanceRepository();
        InMemoryPatientRepository patientRepo = new InMemoryPatientRepository(patientId, centerId);
        seanceRepo.save(new Seance(seanceId, patientId, centerId.value(), LocalDate.now()));
        SeanceDomainService service = buildService(seanceRepo, patientRepo,
                new InMemoryArticleRepository(), new InMemoryLotRepository(), new SpyBonSortieUseCase());

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.signByMedecin(centerId, seanceId, "med-01"));
        assertTrue(ex.getMessage().contains("validation infirmiere"));
    }

    @Test
    void createFromQr_should_always_use_server_today_date() {
        CenterId centerId = CenterId.of(UUID.randomUUID());
        UUID patientId = UUID.randomUUID();

        InMemorySeanceRepository seanceRepo = new InMemorySeanceRepository();
        InMemoryPatientRepository patientRepo = new InMemoryPatientRepository(patientId, centerId);
        SeanceDomainService service = buildService(
                seanceRepo,
                patientRepo,
                new InMemoryArticleRepository(),
                new InMemoryLotRepository(),
                new SpyBonSortieUseCase()
        );

        Seance created = service.createFromQr(centerId, patientId.toString());

        assertEquals(LocalDate.now(), created.getDateSeance());
    }

    // ── In-memory stubs ──────────────────────────────────────────────

    private static final class InMemorySeanceRepository implements SeanceRepositoryPort {
        private final Map<UUID, Seance> data = new HashMap<>();
        @Override
        public Seance save(Seance seance) {
            data.put(seance.getId(), seance);
            return seance;
        }
        @Override
        public Optional<Seance> findById(UUID seanceId, CenterId centerId) {
            return Optional.ofNullable(data.get(seanceId)).filter(s -> s.getCenterId().equals(centerId.value()));
        }
        @Override
        public Optional<Seance> findByPatientIdAndDate(CenterId centerId, UUID patientId, LocalDate date) {
            return data.values().stream()
                    .filter(s -> s.getCenterId().equals(centerId.value()) && s.getPatientId().equals(patientId) && s.getDateSeance().equals(date))
                    .findFirst();
        }
        @Override
        public List<SeanceListItem> findAllByCenter(CenterId centerId) {
            return List.of();
        }
    }

    private record InMemoryPatientRepository(UUID patientId, CenterId centerId) implements PatientRepositoryPort {
        @Override
        public Patient save(Patient p) {
            return p;
        }
        @Override
        public Optional<Patient> findById(PatientId id, CenterId c) {
            return id.value().equals(patientId) && this.centerId.equals(c) ? Optional.of(new Patient()) : Optional.empty();
        }

        @Override
        public Optional<Patient> findByCodePatient(CenterId c, String code) {
            return Optional.empty();
        }

        @Override
        public Optional<Patient> findByNumeroAssurance(CenterId c, String n) {
            return Optional.empty();
        }

        @Override
        public List<Patient> findAllByCenter(CenterId c) {
            return List.of();
        }

        @Override
        public long countByCenter(CenterId c) {
            return 0;
        }
    }

    private static final class InMemoryArticleRepository implements ArticleRepositoryPort {
        private final Map<UUID, Article> data = new HashMap<>();

        void addArticle(UUID id, UUID centerId, String code, BigDecimal stock) {
            Article a = new Article();
            a.setId(id);
            a.setCenterId(centerId);
            a.setCode(code);
            a.setLibelle("Article " + code);
            a.setUnite("pce");
            a.setStockQuantity(stock);
            a.setPmpCourant(BigDecimal.TEN);
            a.setActive(true);
            data.put(id, a);
        }
        @Override
        public Optional<Article> findById(UUID id, CenterId c) {
            return Optional.ofNullable(data.get(id)).filter(a -> a.getCenterId().equals(c.value()));
        }

        @Override
        public Article save(Article a) {
            data.put(a.getId(), a);
            return a;
        }

        @Override
        public List<Article> findAllByCenter(CenterId c) {
            return data.values().stream().filter(a -> a.getCenterId().equals(c.value())).toList();
        }
    }

    private static final class InMemoryLotRepository implements LotRepositoryPort {
        // Each article → ordered list of lots (FEFO = by expiry asc)
        private final Map<UUID, List<Lot>> lotsByArticle = new HashMap<>();

        void addLot(UUID articleId, UUID centerId, UUID lotId, BigDecimal qty, LocalDate expiry) {
            Lot lot = new Lot();
            lot.setId(lotId);
            lot.setArticleId(articleId);
            lot.setCenterId(centerId);
            lot.setQuantiteRestante(qty);
            lot.setDatePeremption(expiry);
            lot.setNumeroLot("LOT-" + lotId.toString().substring(0, 4));
            lotsByArticle.computeIfAbsent(articleId, k -> new ArrayList<>()).add(lot);
            // keep FEFO order
            lotsByArticle.get(articleId).sort(Comparator.comparing(
                    l -> l.getDatePeremption() == null ? LocalDate.MAX : l.getDatePeremption()));
        }

        @Override
        public Lot save(Lot lot) {
            return lot;
        }

        @Override
        public Optional<Lot> findById(UUID id, CenterId c) {
            return lotsByArticle.values().stream().flatMap(Collection::stream)
                    .filter(l -> l.getId().equals(id)).findFirst();
        }

        @Override
        public List<Lot> findAvailableByArticleFefo(UUID articleId, CenterId centerId) {
            return lotsByArticle.getOrDefault(articleId, List.of()).stream()
                    .filter(l -> l.getQuantiteRestante() != null && l.getQuantiteRestante().signum() > 0)
                    .toList();
        }

        @Override
        public List<Lot> findExpiringBefore(CenterId c, LocalDate threshold) {
            return List.of();
        }

        @Override
        public List<Lot> findByArticle(UUID articleId, CenterId c) {
            return lotsByArticle.getOrDefault(articleId, List.of());
        }

        @Override
        public List<Lot> findByBonReception(UUID bonReceptionId, CenterId c) {
            return List.of();
        }
    }

    private static final class SpyBonSortieUseCase implements BonSortieUseCase {
        boolean called = false;
        List<SortieRequestItem> lastItems = List.of();

        @Override
        public BonSortie create(CenterId centerId, UUID seanceId, UUID patientId, String poste,
                                LocalDate dateSortie, List<SortieRequestItem> items, String userId) {
            called = true;
            lastItems = items;
            return null; // test doesn't need the return value
        }

        @Override
        public BonSortie get(CenterId centerId, UUID bonId) {
            return null;
        }

        @Override
        public List<BonSortie> list(CenterId centerId) {
            return List.of();
        }
    }
}
