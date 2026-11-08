package com.hemodialyse.backend.domain.seance.service;

import com.hemodialyse.backend.contexts.stock.publishedlanguage.ArticleDuCatalogue;
import com.hemodialyse.backend.contexts.stock.publishedlanguage.CatalogueDArticles;
import com.hemodialyse.backend.domain.patient.model.Patient;
import com.hemodialyse.backend.domain.patient.port.PatientRepositoryPort;
import com.hemodialyse.backend.domain.patient.vo.PatientId;
import com.hemodialyse.backend.domain.patient.vo.NumeroAssurance;
import com.hemodialyse.backend.domain.seance.model.DerogationPlanning;
import com.hemodialyse.backend.domain.seance.model.MotifHorsPlanning;
import com.hemodialyse.backend.domain.seance.model.PlaceSeance;
import com.hemodialyse.backend.domain.seance.model.SituationPlanning;
import com.hemodialyse.backend.domain.shared.exception.BusinessException;
import com.hemodialyse.backend.domain.seance.model.Seance;
import com.hemodialyse.backend.domain.seance.model.SeanceArticleConsumption;
import com.hemodialyse.backend.domain.seance.model.SeanceListItem;
import com.hemodialyse.backend.domain.seance.model.SeanceSearch;
import com.hemodialyse.backend.domain.seance.model.SeanceStatus;
import com.hemodialyse.backend.domain.seance.port.SeanceBillingEligibilityPort;
import com.hemodialyse.backend.domain.seance.port.SeanceRepositoryPort;
import com.hemodialyse.backend.domain.seance.port.VoletMedicalRepositoryPort;
import com.hemodialyse.backend.domain.seance.port.VoletParamedicalRepositoryPort;
import com.hemodialyse.backend.domain.shared.PagedResult;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.contexts.stock.publishedlanguage.ConsommationsDeSeance;
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
            CatalogueDArticles articleRepo,
            InMemoryLotRepository lotRepo,
            ConsommationsDeSeance bonSortieUseCase) {
        return buildService(seanceRepo, patientRepo, articleRepo, lotRepo, bonSortieUseCase,
                new InMemoryForfaitCatalog(), new AlwaysBillableEligibility());
    }

    private static SeanceDomainService buildService(
            SeanceRepositoryPort seanceRepo,
            PatientRepositoryPort patientRepo,
            CatalogueDArticles articleRepo,
            InMemoryLotRepository lotRepo,
            ConsommationsDeSeance bonSortieUseCase,
            InMemoryForfaitCatalog forfaitCatalogPort) {
        return buildService(seanceRepo, patientRepo, articleRepo, lotRepo, bonSortieUseCase,
                forfaitCatalogPort, new AlwaysBillableEligibility());
    }

    /**
     * Patient programmé ce jour : jour de dialyse, centre ouvert, patient régulier.
     */
    private static final SituationPlanning ATTENDU = new SituationPlanning(false, true, false, null, null, null);
    private static final SituationPlanning JOUR_NON_DIALYSE =
            new SituationPlanning(false, false, false, null, null, null);
    private static final SituationPlanning CENTRE_FERME =
            new SituationPlanning(true, true, false, null, null, null);

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
    void create_should_say_that_the_pec_is_not_validated_when_it_covers_the_date() {
        CenterId centerId = CenterId.of(UUID.randomUUID());
        UUID patientId = UUID.randomUUID();
        SeanceBillingEligibilityPort nonValidee = new SeanceBillingEligibilityPort() {
            @Override
            public boolean isPatientBillableAt(CenterId c, UUID p, LocalDate d) {
                return false;
            }

            @Override
            public java.util.Optional<CauseNonFacturable> causeNonFacturable(CenterId c, UUID p, LocalDate d) {
                return java.util.Optional.of(CauseNonFacturable.PRISE_EN_CHARGE_NON_VALIDEE);
            }
        };
        SeanceDomainService service = buildService(new InMemorySeanceRepository(),
                new InMemoryPatientRepository(patientId, centerId), new InMemoryArticleRepository(),
                new InMemoryLotRepository(), new SpyBonSortieUseCase(), new InMemoryForfaitCatalog(), nonValidee);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.create(centerId, patientId, LocalDate.of(2026, 8, 14)));

        assertTrue(ex.getMessage().contains("n'est pas validée"), ex.getMessage());
    }

    @Test
    void create_should_say_that_the_rights_certificate_is_missing_when_the_pec_itself_is_valid() {
        CenterId centerId = CenterId.of(UUID.randomUUID());
        UUID patientId = UUID.randomUUID();
        SeanceBillingEligibilityPort sansAttestation = new SeanceBillingEligibilityPort() {
            @Override
            public boolean isPatientBillableAt(CenterId c, UUID p, LocalDate d) {
                return false;
            }

            @Override
            public java.util.Optional<CauseNonFacturable> causeNonFacturable(CenterId c, UUID p, LocalDate d) {
                return java.util.Optional.of(CauseNonFacturable.ATTESTATION_DROITS);
            }
        };
        SeanceDomainService service = buildService(new InMemorySeanceRepository(),
                new InMemoryPatientRepository(patientId, centerId), new InMemoryArticleRepository(),
                new InMemoryLotRepository(), new SpyBonSortieUseCase(), new InMemoryForfaitCatalog(), sansAttestation);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.create(centerId, patientId, LocalDate.of(2026, 8, 14)));

        assertTrue(ex.getMessage().contains("attestation de droits"), ex.getMessage());
        assertTrue(ex.getMessage().contains("2026-08-14"), ex.getMessage());
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
    private static final SituationPlanning PATIENT_EN_SOMMEIL =
            new SituationPlanning(false, true, true, null, null, null);

    private static SeanceDomainService buildService(
            SeanceRepositoryPort seanceRepo,
            PatientRepositoryPort patientRepo,
            CatalogueDArticles articleRepo,
            InMemoryLotRepository lotRepo,
            ConsommationsDeSeance bonSortieUseCase,
            InMemoryForfaitCatalog forfaitCatalogPort,
            SeanceBillingEligibilityPort billingEligibilityPort) {
        return buildService(seanceRepo, patientRepo, articleRepo, lotRepo, bonSortieUseCase, forfaitCatalogPort,
                billingEligibilityPort, ATTENDU);
    }

    private static SeanceDomainService buildService(
            SeanceRepositoryPort seanceRepo,
            PatientRepositoryPort patientRepo,
            CatalogueDArticles articleRepo,
            InMemoryLotRepository lotRepo,
            ConsommationsDeSeance bonSortieUseCase,
            InMemoryForfaitCatalog forfaitCatalogPort,
            SeanceBillingEligibilityPort billingEligibilityPort,
            SituationPlanning situation) {
        return new SeanceDomainService(seanceRepo, patientRepo,
                mock(VoletParamedicalRepositoryPort.class), mock(VoletMedicalRepositoryPort.class),
                billingEligibilityPort, (centerId, patientId, date) -> situation,
                (centerId, patientId, date) -> Optional.of(PLACE_DU_JOUR));
    }

    private static final PlaceSeance PLACE_DU_JOUR = new PlaceSeance(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());

    // SE2 : la place figée à la validation est couverte par ValiderUneSeanceTest (contexts/seance).

    @Test
    void a_place_requires_a_room_and_a_slot() {
        assertThrows(IllegalArgumentException.class, () -> new PlaceSeance(null, UUID.randomUUID(), null));
        assertThrows(IllegalArgumentException.class, () -> new PlaceSeance(UUID.randomUUID(), null, null));
    }

    private ScanFixture scanFixture() {
        return scanFixture(ATTENDU);
    }

    private ScanFixture scanFixture(SituationPlanning situation) {
        CenterId centerId = CenterId.of(UUID.randomUUID());
        UUID patientId = UUID.randomUUID();
        InMemorySeanceRepository seanceRepo = new InMemorySeanceRepository();
        SpyBonSortieUseCase spy = new SpyBonSortieUseCase();
        SeanceDomainService service = buildService(seanceRepo, new InMemoryPatientRepository(patientId, centerId),
                new InMemoryArticleRepository(), new InMemoryLotRepository(), spy, new InMemoryForfaitCatalog(),
                new AlwaysBillableEligibility(), situation);
        return new ScanFixture(centerId, patientId, seanceRepo, spy, service);
    }

    @Test
    void scanAndValidate_should_create_then_validate_when_no_session_exists_today() {
        ScanFixture f = scanFixture();

        var result = f.service().scanAndValidate(f.centerId(), f.patientId().toString(), "inf-01", null);

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

        var result = f.service().scanAndValidate(f.centerId(), f.patientId().toString(), "inf-01", null);

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

            var result = f.service().scanAndValidate(f.centerId(), f.patientId().toString(), "inf-02", null);

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
                () -> f.service().scanAndValidate(f.centerId(), f.patientId().toString(), "inf-01", null));
        assertTrue(ex.getMessage().contains("absente"));
    }

    @Test
    void scanAndValidate_should_ask_for_confirmation_when_the_patient_is_not_scheduled_today() {
        ScanFixture f = scanFixture(JOUR_NON_DIALYSE);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> f.service().scanAndValidate(f.centerId(), f.patientId().toString(), "inf-01", null));

        assertEquals("SEANCE_HORS_PLANNING_JOUR", ex.getCode());
        assertTrue(f.seances().findByPatientIdAndDate(f.centerId(), f.patientId(), LocalDate.now()).isEmpty(),
                "rien n'est créé sans confirmation");
    }

    @Test
    void scanAndValidate_should_flag_the_session_when_the_nurse_confirms_with_a_motive() {
        ScanFixture f = scanFixture(JOUR_NON_DIALYSE);

        var result = f.service().scanAndValidate(f.centerId(), f.patientId().toString(), "inf-01",
                new DerogationPlanning(MotifHorsPlanning.RATTRAPAGE, null, false));

        assertTrue(result.created());
        assertEquals(SeanceStatus.VALIDEE, result.seance().getStatus());
        assertTrue(result.seance().isHorsPlanning());
        assertEquals(MotifHorsPlanning.RATTRAPAGE, result.seance().getMotifHorsPlanning());
    }

    @Test
    void scanAndValidate_should_distinguish_an_unexpected_patient_from_a_non_dialysis_day() {
        ScanFixture f = scanFixture(PATIENT_EN_SOMMEIL);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> f.service().scanAndValidate(f.centerId(), f.patientId().toString(), "inf-01", null));

        assertEquals("SEANCE_HORS_PLANNING_PATIENT", ex.getCode());
    }

    @Test
    void scanAndValidate_should_refuse_a_closed_center_unless_an_administrator_forces_it() {
        ScanFixture f = scanFixture(CENTRE_FERME);
        var parInfirmier = new DerogationPlanning(MotifHorsPlanning.URGENCE, null, false);

        assertEquals("SEANCE_CENTRE_FERME", assertThrows(BusinessException.class,
                () -> f.service().scanAndValidate(f.centerId(), f.patientId().toString(), "inf-01", null)).getCode());
        assertEquals("SEANCE_CENTRE_FERME", assertThrows(BusinessException.class,
                () -> f.service().scanAndValidate(f.centerId(), f.patientId().toString(), "inf-01", parInfirmier))
                .getCode(), "la confirmation d'un infirmier ne suffit pas un jour de fermeture");

        var result = f.service().scanAndValidate(f.centerId(), f.patientId().toString(), "admin",
                new DerogationPlanning(MotifHorsPlanning.URGENCE, null, true));
        assertTrue(result.seance().isHorsPlanning());
    }

    @Test
    void scanAndValidate_should_not_check_the_planning_again_for_an_existing_session() {
        ScanFixture f = scanFixture(JOUR_NON_DIALYSE);
        UUID id = UUID.randomUUID();
        f.seances().save(new Seance(id, f.patientId(), f.centerId().value(), LocalDate.now()));

        var result = f.service().scanAndValidate(f.centerId(), f.patientId().toString(), "inf-01", null);

        assertEquals(id, result.seance().getId());
        assertFalse(result.seance().isHorsPlanning());
    }

    @Test
    void createFromQr_should_never_create_an_out_of_planning_session_for_the_secretary() {
        ScanFixture f = scanFixture(JOUR_NON_DIALYSE);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> f.service().createFromQr(f.centerId(), f.patientId().toString()));

        assertEquals("SEANCE_HORS_PLANNING_JOUR", ex.getCode());
    }

    @Test
    void a_motive_other_requires_a_precision() {
        assertEquals("SEANCE_DEROGATION_PRECISION_REQUISE", assertThrows(BusinessException.class,
                () -> new DerogationPlanning(MotifHorsPlanning.AUTRE, "  ", false)).getCode());
        assertEquals("SEANCE_DEROGATION_MOTIF_REQUIS", assertThrows(BusinessException.class,
                () -> new DerogationPlanning(null, null, false)).getCode());
        assertEquals("transfert exceptionnel",
                new DerogationPlanning(MotifHorsPlanning.AUTRE, " transfert exceptionnel ", false).precision());
    }

    @Test
    void scanAndValidate_should_refuse_a_patient_of_another_center() {
        ScanFixture f = scanFixture();
        CenterId autre = CenterId.of(UUID.randomUUID());

        assertThrows(IllegalArgumentException.class,
                () -> f.service().scanAndValidate(autre, f.patientId().toString(), "inf-01", null));
    }

    private record ScanFixture(CenterId centerId, UUID patientId, InMemorySeanceRepository seances,
                               SpyBonSortieUseCase bonSortie, SeanceDomainService service) {
    }

    @Test
    void search_should_bound_the_page_and_the_size_and_forward_the_criteria() {
        CenterId centerId = CenterId.of(UUID.randomUUID());
        InMemorySeanceRepository seanceRepo = new InMemorySeanceRepository();
        SeanceDomainService service = buildService(seanceRepo, new InMemoryPatientRepository(UUID.randomUUID(), centerId),
                new InMemoryArticleRepository(), new InMemoryLotRepository(), new SpyBonSortieUseCase());
        SeanceSearch criteria = new SeanceSearch(LocalDate.of(2026, 10, 1), null,
                java.util.Set.of(SeanceStatus.VALIDEE), " dupont ", SeanceSearch.Sort.PATIENT, false);

        service.search(centerId, criteria, -3, 100000);

        assertEquals(0, seanceRepo.lastPage);
        assertEquals(com.hemodialyse.backend.domain.seance.port.SeanceUseCase.SEARCH_MAX_SIZE, seanceRepo.lastSize);
        assertEquals("dupont", seanceRepo.lastSearch.text(), "le texte est nettoyé");
        assertEquals(SeanceSearch.Sort.PATIENT, seanceRepo.lastSearch.sort());

        service.search(centerId, null, 0, 0);
        assertEquals(1, seanceRepo.lastSize);
        assertEquals(SeanceSearch.Sort.DATE, seanceRepo.lastSearch.sort());
        assertTrue(seanceRepo.lastSearch.desc());
    }

    @Test
    void search_should_refuse_a_period_that_ends_before_it_starts() {
        CenterId centerId = CenterId.of(UUID.randomUUID());
        SeanceDomainService service = buildService(new InMemorySeanceRepository(),
                new InMemoryPatientRepository(UUID.randomUUID(), centerId), new InMemoryArticleRepository(),
                new InMemoryLotRepository(), new SpyBonSortieUseCase());

        assertThrows(IllegalArgumentException.class, () -> service.search(centerId,
                new SeanceSearch(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 1), java.util.Set.of(), null, null, true),
                0, 20));
    }

    @Test
    void sort_parsing_should_only_accept_whitelisted_columns() {
        assertEquals(SeanceSearch.Sort.PATIENT, SeanceSearch.Sort.parse("patient"));
        assertEquals(SeanceSearch.Sort.CODE, SeanceSearch.Sort.parse("patientCode"));
        assertEquals(SeanceSearch.Sort.STATUS, SeanceSearch.Sort.parse("status"));
        assertEquals(SeanceSearch.Sort.CREATED, SeanceSearch.Sort.parse("createdAt"));
        assertEquals(SeanceSearch.Sort.DATE, SeanceSearch.Sort.parse("password; drop table"));
        assertEquals(SeanceSearch.Sort.DATE, SeanceSearch.Sort.parse(null));
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

    // SE3 (ADR 0090) : les consommables déclarés (ajout, modification, retrait) sont tenus par les tests de contexts/seance.

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

    // SE5 (ADR 0092) : l'imposition d'un forfait est testée dans contexts/seance (ForfaitImposeALaSeanceTest, ImposerUnForfaitTest).

    // ── In-memory stubs ──────────────────────────────────────────────

    private static final class InMemorySeanceRepository implements SeanceRepositoryPort {
        @Override
        public void delete(CenterId centerId, UUID seanceId) {
            throw new UnsupportedOperationException();
        }

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

        SeanceSearch lastSearch;
        int lastPage;
        int lastSize;

        @Override
        public PagedResult<SeanceListItem> search(CenterId centerId, SeanceSearch criteria, int page, int size) {
            lastSearch = criteria;
            lastPage = page;
            lastSize = size;
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
    }


    private static final class InMemoryArticleRepository implements CatalogueDArticles {
        private final Map<UUID, ArticleDuCatalogue> data = new HashMap<>();

        void addArticle(UUID id, UUID centerId, String code, BigDecimal stock) {
            data.put(id, new ArticleDuCatalogue(id, centerId, code, "Article " + code, "pce", true, null, null, null, false,
                    null, null));
        }

        void addArticleInactif(UUID id, UUID centerId, String code) {
            data.put(id, new ArticleDuCatalogue(id, centerId, code, "Article " + code, "pce", false, null, null, null, false,
                    null, null));
        }

        @Override
        public Optional<ArticleDuCatalogue> article(UUID centre, UUID article) {
            return Optional.ofNullable(data.get(article)).filter(a -> a.centre().equals(centre));
        }

        @Override
        public List<ArticleDuCatalogue> articles(UUID centre) {
            return data.values().stream().filter(a -> a.centre().equals(centre)).toList();
        }

        @Override
        public List<ArticleDuCatalogue> articlesDeTraitement(UUID centre, String typeTraitementAnemie) {
            return List.of();
        }

        @Override
        public BigDecimal quantitePourDose(UUID centre, UUID article, BigDecimal dose, String uniteDose) {
            return dose;
        }
    }

    private static final class InMemoryLotRepository {
        // Bouchon : la Seance ne lit plus les lots (le choix des lots est l'affaire du Stock, ADR 0045).
        void addLot(UUID articleId, UUID centerId, UUID lotId, BigDecimal qty, LocalDate expiry) {
        }
    }

    /** Bouchon : le service ne lit plus le référentiel des forfaits (SE5, ADR 0092 : l'imposition vit dans contexts/seance). */
    private static final class InMemoryForfaitCatalog {
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

    private static final class SpyBonSortieUseCase implements ConsommationsDeSeance {
        List<Item> lastItems = List.of();
        boolean called = false;

        @Override
        public void enregistrerALaValidation(UUID centre, UUID seance, UUID patient, LocalDate dateSeance, List<Ligne> lignes,
                                             String auteur) {
            called = true;
            lastItems = lignes.stream().map(l -> new Item(l.article(), l.quantite())).toList();
        }
        boolean setCalled = false;
        UUID lastSetArticleId = null;
        BigDecimal lastSetQuantite = null;
        boolean addCalled = false;
        UUID lastAddedArticleId = null;
        BigDecimal lastAddedQuantite = null;

        @Override
        public void fixer(UUID centre, UUID seance, UUID patient, LocalDate dateSeance, UUID article, BigDecimal quantite,
                          String auteur) {
            setCalled = true;
            lastSetArticleId = article;
            lastSetQuantite = quantite;
        }

        @Override
        public void ajouter(UUID centre, UUID seance, UUID patient, LocalDate dateSeance, UUID article, BigDecimal quantite,
                            String auteur) {
            addCalled = true;
            lastAddedArticleId = article;
            lastAddedQuantite = quantite;
        }

        @Override
        public void annuler(UUID centre, UUID seance, String auteur) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void administrer(UUID centre, UUID seance, UUID patient, LocalDate jour, UUID article, BigDecimal quantite,
                                String auteur) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<com.hemodialyse.backend.contexts.stock.publishedlanguage.ConsommationsDeSeance.LigneHistorique> historique(
                UUID centre, UUID seance) {
            return List.of();
        }

        @Override
        public List<com.hemodialyse.backend.contexts.stock.publishedlanguage.ConsommationsDeSeance.LigneHistorique> sortiesHistoriques(
                UUID centre, UUID seance) {
            return List.of();
        }

        @Override
        public List<com.hemodialyse.backend.contexts.stock.publishedlanguage.ConsommationsDeSeance.SortieParDate> sortiesParDate(
                UUID centre, LocalDate date) {
            return List.of();
        }

        /**
         * Consommable relevé : article et quantité (le choix des lots est l'affaire du Stock).
         */
        record Item(UUID articleId, BigDecimal quantite) {
        }
    }
}