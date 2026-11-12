package com.hemodialyse.backend.infrastructure.web.rest;

import com.hemodialyse.backend.contexts.pilotagedirection.application.ConsulterLesAbsencesDesCentres;
import com.hemodialyse.backend.contexts.pilotagedirection.application.ConsulterLesAbsencesDesCentres.AbsencesOverview;
import com.hemodialyse.backend.contexts.pilotagedirection.application.ConsulterLesAbsencesDesCentres.CentreAbsences;
import com.hemodialyse.backend.contexts.absencespatients.application.DetectionAbsences;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.absencespatients.domain.PeriodeDeDetection;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.RegleDuPilotageViolee;
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
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Lectures des absences de patients par les autres écrans : tableau de bord mensuel des séances, agrégats de la direction et
 * isolation par rôle. Les absences sont créées par la détection du contexte absences-patients ; la détection elle-même est
 * éprouvée par le filet de caractérisation d'A3 et les tests du module.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class AbsencePatientIntegrationTest {

    private static final UUID SOC = UUID.fromString("99995000-0000-0000-0000-000000000001");
    private static final UUID SOC_AUTRE = UUID.fromString("99995000-0000-0000-0000-000000000002");
    private static final UUID C1 = UUID.fromString("99995000-0000-0000-0000-0000000000c1");
    private static final UUID C2 = UUID.fromString("99995000-0000-0000-0000-0000000000c2");
    private static final UUID C3 = UUID.fromString("99995000-0000-0000-0000-0000000000c3");
    private final LocalDate jour = LocalDate.now().minusDays(5);
    @Autowired
    private WebApplicationContext context;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private DetectionAbsences detection;
    @Autowired
    private com.hemodialyse.backend.contexts.seance.application.ConsulterLeTableauDeBord tableau;
    @Autowired
    private com.hemodialyse.backend.contexts.seance.application.ConsulterLeDetailDuTableauDeBord detail;
    @Autowired
    private ConsulterLesAbsencesDesCentres direction;
    @Autowired
    private RoleScopeFilter roleScopeFilter;
    private UUID absent;
    private UUID present;

    /** Détecte les absences du jour pour ce centre (le contrôle de nuit, sans la notification). */
    private void detecter(UUID centre, LocalDate date) {
        detection.executer(new CentreId(centre), new PeriodeDeDetection(date, date, false), Instant.now());
    }

    private static void connecteEnAdminDuCentre(UUID centre) {
        UserPrincipal p = UserPrincipal.create(UUID.randomUUID().toString(), centre.toString(), "admin", "",
                List.of("ADMIN"), true);
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(p, null,
                        p.getAuthorities()));
    }

    @org.junit.jupiter.api.AfterEach
    void deconnecte() {
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }

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
    void the_monthly_dashboard_counts_the_tracked_absences_and_the_performed_sessions() {
        // KB-ABS-05 : le tableau de bord est gardé par le centre de la session ; l'administrateur du centre 1 est connecté
        connecteEnAdminDuCentre(C1);
        detecter(C1, jour);   // le patient sans séance réalisée est absent
        seance(C1, absent, jour, "CREE");   // une séance seulement créée n'est pas une présence
        jdbc.update("INSERT INTO absence_patient (id, center_id, patient_id, date_seance, source, statut, prix_ttc, "
                        + "taux_tva, montant_ht) VALUES (?,?,?,?,?,?,?,?,?)", UUID.randomUUID(), C1, present,
                Date.valueOf(jour), "DECLAREE", "ANNULEE", 0, 0, 0);
        int annee = jour.getYear();
        int mois = jour.getMonthValue();

        var c1 = tableau.executer(C1, annee, mois);

        assertEquals(1L, c1.presences(), "seule la séance validée est une présence");
        assertEquals(1L, c1.absences(), "l'absence suivie compte, l'absence annulée non");
        final int an = annee;
        final int mo = mois;
        assertThrows(com.hemodialyse.backend.contexts.seance.application.AccesRefuse.class,
                () -> tableau.executer(C2, an, mo), "le centre 2 n'est pas celui de la session");
        connecteEnAdminDuCentre(C2);
        var c2 = tableau.executer(C2, annee, mois);
        assertEquals(0L, c2.absences(), "isolation par centre");
        connecteEnAdminDuCentre(C1);
        var absences = detail.executer(C1, annee, mois, "absence");
        var presences = detail.executer(C1, annee, mois, "presence");
        assertEquals(1, absences.lignes().size());
        assertEquals("A_QUALIFIER", absences.lignes().getFirst().statut());
        assertEquals(1, presences.lignes().size());
    }

    @Test
    void the_direction_overview_aggregates_per_centre_with_valuation_share_of_revenue_and_anonymity() {
        detecter(C1, jour);
        UUID autre = patient(C3);
        jdbc.update("INSERT INTO absence_patient (id, center_id, patient_id, date_seance, source, statut, prix_ttc, "
                        + "taux_tva, montant_ht) VALUES (?,?,?,?,?,?,?,?,?)", UUID.randomUUID(), C3, autre,
                Date.valueOf(jour), "AUTOMATIQUE", "A_QUALIFIER", 5000, 0, 5000);
        today(C1, "100000.00");

        AbsencesOverview o = direction.execute(SOC, jour.minusDays(1), LocalDate.now());

        assertEquals(2, o.centres().size());
        CentreAbsences c1 = o.centres().stream().filter(c -> c.centerId().equals(C1)).findFirst().orElseThrow();
        // KB-ABS-07 : une seule absence comptabilisée -> tous les chiffres qui en dépendent sont masqués
        assertNull(c1.stats().nbAbsences());
        assertNull(c1.stats().nbJustifiees());
        assertNull(c1.stats().nbAQualifier());
        assertEquals(1, c1.stats().nbSeances());
        assertNull(c1.stats().valorisationHt());
        assertNull(c1.stats().valorisationTtc());
        assertNull(c1.stats().partCaHt());
        assertNull(c1.stats().tauxAbsenteisme());
        // « masquer par centre seulement » : le centre masqué est exclu du total, des motifs et du mensuel (pas de différence)
        assertEquals(1, o.centresMasques());
        assertEquals(0L, o.total().nbAbsences(), "le seul centre est masqué : le total ne porte que sur les centres publiés");
        assertEquals(0, o.total().nbSeances());
        assertTrue(o.motifs().isEmpty());
        assertTrue(o.mensuel().isEmpty());
        assertTrue(o.centres().stream().noneMatch(c -> c.centerId().equals(C3)));
    }

    @Test
    void the_direction_overview_rejects_an_invalid_period() {
        assertThrows(RegleDuPilotageViolee.class, () -> direction.execute(SOC, LocalDate.now(), LocalDate.now().minusDays(1)));
        assertThrows(RegleDuPilotageViolee.class,
                () -> direction.execute(SOC, LocalDate.now().minusYears(6), LocalDate.now()));
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
