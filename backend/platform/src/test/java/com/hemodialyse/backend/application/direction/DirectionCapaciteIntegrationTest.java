package com.hemodialyse.backend.application.direction;

import com.hemodialyse.backend.application.direction.DirectionCapaciteQueryService.CapaciteOverview;
import com.hemodialyse.backend.application.direction.DirectionCapaciteQueryService.CentreCapacite;
import com.hemodialyse.backend.domain.planning.service.CapaciteTheoriqueCalculator.Niveau;
import com.hemodialyse.backend.domain.shared.exception.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Capacité théorique de la société sur une période : 16 générateurs sur 2 séries = 84 patients ; la file active est
 * celle des patients ayant eu une séance réalisée pendant la période ; le nombre de patients par poste et par série est
 * paramétrable par centre ; une file active faible est masquée ; aucune donnée d'une autre société.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class DirectionCapaciteIntegrationTest {

    private static final UUID SOC = UUID.fromString("99996000-0000-0000-0000-000000000001");
    private static final UUID SOC_AUTRE = UUID.fromString("99996000-0000-0000-0000-000000000002");
    private static final UUID GRAND = UUID.fromString("99996000-0000-0000-0000-0000000000a1");
    private static final UUID PETIT = UUID.fromString("99996000-0000-0000-0000-0000000000a2");
    private static final UUID AUTRE = UUID.fromString("99996000-0000-0000-0000-0000000000a3");

    private final LocalDate aujourdhui = LocalDate.now(ZoneOffset.UTC);
    private final LocalDate debut = aujourdhui.minusDays(30);

    @Autowired
    private DirectionCapaciteQueryService service;
    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void seed() {
        cleanup();
        societe(SOC, "ZT-CP1");
        societe(SOC_AUTRE, "ZT-CP2");
        centre(GRAND, "ZT-CP1-A", "Grand centre", SOC);
        centre(PETIT, "ZT-CP1-B", "Petit centre", SOC);
        centre(AUTRE, "ZT-CP2-A", "Autre société", SOC_AUTRE);

        // Grand centre : 16 générateurs (+ 1 réformé ignoré), 2 séries, 6 patients ayant eu une séance validée
        for (int i = 0; i < 16; i++)
            generateur(GRAND, "CP-G" + i, "EN_SERVICE", OffsetDateTime.now(ZoneOffset.UTC).minusYears(1));
        generateur(GRAND, "CP-GR", "REFORME", OffsetDateTime.now(ZoneOffset.UTC).minusYears(1));
        creneau(GRAND, "M", "Matin");
        creneau(GRAND, "A", "Après-midi");
        for (int i = 0; i < 6; i++) seance(patient(GRAND), GRAND, debut.plusDays(2 + i), "VALIDEE");
        seance(patient(GRAND), GRAND, debut.plusDays(3), "CREE");            // pas réalisée : ne compte pas
        seance(patient(GRAND), GRAND, debut.minusDays(10), "VALIDEE");        // avant la période
        UUID deuxSeances = patient(GRAND);
        seance(deuxSeances, GRAND, debut.plusDays(4), "FACTUREE");
        seance(deuxSeances, GRAND, debut.plusDays(6), "SIGNEE");              // un même patient compte une fois

        // Petit centre : 8 générateurs, 1 série, 2 patients par poste et par série (paramètre), 2 patients actifs
        for (int i = 0; i < 8; i++)
            generateur(PETIT, "CP-P" + i, "EN_SERVICE", OffsetDateTime.now(ZoneOffset.UTC).minusYears(1));
        creneau(PETIT, "M", "Matin");
        jdbc.update("INSERT INTO planning_parametres (center_id, jours_ouverts, patients_par_infirmier, "
                        + "patients_par_poste_serie, updated_at) VALUES (?, 'LUNDI', 4, 2, ?)", PETIT,
                OffsetDateTime.now(ZoneOffset.UTC));
        seance(patient(PETIT), PETIT, debut.plusDays(5), "VALIDEE");
        seance(patient(PETIT), PETIT, debut.plusDays(6), "VALIDEE");

        generateur(AUTRE, "CP-X", "EN_SERVICE", OffsetDateTime.now(ZoneOffset.UTC).minusYears(1));
    }

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM seances WHERE center_id IN (?, ?, ?)", GRAND, PETIT, AUTRE);
        jdbc.update("DELETE FROM patients WHERE center_id IN (?, ?, ?)", GRAND, PETIT, AUTRE);
        jdbc.update("DELETE FROM planning_parametres WHERE center_id IN (?, ?, ?)", GRAND, PETIT, AUTRE);
        jdbc.update("DELETE FROM position_creneau WHERE center_id IN (?, ?, ?)", GRAND, PETIT, AUTRE);
        jdbc.update("DELETE FROM gmao_equipements WHERE centre_id IN (?, ?, ?)", GRAND, PETIT, AUTRE);
        jdbc.update("DELETE FROM centers WHERE code LIKE 'ZT-CP%'");
        jdbc.update("DELETE FROM societes WHERE code LIKE 'ZT-CP%'");
    }

    private CentreCapacite centre(CapaciteOverview o, UUID id) {
        return o.centres().stream().filter(c -> c.centerId().equals(id)).findFirst().orElseThrow();
    }

    @Test
    void computes_the_theoretical_capacity_per_centre_for_the_selected_period() {
        CapaciteOverview o = service.capacite(SOC, debut, aujourdhui);

        assertEquals(debut, o.from());
        assertEquals(aujourdhui, o.to());
        assertEquals(2, o.centres().size());
        CentreCapacite grand = centre(o, GRAND);
        assertEquals(16, grand.capacite().generateurs(), "le générateur réformé n'est pas compté");
        assertEquals(2, grand.capacite().generateursSecours());
        assertEquals(14, grand.capacite().postesActifs());
        assertEquals(2, grand.capacite().series());
        assertEquals(3, grand.capacite().patientsParPosteEtSerie());
        assertEquals(84, grand.capacite().capacite());
        assertEquals(7L, grand.capacite().fileActive(), "6 patients + 1 avec deux séances ; hors CREE et hors période");
        assertEquals(0, grand.capacite().tauxOccupation().compareTo(new BigDecimal("8.3")));
        assertEquals(Niveau.MARGE, grand.capacite().niveau());
        assertFalse(grand.capacite().atteinte());

        CentreCapacite petit = centre(o, PETIT);
        assertEquals(2, petit.capacite().patientsParPosteEtSerie(), "paramètre du centre");
        assertEquals(7, petit.capacite().postesActifs());
        assertEquals(14, petit.capacite().capacite(), "7 postes × 1 série × 2 patients");

        assertEquals(24, o.total().generateurs());
        assertEquals(98, o.total().capacite(), "84 + 14");
        assertEquals(9L, o.total().fileActive());
        assertEquals(8, o.regle().generateursParSecours());
    }

    @Test
    void the_active_file_follows_the_selected_period() {
        CapaciteOverview ancienne = service.capacite(SOC, debut.minusDays(20), debut.minusDays(5));

        assertNull(centre(ancienne, GRAND).capacite().fileActive(), "un seul patient dans cette période : file masquée");
        assertEquals(84, centre(ancienne, GRAND).capacite().capacite(), "la capacité ne dépend pas des séances");
    }

    @Test
    void generators_installed_after_the_end_of_the_period_are_not_counted() {
        generateur(GRAND, "CP-FUTUR", "EN_SERVICE", OffsetDateTime.now(ZoneOffset.UTC).plusDays(60));

        assertEquals(16, centre(service.capacite(SOC, debut, aujourdhui), GRAND).capacite().generateurs());
        assertEquals(17, centre(service.capacite(SOC, debut, aujourdhui.plusDays(90)), GRAND).capacite().generateurs());
    }

    @Test
    void a_small_active_file_is_masked_with_its_rate() {
        CentreCapacite petit = centre(service.capacite(SOC, debut, aujourdhui), PETIT);

        assertNull(petit.capacite().fileActive());
        assertNull(petit.capacite().tauxOccupation());
    }

    @Test
    void the_capacity_is_reached_when_the_active_file_equals_it() {
        for (int i = 0; i < 12; i++) seance(patient(PETIT), PETIT, debut.plusDays(7), "VALIDEE");   // 2 + 12 = 14

        CentreCapacite petit = centre(service.capacite(SOC, debut, aujourdhui), PETIT);

        assertEquals(14L, petit.capacite().fileActive());
        assertTrue(petit.capacite().atteinte());
        assertEquals(Niveau.ATTEINTE, petit.capacite().niveau());
        assertEquals(0, petit.capacite().tauxOccupation().compareTo(new BigDecimal("100.0")));
    }

    @Test
    void another_societe_never_appears() {
        CapaciteOverview o = service.capacite(SOC, debut, aujourdhui);

        assertTrue(o.centres().stream().noneMatch(c -> c.centerId().equals(AUTRE)));
        assertEquals(24, o.total().generateurs());
        assertEquals(1, service.capacite(SOC_AUTRE, debut, aujourdhui).total().generateurs());
    }

    @Test
    void an_invalid_period_is_rejected() {
        assertThrows(BusinessException.class, () -> service.capacite(SOC, aujourdhui, aujourdhui.minusDays(1)));
        assertThrows(BusinessException.class, () -> service.capacite(SOC, aujourdhui.minusYears(6), aujourdhui));
    }

    private void societe(UUID id, String code) {
        jdbc.update("INSERT INTO societes (id, code, raison_sociale, actif, created_at) VALUES (?,?,?,TRUE,CURRENT_TIMESTAMP)",
                id, code, "Société " + code);
    }

    private void centre(UUID id, String code, String name, UUID societe) {
        jdbc.update("INSERT INTO centers (id, code, name, societe_id, actif) VALUES (?,?,?,?,TRUE)", id, code, name, societe);
    }

    private void creneau(UUID centre, String code, String libelle) {
        jdbc.update("INSERT INTO position_creneau (id, center_id, code, libelle) VALUES (?,?,?,?)",
                UUID.randomUUID(), centre, code, libelle);
    }

    private void generateur(UUID centre, String code, String statut, OffsetDateTime installation) {
        jdbc.update("INSERT INTO gmao_equipements (id, code, designation, type, centre_id, statut, date_installation, "
                        + "date_creation, cree_par) VALUES (?, ?, ?, 'GENERATEUR_DIALYSE', ?, ?, ?, ?, ?)",
                UUID.randomUUID(), code + "-" + centre.toString().substring(30), "Générateur " + code, centre, statut,
                installation, OffsetDateTime.now(ZoneOffset.UTC), new UUID(0, 0));
    }

    private UUID patient(UUID centre) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO patients (id, center_id, code_patient, nom, prenom, sexe, date_naissance, numero_assurance, "
                        + "date_admission, type_patient, qualite_assure, sous_kt, epo_enabled, created_at) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,FALSE,CURRENT_TIMESTAMP)",
                id, centre, "CP-" + id.toString().substring(0, 8), "NOM", "Prenom", "M", Date.valueOf("1970-05-05"),
                "ASS-" + id.toString().substring(0, 8), Date.valueOf(LocalDate.of(2026, 1, 2)), "NON_VACANCIER",
                "ASSURE", false);
        return id;
    }

    private void seance(UUID patient, UUID centre, LocalDate date, String statut) {
        jdbc.update("INSERT INTO seances (id, patient_id, center_id, date_seance, statut, created_at) "
                        + "VALUES (?,?,?,?,?,CURRENT_TIMESTAMP)", UUID.randomUUID(), patient, centre, Date.valueOf(date),
                statut);
    }
}
