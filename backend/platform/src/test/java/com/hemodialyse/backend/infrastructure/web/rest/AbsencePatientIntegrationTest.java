package com.hemodialyse.backend.infrastructure.web.rest;

import com.hemodialyse.backend.application.absence.AbsencePatientService;
import com.hemodialyse.backend.application.direction.DirectionAbsencesQueryService;
import com.hemodialyse.backend.application.direction.DirectionAbsencesQueryService.AbsencesOverview;
import com.hemodialyse.backend.application.direction.DirectionAbsencesQueryService.CentreAbsences;
import com.hemodialyse.backend.domain.absence.model.AbsenceFiltre;
import com.hemodialyse.backend.domain.absence.model.AbsenceLigne;
import com.hemodialyse.backend.domain.absence.model.AbsencePatient;
import com.hemodialyse.backend.domain.absence.model.MotifAbsence;
import com.hemodialyse.backend.domain.absence.model.StatutAbsence;
import com.hemodialyse.backend.domain.shared.exception.BusinessException;
import com.hemodialyse.backend.infrastructure.security.RoleScopeFilter;
import com.hemodialyse.backend.infrastructure.security.UserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Suivi des absences de patients : détection, valorisation (forfait de la prise en charge en TTC converti en HT avec la
 * TVA du type « HEMODIALYSE »), règles de cycle de vie, isolation entre centres et agrégats de la direction.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class AbsencePatientIntegrationTest {

    private static final UUID SOC = UUID.fromString("99995000-0000-0000-0000-000000000001");
    private static final UUID SOC_AUTRE = UUID.fromString("99995000-0000-0000-0000-000000000002");
    private static final UUID C1 = UUID.fromString("99995000-0000-0000-0000-0000000000c1");
    private static final UUID C2 = UUID.fromString("99995000-0000-0000-0000-0000000000c2");
    private static final UUID C3 = UUID.fromString("99995000-0000-0000-0000-0000000000c3");
    private static final UUID USER = UUID.randomUUID();
    private final LocalDate jour = LocalDate.now().minusDays(5);
    @Autowired
    private WebApplicationContext context;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private AbsencePatientService service;
    @Autowired
    private DirectionAbsencesQueryService direction;
    @Autowired
    private RoleScopeFilter roleScopeFilter;
    private UUID absent;
    private UUID present;

    private static UserPrincipal principal(String role) {
        return UserPrincipal.create(UUID.randomUUID().toString(), UUID.randomUUID().toString(), "u-" + role, "",
                List.of(role), true);
    }

    @BeforeEach
    void setup() {
        cleanup();
        jdbc.update("INSERT INTO societes (id, code, raison_sociale, actif, created_at) VALUES (?,?,?,TRUE,CURRENT_TIMESTAMP)",
                SOC, "ZT-AB1", "Société AB1");
        jdbc.update("INSERT INTO societes (id, code, raison_sociale, actif, created_at) VALUES (?,?,?,TRUE,CURRENT_TIMESTAMP)",
                SOC_AUTRE, "ZT-AB2", "Société AB2");
        centre(C1, "ZT-AB1-A", "Centre 1", SOC);
        centre(C2, "ZT-AB1-B", "Centre 2", SOC);
        centre(C3, "ZT-AB2-A", "Centre 3", SOC_AUTRE);
        absent = patient(C1);
        present = patient(C1);

        UUID forfait = UUID.randomUUID();
        jdbc.update("INSERT INTO forfait (id, center_id, code, libelle, prix) VALUES (?,?,?,?,?)", forfait, C1,
                "ZT-F", "Forfait test", new BigDecimal("10000.00"));
        jdbc.update("INSERT INTO prise_en_charge (id, patient_id, center_id, date_debut_effectif, forfait_effectif_id, "
                        + "statut, created_at) VALUES (?,?,?,?,?,?,CURRENT_TIMESTAMP)", UUID.randomUUID(), absent, C1,
                Date.valueOf("2026-01-01"), forfait, "VALIDEE");
        jdbc.update("INSERT INTO tva_types (id, center_id, libelle, taux, type_prestation, exonere, date_debut_validite, actif) "
                        + "VALUES (?,?,?,?,?,FALSE,?,TRUE)", UUID.randomUUID(), C1, "TVA 19", new BigDecimal("19.00"),
                "HEMODIALYSE", Date.valueOf("2020-01-01"));
        seance(C1, present, jour, "VALIDEE");
    }

    @AfterEach
    void cleanup() {
        for (String table : List.of("absence_patient", "factures", "seances", "prise_en_charge", "forfait", "tva_types",
                "patients")) {
            jdbc.update("DELETE FROM " + table + " WHERE center_id IN (?, ?, ?)", C1, C2, C3);
        }
        jdbc.update("DELETE FROM centers WHERE code LIKE 'ZT-AB%'");
        jdbc.update("DELETE FROM societes WHERE code LIKE 'ZT-AB%'");
    }

    @Test
    void detects_each_scheduled_patient_without_session_once_and_values_the_loss() {
        assertEquals(1, service.detecter(C1, jour), "seul le patient sans séance réalisée est absent");
        assertEquals(0, service.detecter(C1, jour), "détection idempotente");
        assertEquals(0, service.detecter(C2, jour), "autre centre : aucun patient");

        AbsenceLigne ligne = service.lister(C1, AbsenceFiltre.aucun(), 0, 20).items().get(0);
        AbsencePatient a = ligne.absence();
        assertEquals(absent, a.patientId());
        assertEquals(StatutAbsence.A_QUALIFIER, a.statut());
        assertEquals(new BigDecimal("11900.00"), a.valeur().prixTtc());
        assertEquals(new BigDecimal("19.00"), a.valeur().tauxTva());
        assertEquals(new BigDecimal("10000.00"), a.valeur().montantHt());
        assertEquals("Forfait test", a.valeur().forfaitLibelle());
        assertNotNull(ligne.patientNom());
        assertEquals(0, service.lister(C2, AbsenceFiltre.aucun(), 0, 20).total(), "isolation par centre");
    }

    @Test
    void a_validated_billing_period_closes_the_absences_of_that_patient() {
        service.detecter(C1, jour);
        UUID id = service.lister(C1, AbsenceFiltre.aucun(), 0, 20).items().get(0).absence().id();
        Date debut = Date.valueOf(jour.minusDays(10));
        Date fin = Date.valueOf(jour.plusDays(1));
        jdbc.update("INSERT INTO factures (id, center_id, patient_id, numero_facture, period_start, period_end, "
                        + "date_facturation, tva_rate, total_ht, total_tva, total_ttc) VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                UUID.randomUUID(), C1, absent, "ZT-AB-CLOT", debut, fin, fin, new BigDecimal("19.00"),
                BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ONE);

        assertEquals("ABSENCE_PERIODE_FACTUREE", assertThrows(BusinessException.class,
                () -> service.qualifier(C1, id, MotifAbsence.MALADIE, null, USER, true)).getCode());
        assertEquals("ABSENCE_PERIODE_FACTUREE", assertThrows(BusinessException.class,
                () -> service.annuler(C1, id, "Erreur", USER, true)).getCode());
        assertEquals("ABSENCE_PERIODE_FACTUREE", assertThrows(BusinessException.class,
                () -> service.declarer(C1, absent, jour.minusDays(1), null, null, USER)).getCode());
        service.detecter(C1, jour.minusDays(2)); // l'autre patient (non facturé) est détecté, pas celui-ci
        assertEquals(0, service.lister(C1, new AbsenceFiltre(null, null, absent, jour.minusDays(2), jour.minusDays(2)),
                0, 20).total(), "pas de nouvelle absence dans une période clôturée");
    }

    @Test
    void a_closed_day_produces_no_absence() {
        jdbc.update("INSERT INTO center_holiday (id, center_id, day_date, label) VALUES (?,?,?,?)", UUID.randomUUID(), C1,
                Date.valueOf(jour), "Férié test");
        try {
            assertEquals(0, service.detecter(C1, jour));
        } finally {
            jdbc.update("DELETE FROM center_holiday WHERE center_id = ?", C1);
        }
    }

    @Test
    void declaring_is_refused_for_a_duplicate_a_present_patient_or_an_unknown_patient() {
        service.detecter(C1, jour);
        assertEquals("ABSENCE_EXISTANTE", assertThrows(BusinessException.class,
                () -> service.declarer(C1, absent, jour, null, null, USER)).getCode());
        assertEquals("ABSENCE_SEANCE_REALISEE", assertThrows(BusinessException.class,
                () -> service.declarer(C1, present, jour, null, null, USER)).getCode());
        assertEquals("PATIENT_INTROUVABLE", assertThrows(BusinessException.class,
                () -> service.declarer(C1, UUID.randomUUID(), jour, null, null, USER)).getCode());
        assertEquals("PATIENT_INTROUVABLE", assertThrows(BusinessException.class,
                () -> service.declarer(C2, absent, jour, null, null, USER)).getCode(), "patient d'un autre centre");
    }

    @Test
    void a_declared_absence_with_a_reason_is_qualified_and_its_correction_is_restricted() {
        LocalDate hier = LocalDate.now().minusDays(1);
        AbsencePatient a = service.declarer(C1, absent, hier, MotifAbsence.HOSPITALISATION, null, USER);
        assertEquals(StatutAbsence.JUSTIFIEE, a.statut());

        assertEquals("ABSENCE_CORRECTION_INTERDITE", assertThrows(BusinessException.class,
                () -> service.qualifier(C1, a.id(), MotifAbsence.NON_JUSTIFIEE, "Contrôle", USER, false)).getCode());
        AbsencePatient corrigee = service.qualifier(C1, a.id(), MotifAbsence.NON_JUSTIFIEE, "Contrôle", USER, true);
        assertEquals(StatutAbsence.NON_JUSTIFIEE, corrigee.statut());
        assertEquals(StatutAbsence.NON_JUSTIFIEE,
                service.lister(C1, new AbsenceFiltre(null, MotifAbsence.NON_JUSTIFIEE, null, null, null), 0, 20)
                        .items().get(0).absence().statut());
        assertEquals("ABSENCE_INTROUVABLE", assertThrows(BusinessException.class,
                        () -> service.qualifier(C2, a.id(), MotifAbsence.MALADIE, null, USER, true)).getCode(),
                "une absence d'un autre centre est introuvable");
    }

    @Test
    void a_make_up_session_cancels_the_loss_only_when_a_session_exists() {
        service.detecter(C1, jour);
        UUID id = service.lister(C1, AbsenceFiltre.aucun(), 0, 20).items().get(0).absence().id();
        LocalDate rattrapage = jour.plusDays(2);

        assertEquals("ABSENCE_RATTRAPAGE_SANS_SEANCE", assertThrows(BusinessException.class,
                () -> service.rattraper(C1, id, rattrapage, USER)).getCode());
        seance(C1, absent, rattrapage, "VALIDEE");
        assertEquals(StatutAbsence.RATTRAPEE, service.rattraper(C1, id, rattrapage, USER).statut());
        assertEquals("ABSENCE_NON_MODIFIABLE", assertThrows(BusinessException.class,
                () -> service.qualifier(C1, id, MotifAbsence.MALADIE, "x", USER, true)).getCode());
    }

    @Test
    void a_session_entered_late_cancels_the_detected_absence() {
        service.detecter(C1, jour);
        seance(C1, absent, jour, "SIGNEE");

        assertEquals(1, service.reconcilier(C1, jour.minusDays(1), jour.plusDays(1)));

        AbsencePatient a = service.lister(C1, AbsenceFiltre.aucun(), 0, 20).items().get(0).absence();
        assertEquals(StatutAbsence.ANNULEE, a.statut());
        assertEquals(0, service.synthese(C1).aQualifier());
    }

    @Test
    void the_summary_counts_pending_and_late_absences() {
        service.detecter(C1, jour); // 5 jours : en retard (> 3 jours)
        AbsencePatientService.Synthese s = service.synthese(C1);
        assertEquals(1, s.aQualifier());
        assertEquals(1, s.enRetard());
        assertEquals(0, service.synthese(C2).aQualifier());
    }

    @Test
    void the_direction_overview_aggregates_per_centre_with_valuation_share_of_revenue_and_anonymity() {
        service.detecter(C1, jour);
        UUID autre = patient(C3);
        jdbc.update("INSERT INTO absence_patient (id, center_id, patient_id, date_seance, source, statut, prix_ttc, "
                        + "taux_tva, montant_ht) VALUES (?,?,?,?,?,?,?,?,?)", UUID.randomUUID(), C3, autre,
                Date.valueOf(jour), "AUTOMATIQUE", "A_QUALIFIER", 5000, 0, 5000);
        today(C1, "100000.00");

        AbsencesOverview o = direction.absences(SOC, jour.minusDays(1), LocalDate.now());

        assertEquals(2, o.centres().size());
        CentreAbsences c1 = o.centres().stream().filter(c -> c.centerId().equals(C1)).findFirst().orElseThrow();
        assertEquals(1, c1.stats().nbAbsences());
        assertEquals(1, c1.stats().nbSeances());
        assertEquals(0, new BigDecimal("10000.00").compareTo(c1.stats().valorisationHt()));
        assertEquals(0, new BigDecimal("11900.00").compareTo(c1.stats().valorisationTtc()));
        assertEquals(0, new BigDecimal("10.00").compareTo(c1.stats().partCaHt()), "10 000 / 100 000 de CA HT");
        assertEquals(0, new BigDecimal("50.00").compareTo(c1.stats().tauxAbsenteisme()), "1 absence / (1 + 1 séance)");
        assertEquals(1, o.total().nbAbsences(), "l'autre société est exclue");
        assertNull(o.motifs().get(0).nb(), "un seul cas : effectif masqué");
        assertNull(o.motifs().get(0).valorisationHt(), "la valorisation d'un effectif masqué l'est aussi");
        assertEquals("NON_QUALIFIE", o.motifs().get(0).motif());
        assertNull(o.mensuel().get(0).nbAbsences());
        assertTrue(o.centres().stream().noneMatch(c -> c.centerId().equals(C3)));
    }

    @Test
    void the_direction_overview_rejects_an_invalid_period() {
        assertThrows(BusinessException.class, () -> direction.absences(SOC, LocalDate.now(), LocalDate.now().minusDays(1)));
        assertThrows(BusinessException.class,
                () -> direction.absences(SOC, LocalDate.now().minusYears(6), LocalDate.now()));
    }

    @Test
    void the_scope_filter_keeps_direction_and_superadmin_away_from_patient_absences() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity())
                .addFilters(roleScopeFilter).build();
        for (String role : List.of("DIRECTION", "SUPERADMIN")) {
            mockMvc.perform(get("/api/v1/absences-patients").with(user(principal(role))))
                    .andExpect(status().isForbidden());
        }
    }

    private void centre(UUID id, String code, String name, UUID societe) {
        jdbc.update("INSERT INTO centers (id, code, name, societe_id, actif) VALUES (?,?,?,?,TRUE)", id, code, name, societe);
    }

    private UUID patient(UUID centre) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO patients (id, center_id, code_patient, nom, prenom, sexe, date_naissance, numero_assurance, "
                        + "date_admission, type_patient, etat_patient, qualite_assure, sous_kt, epo_enabled, created_at) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,FALSE,CURRENT_TIMESTAMP)",
                id, centre, "ZT-" + id.toString().substring(0, 8), "NOM", "Prenom", "M", Date.valueOf("1970-05-05"),
                "ASS-" + id.toString().substring(0, 8), Date.valueOf("2026-01-02"), "NON_VACANCIER", "PERMANENT",
                "ASSURE", false);
        for (String colonne : List.of("jour_lundi", "jour_mardi", "jour_mercredi", "jour_jeudi", "jour_vendredi",
                "jour_samedi", "jour_dimanche")) {
            jdbc.update("UPDATE patients SET " + colonne + " = TRUE WHERE id = ?", id);
        }
        return id;
    }

    private void seance(UUID centre, UUID patient, LocalDate date, String statut) {
        jdbc.update("INSERT INTO seances (id, patient_id, center_id, date_seance, statut, created_at) VALUES (?,?,?,?,?,CURRENT_TIMESTAMP)",
                UUID.randomUUID(), patient, centre, Date.valueOf(date), statut);
    }

    private void today(UUID centre, String ht) {
        Date today = Date.valueOf(LocalDate.now());
        jdbc.update("INSERT INTO factures (id, center_id, patient_id, numero_facture, period_start, period_end, date_facturation, "
                        + "tva_rate, total_ht, total_tva, total_ttc) VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                UUID.randomUUID(), centre, present, "ZT-AB-F1", today, today, today, new BigDecimal("19.00"),
                new BigDecimal(ht), BigDecimal.ZERO, new BigDecimal(ht));
    }
}
