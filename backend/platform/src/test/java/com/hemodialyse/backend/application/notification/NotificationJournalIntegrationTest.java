package com.hemodialyse.backend.application.notification;

import com.hemodialyse.backend.application.infirmier.AbsenceInfirmierService;
import com.hemodialyse.backend.application.infirmier.InfirmierService;
import com.hemodialyse.backend.application.notification.NotificationJournalPort.Alerte;
import com.hemodialyse.backend.domain.infirmier.model.QualificationInfirmier;
import com.hemodialyse.backend.domain.infirmier.model.TypeAbsence;
import com.hemodialyse.backend.domain.shared.PagedResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Journal durable des alertes sur base réelle : une alerte envoyée la nuit est retrouvée à la connexion, par les seuls
 * rôles ciblés et le seul centre concerné ; la lecture est propre à chaque utilisateur ; une alerte ancienne est purgée.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class NotificationJournalIntegrationTest {

    private static final UUID CENTRE = UUID.fromString("99997000-0000-0000-0000-00000000000a");
    private static final UUID AUTRE_CENTRE = UUID.fromString("99997000-0000-0000-0000-00000000000b");

    @Autowired
    private NotificationService notifications;
    @Autowired
    private NotificationJournalPort journal;
    @Autowired
    private InfirmierService infirmiers;
    @Autowired
    private AbsenceInfirmierService absences;
    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    @AfterEach
    void nettoyer() {
        jdbc.update("DELETE FROM notification_lecture");
        jdbc.update("DELETE FROM notification_evenement WHERE center_id IN (?, ?)", CENTRE, AUTRE_CENTRE);
        jdbc.update("DELETE FROM infirmier_absence WHERE center_id IN (?, ?)", CENTRE, AUTRE_CENTRE);
        jdbc.update("DELETE FROM infirmier WHERE center_id IN (?, ?)", CENTRE, AUTRE_CENTRE);
    }

    private PagedResult<Alerte> pour(UUID centre, String utilisateur, String... roles) {
        return journal.lister(centre, utilisateur, Set.of(roles), 0, 50);
    }

    @Test
    void a_night_proposal_is_found_again_by_the_administrator_who_was_not_connected() {
        UUID run = UUID.randomUUID();

        notifications.notifyOptimisationProposition(CENTRE, run, "COUVERTURE", "SOUS_EFFECTIF", 3);

        PagedResult<Alerte> admin = pour(CENTRE, "admin-1", "ADMIN");
        assertEquals(1, admin.total());
        Alerte alerte = admin.items().get(0);
        assertEquals("OPTIMISATION_PROPOSITION", alerte.type());
        assertEquals(run.toString(), alerte.payload().get("runId"));
        assertEquals("3", alerte.payload().get("valeur"));
        assertFalse(alerte.lue());
    }

    @Test
    void an_alert_is_only_visible_to_the_targeted_roles_of_its_own_center() {
        notifications.notifyOptimisationProposition(CENTRE, UUID.randomUUID(), "PATIENTS", "GAIN", 2);

        assertEquals(1, pour(CENTRE, "u1", "ADMIN").total());
        assertEquals(0, pour(CENTRE, "u2", "MEDECIN").total(), "alerte réservée à l'administrateur");
        assertEquals(1, pour(CENTRE, "u3", "MEDECIN", "ADMIN").total(), "un des rôles suffit");
        assertEquals(0, pour(AUTRE_CENTRE, "u1", "ADMIN").total(), "autre centre : rien");
    }

    @Test
    void reading_is_personal_and_can_be_done_for_one_alert_or_for_all() {
        notifications.notifyGenerateurIndisponible(CENTRE, "A-G1", "HORS_SERVICE", List.of("BENALI Karim"));
        notifications.notifyGenerateurIndisponible(CENTRE, "A-G2", "EN_MAINTENANCE", List.of());
        List<Alerte> alertes = pour(CENTRE, "admin-1", "ADMIN").items();
        assertEquals(2, alertes.size());

        journal.marquerLues(CENTRE, "admin-1", List.of(alertes.get(0).id()));

        assertEquals(List.of(true, false), pour(CENTRE, "admin-1", "ADMIN").items().stream().map(Alerte::lue).toList());
        assertTrue(pour(CENTRE, "secretaire-1", "SECRETAIRE", "ADMIN").items().stream().noneMatch(Alerte::lue),
                "la lecture de l'un ne vaut pas pour les autres");

        journal.toutMarquerLu(CENTRE, "admin-1", Set.of("ADMIN"));
        assertTrue(pour(CENTRE, "admin-1", "ADMIN").items().stream().allMatch(Alerte::lue));
        journal.toutMarquerLu(CENTRE, "admin-1", Set.of("ADMIN"));
        assertEquals(2, pour(CENTRE, "admin-1", "ADMIN").total(), "relire ne duplique rien");
    }

    @Test
    void marking_an_alert_of_another_center_as_read_does_nothing() {
        notifications.notifyGenerateurIndisponible(CENTRE, "A-G1", "HORS_SERVICE", List.of());
        UUID id = pour(CENTRE, "admin-1", "ADMIN").items().get(0).id();

        journal.marquerLues(AUTRE_CENTRE, "admin-1", List.of(id));

        assertFalse(pour(CENTRE, "admin-1", "ADMIN").items().get(0).lue());
    }

    @Test
    void the_list_is_paginated_from_the_most_recent_alert() {
        for (int i = 1; i <= 5; i++) {
            journal.enregistrer(UUID.randomUUID(), CENTRE, "GENERATEUR_INDISPONIBLE",
                    Map.of("generateur", "G" + i, "targetRoles", "ADMIN"), Instant.now().minus(10 - i, ChronoUnit.MINUTES));
        }

        PagedResult<Alerte> premiere = journal.lister(CENTRE, "u", Set.of("ADMIN"), 0, 2);
        PagedResult<Alerte> derniere = journal.lister(CENTRE, "u", Set.of("ADMIN"), 2, 2);

        assertEquals(5, premiere.total());
        assertEquals(List.of("G5", "G4"), premiere.items().stream().map(a -> a.payload().get("generateur")).toList());
        assertEquals(List.of("G1"), derniere.items().stream().map(a -> a.payload().get("generateur")).toList());
    }

    @Test
    void alerts_older_than_the_retention_are_purged_with_their_readings() {
        UUID ancienne = UUID.randomUUID();
        journal.enregistrer(ancienne, CENTRE, "GENERATEUR_INDISPONIBLE", Map.of("targetRoles", "ADMIN"),
                Instant.now().minus(90, ChronoUnit.DAYS));
        journal.marquerLues(CENTRE, "admin-1", List.of(ancienne));
        notifications.notifyGenerateurIndisponible(CENTRE, "A-G1", "HORS_SERVICE", List.of());

        assertEquals(1, pour(CENTRE, "admin-1", "ADMIN").total(), "au-delà de 30 jours, plus proposée");
        assertEquals(1, journal.purger(Instant.now().minus(60, ChronoUnit.DAYS)));

        assertEquals(1, pour(CENTRE, "admin-1", "ADMIN").total());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM notification_lecture WHERE notification_id = ?",
                Long.class, ancienne));
    }

    @Test
    void transient_events_are_not_kept_in_the_journal() {
        notifications.notifyPatientCreated(CENTRE, UUID.randomUUID(), "P1", "Benali", "Karim");

        assertEquals(0, pour(CENTRE, "admin-1", "ADMIN").total());
    }

    @Test
    void an_absence_entered_by_the_secretary_is_logged_for_the_administrator_only() {
        UUID infirmier = infirmiers.creer(CENTRE, "J1", "Amrani", "Sara", null, QualificationInfirmier.INFIRMIER, false)
                .infirmier().id();
        LocalDate lundi = LocalDate.of(2026, 9, 28);

        absences.declarer(CENTRE, infirmier, lundi, lundi, TypeAbsence.CONGE, null, false);
        absences.declarer(CENTRE, infirmier, lundi.plusDays(7), lundi.plusDays(7), TypeAbsence.CONGE, null, true);

        PagedResult<Alerte> admin = pour(CENTRE, "admin-1", "ADMIN");
        assertEquals(1, admin.total(), "l'absence saisie par l'administrateur ne le prévient pas");
        assertEquals("INFIRMIER_ABSENCE_ENREGISTREE", admin.items().get(0).type());
        assertTrue(admin.items().get(0).payload().get("infirmier").contains("Amrani"));
        assertEquals(0, pour(CENTRE, "secretaire-1", "SECRETAIRE").total());
    }
}
