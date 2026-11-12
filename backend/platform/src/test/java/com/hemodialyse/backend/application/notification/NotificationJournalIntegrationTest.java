package com.hemodialyse.backend.application.notification;

import com.hemodialyse.backend.contexts.notifications.application.NotificationDuJournal;
import com.hemodialyse.backend.contexts.notifications.application.NotificationsDuJournal;
import com.hemodialyse.backend.contexts.notifications.domain.NotificationId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.PagedResult;
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
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Les alertes émises par {@link NotificationService} sont enregistrées au journal du contexte Notifications (NOT1) sur base réelle : une
 * alerte envoyée la nuit est retrouvée à la connexion, par les seuls rôles ciblés et le seul centre concerné ; la lecture est propre à chaque
 * utilisateur. Les règles du journal lui-même (pagination, purge, visibilité exacte) sont dans les tests de contrat du module.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class NotificationJournalIntegrationTest {

    private static final UUID CENTRE = UUID.fromString("99997000-0000-0000-0000-00000000000a");
    private static final UUID AUTRE_CENTRE = UUID.fromString("99997000-0000-0000-0000-00000000000b");

    @Autowired
    private NotificationService notifications;
    @Autowired
    private com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.NotificationsDeLaFiche fiches;
    @Autowired
    private NotificationsDuJournal journal;
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

    private PagedResult<NotificationDuJournal> pour(UUID centre, String utilisateur, String... roles) {
        return journal.lister(new CentreId(centre), utilisateur, Set.of(roles), Instant.now().minus(30, ChronoUnit.DAYS), 0, 50);
    }

    @Test
    void a_night_proposal_is_found_again_by_the_administrator_who_was_not_connected() {
        UUID run = UUID.randomUUID();

        notifications.notifyOptimisationProposition(CENTRE, run, "COUVERTURE", "SOUS_EFFECTIF", 3);

        PagedResult<NotificationDuJournal> admin = pour(CENTRE, "admin-1", "ADMIN");
        assertEquals(1, admin.total());
        NotificationDuJournal alerte = admin.items().get(0);
        assertEquals("OPTIMISATION_PROPOSITION", alerte.type().valeur());
        assertEquals(run.toString(), alerte.contenu().get("runId"));
        assertEquals("3", alerte.contenu().get("valeur"));
        assertFalse(alerte.lue());
    }

    @Test
    void the_summary_of_the_night_is_kept_for_the_administrator_even_when_nothing_was_proposed() {
        notifications.notifyReplanificationNocturne(CENTRE, "RIEN", 0, 0);

        PagedResult<NotificationDuJournal> admin = pour(CENTRE, "admin-1", "ADMIN");
        assertEquals(1, admin.total(), "retrouvé à la connexion, personne n'étant connecté la nuit");
        assertEquals("REPLANIFICATION_NOCTURNE", admin.items().get(0).type().valeur());
        assertEquals("RIEN", admin.items().get(0).contenu().get("issue"));
        assertEquals(0, pour(CENTRE, "medecin-1", "MEDECIN").total());
        assertEquals(0, pour(AUTRE_CENTRE, "admin-2", "ADMIN").total());
    }

    @Test
    void a_roster_change_alert_reaches_the_account_of_the_nurse_and_the_administrator_but_not_the_other_nurses() {
        UUID infirmier = UUID.randomUUID();
        UUID compte = UUID.randomUUID();
        UUID run = UUID.randomUUID();

        notifications.notifyRoulementModifie(CENTRE, infirmier, compte, run, UUID.randomUUID());
        notifications.notifyRemplacementAffecte(CENTRE, infirmier, compte, LocalDate.of(2026, 11, 2), UUID.randomUUID(),
                UUID.randomUUID(), run, UUID.randomUUID());

        assertEquals(2, pour(CENTRE, compte.toString(), "INFIRMIER").total(), "l'infirmier concerné, par son compte");
        assertEquals(2, pour(CENTRE, "admin-1", "ADMIN").total(), "l'administration du centre");
        assertEquals(0, pour(CENTRE, UUID.randomUUID().toString(), "INFIRMIER").total(), "un autre infirmier du centre");
        assertEquals(0, pour(AUTRE_CENTRE, compte.toString(), "INFIRMIER", "ADMIN").total(), "un autre centre");
    }

    @Test
    void a_roster_change_alert_of_a_nurse_without_account_is_for_the_administrator_only() {
        notifications.notifyRoulementModifie(CENTRE, UUID.randomUUID(), null, UUID.randomUUID(), UUID.randomUUID());

        assertEquals(1, pour(CENTRE, "admin-1", "ADMIN").total());
        assertEquals(0, pour(CENTRE, "inf-1", "INFIRMIER").total());
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
        List<NotificationDuJournal> alertes = pour(CENTRE, "admin-1", "ADMIN").items();
        assertEquals(2, alertes.size());
        Instant maintenant = Instant.now();
        Instant depuis = maintenant.minus(30, ChronoUnit.DAYS);

        journal.marquerLues(new CentreId(CENTRE), "admin-1", Set.of("ADMIN"), List.<NotificationId>of(alertes.get(0).id()), maintenant);

        assertEquals(List.of(true, false), pour(CENTRE, "admin-1", "ADMIN").items().stream().map(NotificationDuJournal::lue).toList());
        assertTrue(pour(CENTRE, "secretaire-1", "SECRETAIRE", "ADMIN").items().stream().noneMatch(NotificationDuJournal::lue),
                "la lecture de l'un ne vaut pas pour les autres");

        journal.toutMarquerLu(new CentreId(CENTRE), "admin-1", Set.of("ADMIN"), depuis, maintenant);
        assertTrue(pour(CENTRE, "admin-1", "ADMIN").items().stream().allMatch(NotificationDuJournal::lue));
        journal.toutMarquerLu(new CentreId(CENTRE), "admin-1", Set.of("ADMIN"), depuis, maintenant);
        assertEquals(2, pour(CENTRE, "admin-1", "ADMIN").total(), "relire ne duplique rien");
    }

    @Test
    void transient_events_are_not_kept_in_the_journal() {
        fiches.ficheCreee(CENTRE, UUID.randomUUID(), "P1", "Benali", "Karim");

        assertEquals(0, pour(CENTRE, "admin-1", "ADMIN").total());
    }

    @Test
    void a_nurse_absence_always_reaches_the_doctor_and_the_administrator_only_when_the_secretary_entered_it() {
        UUID infirmier = UUID.randomUUID();
        jdbc.update("INSERT INTO infirmier (id, center_id, matricule, nom, prenom, qualification, habilite_isolement, actif) "
                + "VALUES (?, ?, 'J1', 'Amrani', 'Sara', ?, FALSE, TRUE)", infirmier, CENTRE,
                "INFIRMIER");
        LocalDate lundi = LocalDate.of(2026, 9, 28);

        notifications.notifyAbsenceInfirmierEnregistree(CENTRE, "Sara Amrani", lundi, lundi, true);
        notifications.notifyAbsenceInfirmierEnregistree(CENTRE, "Sara Amrani", lundi.plusDays(7), lundi.plusDays(7), false);

        PagedResult<NotificationDuJournal> admin = pour(CENTRE, "admin-1", "ADMIN");
        assertEquals(1, admin.total(), "l'absence saisie par l'administrateur ne le prévient pas");
        assertEquals("INFIRMIER_ABSENCE_ENREGISTREE", admin.items().get(0).type().valeur());
        assertTrue(admin.items().get(0).contenu().get("infirmier").contains("Amrani"));
        assertEquals(0, pour(CENTRE, "secretaire-1", "SECRETAIRE").total());

        PagedResult<NotificationDuJournal> medecin = pour(CENTRE, "medecin-1", "MEDECIN");
        assertEquals(2, medecin.total(), "le médecin est prévenu des deux absences, quel que soit l'auteur de la saisie");
        assertTrue(medecin.items().stream().allMatch(a -> "INFIRMIER_ABSENCE_ENREGISTREE".equals(a.type().valeur())));
        assertEquals(0, pour(AUTRE_CENTRE, "medecin-2", "MEDECIN").total(), "jamais les alertes d'un autre centre");
    }
}
