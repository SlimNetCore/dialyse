package com.hemodialyse.backend.infrastructure.scheduling;

import com.hemodialyse.backend.application.query.ObservanceAnemieQueryService;
import com.hemodialyse.backend.application.query.ObservanceAnemieQueryService.ObservanceTraitement;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Observance de l'EPO sur base réelle : une prescription passée de 4000 à 8000 UI après une administration de 4000 UI
 * laisse 4000 UI à administrer (écran de l'infirmier) ; un retard constaté sur une période close est expliqué (alerte).
 * Chaque centre ne voit que ses propres administrations.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class ObservanceAnemieIntegrationTest {

    private static final UUID CENTRE = UUID.fromString("99995000-0000-0000-0000-00000000000a");
    private static final UUID AUTRE_CENTRE = UUID.fromString("99995000-0000-0000-0000-00000000000b");
    private static final UUID PATIENT = UUID.fromString("99995000-0000-0000-0000-0000000000a1");
    private static final UUID EPO = UUID.fromString("99995000-0000-0000-0000-0000000000e1");

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private ObservanceAnemieQueryService observance;
    @Autowired
    private ObservancePrescriptionScheduler scheduler;

    @BeforeEach
    @AfterEach
    void nettoyer() {
        for (String table : List.of("alertes_observance", "administrations_anemie", "prescriptions_medicales")) {
            jdbc.update("DELETE FROM " + table + " WHERE center_id IN (?, ?)", CENTRE, AUTRE_CENTRE);
        }
    }

    private void prescrire(UUID centre, LocalDate date, Integer doseUi) {
        jdbc.update("INSERT INTO prescriptions_medicales (id, patient_id, center_id, date_prescription, epo_article_id, "
                        + "epo_dose_ui, epo_voie, epo_frequence_valeur, epo_frequence_unite, created_at) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP)", UUID.randomUUID(), PATIENT, centre,
                Date.valueOf(date), EPO, doseUi, "SC", 1, "SEMAINE");
    }

    private void administrer(UUID centre, LocalDate jour, int dose) {
        jdbc.update("INSERT INTO administrations_anemie (id, patient_id, center_id, type_traitement, date_administration, "
                        + "administree, dose, unite_dose, molecule, created_at) VALUES (?,?,?,?,?,TRUE,?,?,?,CURRENT_TIMESTAMP)",
                UUID.randomUUID(), PATIENT, centre, "EPO", Date.valueOf(jour), dose, "UI", "Époétine alfa");
    }

    private ObservanceTraitement epo(UUID centre) {
        return observance.getObservanceActuelle(centre, PATIENT).epo();
    }

    @Test
    void should_leave_4000_ui_to_administer_when_the_prescription_goes_from_4000_to_8000_after_one_syringe() {
        LocalDate aujourdhui = LocalDate.now();
        prescrire(CENTRE, aujourdhui.minusDays(2), 4000);
        administrer(CENTRE, aujourdhui, 4000);
        assertEquals(0, epo(CENTRE).dosesRestantes(), "à 4000 UI par semaine, la seringue solde la période");

        prescrire(CENTRE, aujourdhui, 8000);

        ObservanceTraitement apres = epo(CENTRE);
        assertEquals("UI", apres.uniteDose());
        assertEquals(8000, apres.doseAttendue());
        assertEquals(4000, apres.doseAdministree());
        assertEquals(4000, apres.doseRestante());
        assertEquals(1, apres.dosesRestantes(), "il reste une seringue de 4000 UI");

        administrer(CENTRE, aujourdhui, 4000);

        ObservanceTraitement solde = epo(CENTRE);
        assertEquals(0, solde.doseRestante());
        assertEquals(0, solde.dosesRestantes());
        assertEquals(2, solde.dosesAdministrees());
    }

    @Test
    void should_count_administrations_for_a_prescription_without_dose() {
        prescrire(CENTRE, LocalDate.now(), null);

        ObservanceTraitement avant = epo(CENTRE);
        assertNull(avant.uniteDose());
        assertNull(avant.doseRestante());
        assertEquals(1, avant.dosesRestantes());

        administrer(CENTRE, LocalDate.now(), 4000);
        assertEquals(0, epo(CENTRE).dosesRestantes());
    }

    @Test
    void should_ignore_the_administrations_of_another_center() {
        prescrire(CENTRE, LocalDate.now(), 8000);
        administrer(AUTRE_CENTRE, LocalDate.now(), 8000);

        assertEquals(8000, epo(CENTRE).doseRestante());
        assertEquals(0, epo(CENTRE).dosesAdministrees());
    }

    @Test
    void should_open_a_late_alert_that_explains_what_was_prescribed_administered_and_missing() {
        LocalDate aujourdhui = LocalDate.now();
        LocalDate debut = aujourdhui.minusDays(10);
        prescrire(CENTRE, debut, 8000);
        administrer(CENTRE, debut.plusDays(1), 4000);

        scheduler.controlerCentre(CenterId.of(CENTRE));

        Map<String, Object> alerte = jdbc.queryForMap("SELECT * FROM alertes_observance WHERE center_id = ? "
                + "AND type_alerte = 'RETARD_CONSTATE'", CENTRE);
        assertEquals(8000, ((Number) alerte.get("doses_attendues")).intValue());
        assertEquals(4000, ((Number) alerte.get("doses_administrees")).intValue());
        assertEquals("UI", alerte.get("unite_dose"));
        assertEquals(8000, ((Number) alerte.get("dose_prescrite")).intValue());
        assertEquals(1, ((Number) alerte.get("frequence_valeur")).intValue());
        assertEquals("SEMAINE", alerte.get("frequence_unite"));
        assertEquals(debut, ((Date) alerte.get("periode_debut")).toLocalDate());
        assertEquals(debut.plusDays(6), ((Date) alerte.get("periode_fin")).toLocalDate());
        String message = (String) alerte.get("message");
        assertNotNull(message);
        assertTrue(message.contains("Il manque 4000 UI"), message);
        assertTrue(message.contains("Administré : 4000 UI"), message);
    }

    @Test
    void should_not_open_a_late_alert_when_the_prescribed_quantity_was_fully_administered() {
        LocalDate debut = LocalDate.now().minusDays(10);
        prescrire(CENTRE, debut, 8000);
        administrer(CENTRE, debut.plusDays(1), 4000);
        administrer(CENTRE, debut.plusDays(3), 4000);

        scheduler.controlerCentre(CenterId.of(CENTRE));

        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM alertes_observance WHERE center_id = ? "
                + "AND type_alerte = 'RETARD_CONSTATE'", Integer.class, CENTRE));
    }

    @Test
    void should_remind_before_the_deadline_with_the_quantity_left() {
        LocalDate aujourdhui = LocalDate.now();
        // dernière journée de la période en cours : l'échéance approche
        LocalDate debut = aujourdhui.minusDays(6);
        prescrire(CENTRE, debut, 8000);
        administrer(CENTRE, aujourdhui, 4000);

        scheduler.controlerCentre(CenterId.of(CENTRE));

        Map<String, Object> rappel = jdbc.queryForMap("SELECT * FROM alertes_observance WHERE center_id = ? "
                + "AND type_alerte = 'RAPPEL_ECHEANCE'", CENTRE);
        assertEquals(8000, ((Number) rappel.get("doses_attendues")).intValue());
        assertEquals(4000, ((Number) rappel.get("doses_administrees")).intValue());
        assertTrue(((String) rappel.get("message")).contains("Il reste à administrer 4000 UI de EPO"));
    }
}
