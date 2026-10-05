package com.hemodialyse.backend.domain.seance.service;

import com.hemodialyse.backend.domain.article.model.Article;
import com.hemodialyse.backend.domain.article.model.TypeTraitementAnemie;
import com.hemodialyse.backend.domain.article.port.ArticleRepositoryPort;
import com.hemodialyse.backend.domain.patient.model.Patient;
import com.hemodialyse.backend.domain.patient.port.PatientRepositoryPort;
import com.hemodialyse.backend.domain.patient.vo.PatientId;
import com.hemodialyse.backend.domain.patient.vo.NumeroAssurance;
import com.hemodialyse.backend.domain.seance.model.Seance;
import com.hemodialyse.backend.domain.seance.model.SeanceArticleConsumption;
import com.hemodialyse.backend.domain.seance.model.SeanceListItem;
import com.hemodialyse.backend.domain.seance.model.SeanceStatus;
import com.hemodialyse.backend.domain.seance.port.SeanceBillingEligibilityPort;
import com.hemodialyse.backend.domain.seance.port.SeanceForfaitCatalogPort;
import com.hemodialyse.backend.domain.seance.port.SeanceRepositoryPort;
import com.hemodialyse.backend.domain.seance.port.VoletMedicalRepositoryPort;
import com.hemodialyse.backend.domain.seance.port.VoletParamedicalRepositoryPort;
import com.hemodialyse.backend.domain.shared.PagedResult;
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
        return buildService(seanceRepo, patientRepo, articleRepo, lotRepo, bonSortieUseCase,
                new InMemoryForfaitCatalog(), new AlwaysBillableEligibility());
    }

    private static SeanceDomainService buildService(
            SeanceRepositoryPort seanceRepo,
            PatientRepositoryPort patientRepo,
            ArticleRepositoryPort articleRepo,
            LotRepositoryPort lotRepo,
            BonSortieUseCase bonSortieUseCase,
            SeanceForfaitCatalogPort forfaitCatalogPort) {
        return buildService(seanceRepo, patientRepo, articleRepo, lotRepo, bonSortieUseCase,
                forfaitCatalogPort, new AlwaysBillableEligibility());
    }

    private static SeanceDomainService buildService(
            SeanceRepositoryPort seanceRepo,
            PatientRepositoryPort patientRepo,
            ArticleRepositoryPort articleRepo,
            LotRepositoryPort lotRepo,
            BonSortieUseCase bonSortieUseCase,
            SeanceForfaitCatalogPort forfaitCatalogPort,
            SeanceBillingEligibilityPort billingEligibilityPort) {
        return new SeanceDomainService(seanceRepo, patientRepo, articleRepo, lotRepo, bonSortieUseCase,
                mock(VoletParamedicalRepositoryPort.class), mock(VoletMedicalRepositoryPort.class),
                forfaitCatalogPort, billingEligibilityPort);
    }

    @Test
    void create_should_fail_when_patient_is_not_billable() {
        CenterId centerId = CenterId.of(UUID.randomUUID());
        UUID patientId = UUID.randomUUID();
        InMemorySeanceRepository seanceRepo = new InMemorySeanceRepository();
        InMemoryPatientRepository patientRepo = new InMemoryPatientRepository(patientId, centerId);

        SeanceDomainService service = buildService(
                seanceRepo,
                patientRepo,
                new InMemoryArticleRepository(),
                new InMemoryLotRepository(),
                new SpyBonSortieUseCase(),
                new InMemoryForfaitCatalog(),
                new NeverBillableEligibility());

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.create(centerId, patientId, LocalDate.of(2026, 8, 14)));
        assertEquals("Le patient doit avoir une prise en charge valide pour être facturé", ex.getMessage());
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
    void validate_should_allow_additional_consommables_when_seance_already_validated() {
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
        seance.validerParInfirmier("inf-01");
        seanceRepo.save(seance);

        SeanceDomainService service = buildService(seanceRepo, patientRepo, articleRepo, lotRepo, spyBonSortie);
        Seance result = service.validate(centerId, seanceId, "inf-02",
                List.of(new SeanceArticleConsumption(articleId, new BigDecimal("1"))));

        assertEquals(SeanceStatus.VALIDEE, result.getStatus());
        assertTrue(spyBonSortie.called);
        assertEquals(new BigDecimal("1"), spyBonSortie.lastItems.getFirst().quantite());
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

    @Test
    void create_should_fail_when_patient_belongs_to_another_center() {
        CenterId requestedCenter = CenterId.of(UUID.randomUUID());
        CenterId patientCenter = CenterId.of(UUID.randomUUID());
        UUID patientId = UUID.randomUUID();

        InMemorySeanceRepository seanceRepo = new InMemorySeanceRepository();
        InMemoryPatientRepository patientRepo = new InMemoryPatientRepository(patientId, patientCenter);

        SeanceDomainService service = buildService(
                seanceRepo,
                patientRepo,
                new InMemoryArticleRepository(),
                new InMemoryLotRepository(),
                new SpyBonSortieUseCase()
        );

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> service.create(requestedCenter, patientId, LocalDate.now())
        );
        assertTrue(ex.getMessage().contains("Patient introuvable"));
    }

    @Test
    void createFromQr_should_fail_when_code_matches_patient_of_other_center() {
        CenterId activeCenter = CenterId.of(UUID.randomUUID());
        CenterId foreignCenter = CenterId.of(UUID.randomUUID());

        InMemorySeanceRepository seanceRepo = new InMemorySeanceRepository();
        InMemoryPatientRepository patientRepo = new InMemoryPatientRepository(UUID.randomUUID(), activeCenter);
        patientRepo.addPatient(UUID.randomUUID(), foreignCenter, "PAT-FOREIGN", "ASS-FOREIGN");

        SeanceDomainService service = buildService(
                seanceRepo,
                patientRepo,
                new InMemoryArticleRepository(),
                new InMemoryLotRepository(),
                new SpyBonSortieUseCase()
        );

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> service.createFromQr(activeCenter, "PAT-FOREIGN")
        );
        assertTrue(ex.getMessage().contains("Patient introuvable"));
    }

    // ───────── Scan infirmier : la séance du jour est validée directement ─────────

    private ScanFixture scanFixture() {
        CenterId centerId = CenterId.of(UUID.randomUUID());
        UUID patientId = UUID.randomUUID();
        InMemorySeanceRepository seanceRepo = new InMemorySeanceRepository();
        SpyBonSortieUseCase spy = new SpyBonSortieUseCase();
        SeanceDomainService service = buildService(seanceRepo, new InMemoryPatientRepository(patientId, centerId),
                new InMemoryArticleRepository(), new InMemoryLotRepository(), spy);
        return new ScanFixture(centerId, patientId, seanceRepo, spy, service);
    }

    @Test
    void scanAndValidate_should_create_then_validate_when_no_session_exists_today() {
        ScanFixture f = scanFixture();

        var result = f.service().scanAndValidate(f.centerId(), f.patientId().toString(), "inf-01");

        assertTrue(result.created());
        assertTrue(result.validatedNow());
        assertFalse(result.alreadyValidated());
        assertEquals(SeanceStatus.VALIDEE, result.seance().getStatus());
        assertEquals(LocalDate.now(), result.seance().getDateSeance());
        assertEquals("inf-01", result.seance().getSignedByInfirmierUserId());
        assertNotNull(result.seance().getValidatedAt());
        assertFalse(f.bonSortie().called, "aucun consommable : aucune sortie de stock");
    }

    @Test
    void scanAndValidate_should_validate_an_existing_created_session_without_duplicating_it() {
        ScanFixture f = scanFixture();
        UUID id = UUID.randomUUID();
        f.seances().save(new Seance(id, f.patientId(), f.centerId().value(), LocalDate.now()));

        var result = f.service().scanAndValidate(f.centerId(), f.patientId().toString(), "inf-01");

        assertFalse(result.created());
        assertTrue(result.validatedNow());
        assertEquals(id, result.seance().getId());
        assertEquals(SeanceStatus.VALIDEE, f.seances().findById(id, f.centerId()).orElseThrow().getStatus());
        assertEquals(id, f.seances().findByPatientIdAndDate(f.centerId(), f.patientId(), LocalDate.now()).orElseThrow().getId());
    }

    @Test
    void scanAndValidate_should_be_idempotent_for_validated_signed_or_billed_sessions() {
        for (SeanceStatus status : List.of(SeanceStatus.VALIDEE, SeanceStatus.SIGNEE, SeanceStatus.FACTUREE)) {
            ScanFixture f = scanFixture();
            Seance existante = new Seance(UUID.randomUUID(), f.patientId(), f.centerId().value(), LocalDate.now());
            existante.setStatus(status);
            f.seances().save(existante);

            var result = f.service().scanAndValidate(f.centerId(), f.patientId().toString(), "inf-02");

            assertTrue(result.alreadyValidated(), status.name());
            assertFalse(result.created());
            assertFalse(result.validatedNow());
            assertEquals(status, result.seance().getStatus(), "le statut ne change pas");
            assertNull(result.seance().getSignedByInfirmierUserId(), "pas de nouvelle signature infirmier");
        }
    }

    @Test
    void scanAndValidate_should_refuse_an_absent_session() {
        ScanFixture f = scanFixture();
        Seance absente = new Seance(UUID.randomUUID(), f.patientId(), f.centerId().value(), LocalDate.now());
        absente.setStatus(SeanceStatus.ABSENT);
        f.seances().save(absente);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> f.service().scanAndValidate(f.centerId(), f.patientId().toString(), "inf-01"));
        assertTrue(ex.getMessage().contains("absente"));
    }

    @Test
    void scanAndValidate_should_refuse_a_patient_of_another_center() {
        ScanFixture f = scanFixture();
        CenterId autre = CenterId.of(UUID.randomUUID());

        assertThrows(IllegalArgumentException.class,
                () -> f.service().scanAndValidate(autre, f.patientId().toString(), "inf-01"));
    }

    @Test
    void addConsommableSeance_should_issue_only_the_new_line_on_a_validated_session() {
        CenterId centerId = CenterId.of(UUID.randomUUID());
        UUID patientId = UUID.randomUUID();
        UUID seanceId = UUID.randomUUID();
        UUID articleId = UUID.randomUUID();
        InMemorySeanceRepository seanceRepo = new InMemorySeanceRepository();
        InMemoryArticleRepository articles = new InMemoryArticleRepository();
        articles.addArticle(articleId, centerId.value(), "ART-1", BigDecimal.TEN);
        SpyBonSortieUseCase spy = new SpyBonSortieUseCase();
        Seance seance = new Seance(seanceId, patientId, centerId.value(), LocalDate.now());
        seance.validerParInfirmier("inf-01");
        seanceRepo.save(seance);
        SeanceDomainService service = buildService(seanceRepo, new InMemoryPatientRepository(patientId, centerId),
                articles, new InMemoryLotRepository(), spy);

        service.addConsommableSeance(centerId, seanceId, articleId, new BigDecimal("2"), "inf-01");

        assertTrue(spy.addCalled);
        assertEquals(articleId, spy.lastAddedArticleId);
        assertEquals(new BigDecimal("2"), spy.lastAddedQuantite);
        assertFalse(spy.called, "ni bon de sortie de validation ni retraitement des lignes existantes");
    }

    // ───────── Ajout d'un consommable à une séance déjà validée ─────────

    @Test
    void addConsommableSeance_should_refuse_a_created_a_billed_session_a_bad_quantity_and_an_inactive_article() {
        CenterId centerId = CenterId.of(UUID.randomUUID());
        UUID patientId = UUID.randomUUID();
        UUID articleId = UUID.randomUUID();
        InMemorySeanceRepository seanceRepo = new InMemorySeanceRepository();
        InMemoryArticleRepository articles = new InMemoryArticleRepository();
        articles.addArticle(articleId, centerId.value(), "ART-1", BigDecimal.TEN);
        SpyBonSortieUseCase spy = new SpyBonSortieUseCase();
        SeanceDomainService service = buildService(seanceRepo, new InMemoryPatientRepository(patientId, centerId),
                articles, new InMemoryLotRepository(), spy);

        UUID creee = UUID.randomUUID();
        seanceRepo.save(new Seance(creee, patientId, centerId.value(), LocalDate.now()));
        assertThrows(IllegalStateException.class,
                () -> service.addConsommableSeance(centerId, creee, articleId, BigDecimal.ONE, "inf"));

        UUID facturee = UUID.randomUUID();
        Seance f = new Seance(facturee, patientId, centerId.value(), LocalDate.now().minusDays(1));
        f.setStatus(SeanceStatus.FACTUREE);
        seanceRepo.save(f);
        assertThrows(IllegalStateException.class,
                () -> service.addConsommableSeance(centerId, facturee, articleId, BigDecimal.ONE, "inf"));

        UUID validee = UUID.randomUUID();
        Seance v = new Seance(validee, patientId, centerId.value(), LocalDate.now().minusDays(2));
        v.validerParInfirmier("inf");
        seanceRepo.save(v);
        assertThrows(IllegalArgumentException.class,
                () -> service.addConsommableSeance(centerId, validee, articleId, BigDecimal.ZERO, "inf"));
        assertThrows(IllegalArgumentException.class,
                () -> service.addConsommableSeance(centerId, validee, UUID.randomUUID(), BigDecimal.ONE, "inf"));
        assertFalse(spy.addCalled, "aucune sortie de stock sur un refus");
    }

    private record ScanFixture(CenterId centerId, UUID patientId, InMemorySeanceRepository seances,
                               SpyBonSortieUseCase bonSortie, SeanceDomainService service) {
    }

    @Test
    void removeConsommableSeance_should_set_the_article_quantity_to_zero_on_the_session_bon() {
        CenterId centerId = CenterId.of(UUID.randomUUID());
        UUID patientId = UUID.randomUUID();
        UUID seanceId = UUID.randomUUID();
        UUID articleId = UUID.randomUUID();

        InMemorySeanceRepository seanceRepo = new InMemorySeanceRepository();
        InMemoryPatientRepository patientRepo = new InMemoryPatientRepository(patientId, centerId);
        SpyBonSortieUseCase spyBonSortie = new SpyBonSortieUseCase();
        Seance seance = new Seance(seanceId, patientId, centerId.value(), LocalDate.now());
        seance.validerParInfirmier("inf-01");
        seanceRepo.save(seance);

        SeanceDomainService service = buildService(seanceRepo, patientRepo,
                new InMemoryArticleRepository(), new InMemoryLotRepository(), spyBonSortie);
        service.removeConsommableSeance(centerId, seanceId, articleId, "inf-01");

        assertTrue(spyBonSortie.setCalled);
        assertEquals(articleId, spyBonSortie.lastSetArticleId);
        assertEquals(0, BigDecimal.ZERO.compareTo(spyBonSortie.lastSetQuantite));
    }

    @Test
    void updateConsommableSeance_should_set_the_new_quantity_on_the_single_session_bon() {
        CenterId centerId = CenterId.of(UUID.randomUUID());
        UUID patientId = UUID.randomUUID();
        UUID seanceId = UUID.randomUUID();
        UUID articleId = UUID.randomUUID();

        InMemorySeanceRepository seanceRepo = new InMemorySeanceRepository();
        InMemoryPatientRepository patientRepo = new InMemoryPatientRepository(patientId, centerId);
        SpyBonSortieUseCase spyBonSortie = new SpyBonSortieUseCase();
        Seance seance = new Seance(seanceId, patientId, centerId.value(), LocalDate.now());
        seance.validerParInfirmier("inf-01");
        seanceRepo.save(seance);

        SeanceDomainService service = buildService(seanceRepo, patientRepo,
                new InMemoryArticleRepository(), new InMemoryLotRepository(), spyBonSortie);
        service.updateConsommableSeance(centerId, seanceId, articleId, new BigDecimal("3"), "inf-01");

        assertTrue(spyBonSortie.setCalled);
        assertEquals(articleId, spyBonSortie.lastSetArticleId);
        assertEquals(new BigDecimal("3"), spyBonSortie.lastSetQuantite);
        assertFalse(spyBonSortie.addCalled, "une modification ne crée pas une nouvelle sortie");
    }

    @Test
    void recentByPatient_should_return_the_latest_sessions_before_the_date_newest_first_and_bounded() {
        CenterId centerId = CenterId.of(UUID.randomUUID());
        UUID patientId = UUID.randomUUID();
        InMemorySeanceRepository seanceRepo = new InMemorySeanceRepository();
        LocalDate today = LocalDate.of(2026, 10, 4);
        for (int i = 0; i < 14; i++) {
            seanceRepo.save(new Seance(UUID.randomUUID(), patientId, centerId.value(), today.minusDays(i)));
        }
        seanceRepo.save(new Seance(UUID.randomUUID(), UUID.randomUUID(), centerId.value(), today.minusDays(1)));
        SeanceDomainService service = buildService(seanceRepo, new InMemoryPatientRepository(patientId, centerId),
                new InMemoryArticleRepository(), new InMemoryLotRepository(), new SpyBonSortieUseCase());

        var recents = service.recentByPatient(centerId, patientId, today, 3);

        assertEquals(3, recents.size());
        assertEquals(today.minusDays(1), recents.get(0).seance().getDateSeance(), "la séance du jour est exclue");
        assertEquals(today.minusDays(3), recents.get(2).seance().getDateSeance());
        assertEquals(10, service.recentByPatient(centerId, patientId, today, 99).size(), "limite bornée à 10");
        assertEquals(1, service.recentByPatient(centerId, patientId, today, 0).size(), "au moins une");
        assertEquals(0, service.recentByPatient(CenterId.of(UUID.randomUUID()), patientId, today, 3).size(),
                "un autre centre ne voit rien");
    }

    @Test
    void removeConsommableSeance_should_fail_when_facturee() {
        CenterId centerId = CenterId.of(UUID.randomUUID());
        UUID patientId = UUID.randomUUID();
        UUID seanceId = UUID.randomUUID();

        InMemorySeanceRepository seanceRepo = new InMemorySeanceRepository();
        InMemoryPatientRepository patientRepo = new InMemoryPatientRepository(patientId, centerId);
        Seance seance = new Seance(seanceId, patientId, centerId.value(), LocalDate.now());
        seance.setStatus(SeanceStatus.FACTUREE);
        seanceRepo.save(seance);

        SeanceDomainService service = buildService(seanceRepo, patientRepo,
                new InMemoryArticleRepository(), new InMemoryLotRepository(), new SpyBonSortieUseCase());

        assertThrows(IllegalStateException.class,
                () -> service.removeConsommableSeance(centerId, seanceId, UUID.randomUUID(), "inf-01"));
    }

    @Test
    void updateDate_should_allow_edit_when_seance_is_signee() {
        CenterId centerId = CenterId.of(UUID.randomUUID());
        UUID patientId = UUID.randomUUID();
        UUID seanceId = UUID.randomUUID();

        InMemorySeanceRepository seanceRepo = new InMemorySeanceRepository();
        InMemoryPatientRepository patientRepo = new InMemoryPatientRepository(patientId, centerId);
        Seance seance = new Seance(seanceId, patientId, centerId.value(), LocalDate.now());
        seance.validerParInfirmier("inf-01");
        seance.signerParMedecin("med-01");
        seanceRepo.save(seance);

        SeanceDomainService service = buildService(
                seanceRepo,
                patientRepo,
                new InMemoryArticleRepository(),
                new InMemoryLotRepository(),
                new SpyBonSortieUseCase()
        );

        LocalDate newDate = LocalDate.now().plusDays(1);
        Seance updated = service.updateDate(centerId, seanceId, newDate);
        assertEquals(newDate, updated.getDateSeance());
    }

    @Test
    void updateDate_should_fail_when_seance_is_facturee() {
        CenterId centerId = CenterId.of(UUID.randomUUID());
        UUID patientId = UUID.randomUUID();
        UUID seanceId = UUID.randomUUID();

        InMemorySeanceRepository seanceRepo = new InMemorySeanceRepository();
        InMemoryPatientRepository patientRepo = new InMemoryPatientRepository(patientId, centerId);
        Seance seance = new Seance(seanceId, patientId, centerId.value(), LocalDate.now());
        seance.setStatus(SeanceStatus.FACTUREE);
        seanceRepo.save(seance);

        SeanceDomainService service = buildService(
                seanceRepo,
                patientRepo,
                new InMemoryArticleRepository(),
                new InMemoryLotRepository(),
                new SpyBonSortieUseCase()
        );

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> service.updateDate(centerId, seanceId, LocalDate.now().plusDays(1))
        );
        assertTrue(ex.getMessage().contains("facturee"));
    }

    @Test
    void updateForfait_should_store_override_snapshot_when_seance_not_facturee() {
        CenterId centerId = CenterId.of(UUID.randomUUID());
        UUID patientId = UUID.randomUUID();
        UUID seanceId = UUID.randomUUID();
        UUID forfaitId = UUID.randomUUID();

        InMemorySeanceRepository seanceRepo = new InMemorySeanceRepository();
        InMemoryPatientRepository patientRepo = new InMemoryPatientRepository(patientId, centerId);
        InMemoryForfaitCatalog forfaitCatalog = new InMemoryForfaitCatalog();
        forfaitCatalog.add(forfaitId, centerId, "F-HD", "Forfait HD", new BigDecimal("3500.00"));
        Seance seance = new Seance(seanceId, patientId, centerId.value(), LocalDate.now());
        seanceRepo.save(seance);

        SeanceDomainService service = buildService(
                seanceRepo,
                patientRepo,
                new InMemoryArticleRepository(),
                new InMemoryLotRepository(),
                new SpyBonSortieUseCase(),
                forfaitCatalog
        );

        Seance updated = service.updateForfait(centerId, seanceId, forfaitId, "inf-01");

        assertEquals(forfaitId, updated.getForfaitOverrideId());
        assertEquals("F-HD", updated.getForfaitOverrideCode());
        assertEquals("Forfait HD", updated.getForfaitOverrideNom());
        assertEquals(new BigDecimal("3500.00"), updated.getForfaitOverridePrix());
        assertEquals("inf-01", updated.getForfaitOverrideUpdatedBy());
        assertNotNull(updated.getForfaitOverrideUpdatedAt());
    }

    @Test
    void updateForfait_should_fail_when_seance_is_facturee() {
        CenterId centerId = CenterId.of(UUID.randomUUID());
        UUID patientId = UUID.randomUUID();
        UUID seanceId = UUID.randomUUID();
        UUID forfaitId = UUID.randomUUID();

        InMemorySeanceRepository seanceRepo = new InMemorySeanceRepository();
        InMemoryPatientRepository patientRepo = new InMemoryPatientRepository(patientId, centerId);
        InMemoryForfaitCatalog forfaitCatalog = new InMemoryForfaitCatalog();
        forfaitCatalog.add(forfaitId, centerId, "F-HD", "Forfait HD", new BigDecimal("3500.00"));
        Seance seance = new Seance(seanceId, patientId, centerId.value(), LocalDate.now());
        seance.setStatus(SeanceStatus.FACTUREE);
        seanceRepo.save(seance);

        SeanceDomainService service = buildService(
                seanceRepo,
                patientRepo,
                new InMemoryArticleRepository(),
                new InMemoryLotRepository(),
                new SpyBonSortieUseCase(),
                forfaitCatalog
        );

        assertThrows(IllegalStateException.class,
                () -> service.updateForfait(centerId, seanceId, forfaitId, "inf-01"));
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

        @Override
        public PagedResult<SeanceListItem> findPagedByCenter(CenterId centerId, int page, int size) {
            return new PagedResult<>(List.of(), 0, page, size);
        }

        @Override
        public List<Seance> findRecentByPatient(CenterId centerId, UUID patientId, LocalDate before, int limit) {
            return data.values().stream()
                    .filter(s -> centerId.value().equals(s.getCenterId()) && patientId.equals(s.getPatientId())
                            && s.getDateSeance().isBefore(before))
                    .sorted(java.util.Comparator.comparing(Seance::getDateSeance).reversed())
                    .limit(limit)
                    .toList();
        }

        @Override
        public PagedResult<SeanceListItem> findPagedByCenterAndMonth(CenterId centerId, java.time.YearMonth month, int page, int size) {
            return new PagedResult<>(List.of(), 0, page, size);
        }
    }

    private static final class InMemoryPatientRepository implements PatientRepositoryPort {
        private final Map<UUID, Patient> byId = new HashMap<>();

        private InMemoryPatientRepository(UUID patientId, CenterId centerId) {
            addPatient(patientId, centerId, "PAT-" + patientId.toString().substring(0, 6).toUpperCase(), "ASS-" + patientId.toString().substring(0, 6).toUpperCase());
        }

        void addPatient(UUID patientId, CenterId centerId, String codePatient, String numeroAssurance) {
            Patient patient = new Patient();
            patient.setId(PatientId.of(patientId));
            patient.setCenterId(centerId);
            patient.setCodePatient(codePatient);
            patient.setNumeroAssurance(new NumeroAssurance(numeroAssurance));
            patient.setNom("Test");
            patient.setPrenom("Patient");
            byId.put(patientId, patient);
        }

        @Override
        public Patient save(Patient p) {
            byId.put(p.getId().value(), p);
            return p;
        }

        @Override
        public Optional<Patient> findById(PatientId id, CenterId centerId) {
            return Optional.ofNullable(byId.get(id.value()))
                    .filter(p -> p.getCenterId().equals(centerId));
        }

        @Override
        public Optional<Patient> findByCodePatient(CenterId centerId, String codePatient) {
            return byId.values().stream()
                    .filter(p -> p.getCenterId().equals(centerId))
                    .filter(p -> p.getCodePatient() != null && p.getCodePatient().equalsIgnoreCase(codePatient))
                    .findFirst();
        }

        @Override
        public Optional<Patient> findByNumeroAssurance(CenterId centerId, String numeroAssurance) {
            return byId.values().stream()
                    .filter(p -> p.getCenterId().equals(centerId))
                    .filter(p -> p.getNumeroAssurance() != null && p.getNumeroAssurance().value().equalsIgnoreCase(numeroAssurance))
                    .findFirst();
        }

        @Override
        public List<Patient> findAllByCenter(CenterId centerId) {
            return byId.values().stream().filter(p -> p.getCenterId().equals(centerId)).toList();
        }

        @Override
        public long countByCenter(CenterId centerId) {
            return byId.values().stream().filter(p -> p.getCenterId().equals(centerId)).count();
        }

        @Override
        public List<Patient> findWithAssignment(CenterId centerId) {
            return byId.values().stream().filter(p -> p.getCenterId().equals(centerId) && p.getSalleId() != null).toList();
        }

        @Override
        public List<CenterId> findCentersWithAssignments() {
            return byId.values().stream().filter(p -> p.getSalleId() != null).map(Patient::getCenterId).distinct().toList();
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

        @Override
        public List<Article> findAllByCenterAndTypeTraitementAnemie(CenterId c, TypeTraitementAnemie type) {
            return data.values().stream()
                    .filter(a -> a.getCenterId().equals(c.value()))
                    .filter(a -> a.getTypeTraitementAnemie() == type)
                    .toList();
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

    private static final class InMemoryForfaitCatalog implements SeanceForfaitCatalogPort {
        private final Map<UUID, SeanceForfaitSnapshot> data = new HashMap<>();

        void add(UUID forfaitId, CenterId centerId, String code, String nom, BigDecimal prix) {
            data.put(composeKey(centerId, forfaitId), new SeanceForfaitSnapshot(forfaitId, code, nom, prix));
        }

        @Override
        public Optional<SeanceForfaitSnapshot> findById(CenterId centerId, UUID forfaitId) {
            return Optional.ofNullable(data.get(composeKey(centerId, forfaitId)));
        }

        private UUID composeKey(CenterId centerId, UUID forfaitId) {
            return UUID.nameUUIDFromBytes((centerId.value() + ":" + forfaitId).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
    }

    private static final class AlwaysBillableEligibility implements SeanceBillingEligibilityPort {
        @Override
        public boolean isPatientBillableAt(CenterId centerId, UUID patientId, LocalDate dateSeance) {
            return true;
        }
    }

    private static final class NeverBillableEligibility implements SeanceBillingEligibilityPort {
        @Override
        public boolean isPatientBillableAt(CenterId centerId, UUID patientId, LocalDate dateSeance) {
            return false;
        }
    }

    private static final class SpyBonSortieUseCase implements BonSortieUseCase {
        boolean called = false;
        List<SortieRequestItem> lastItems = List.of();
        boolean setCalled = false;
        UUID lastSetArticleId = null;
        BigDecimal lastSetQuantite = null;
        boolean addCalled = false;
        UUID lastAddedArticleId = null;
        BigDecimal lastAddedQuantite = null;

        @Override
        public BonSortie create(CenterId centerId, UUID seanceId, UUID patientId, String poste,
                                LocalDate dateSortie, List<SortieRequestItem> items, String userId) {
            called = true;
            lastItems = items;
            return null;
        }

        @Override
        public BonSortie get(CenterId centerId, UUID bonId) {
            return null;
        }

        @Override
        public List<BonSortie> list(CenterId centerId) {
            return List.of();
        }

        @Override
        public BonSortie setSeanceConsommation(CenterId centerId, UUID seanceId, UUID patientId, LocalDate dateSeance,
                                               UUID articleId, java.math.BigDecimal quantite, String userId) {
            setCalled = true;
            lastSetArticleId = articleId;
            lastSetQuantite = quantite;
            return null;
        }

        @Override
        public BonSortie addSeanceConsommation(CenterId centerId, UUID seanceId, UUID patientId, LocalDate dateSeance,
                                               UUID articleId, java.math.BigDecimal quantite, String userId) {
            addCalled = true;
            lastAddedArticleId = articleId;
            lastAddedQuantite = quantite;
            return null;
        }

        @Override
        public BonSortie update(CenterId centerId, UUID bonSortieId, UUID seanceId, UUID patientId, String poste,
                                LocalDate dateSortie, List<SortieRequestItem> items, String userId) {
            return null;
        }

        @Override
        public BonSortie createViaFefo(CenterId centerId, UUID seanceId, UUID patientId, String poste,
                                       LocalDate dateSortie, UUID articleId, java.math.BigDecimal quantite, String userId) {
            return null;
        }
    }
}
