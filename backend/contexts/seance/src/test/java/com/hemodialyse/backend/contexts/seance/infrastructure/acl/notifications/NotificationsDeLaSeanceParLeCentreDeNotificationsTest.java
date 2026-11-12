package com.hemodialyse.backend.contexts.seance.infrastructure.acl.notifications;

import com.hemodialyse.backend.contexts.notifications.publishedlanguage.DemandeDeNotification;
import com.hemodialyse.backend.contexts.notifications.publishedlanguage.NotificationAEnvoyer;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.FichesDesPatients;
import com.hemodialyse.backend.contexts.seance.application.FicheDuPatient;
import com.hemodialyse.backend.contexts.seance.application.SessionCourante;
import com.hemodialyse.backend.contexts.seance.domain.PatientId;
import com.hemodialyse.backend.contexts.seance.domain.RappelDeRegularisation;
import com.hemodialyse.backend.contexts.seance.domain.Seance;
import com.hemodialyse.backend.contexts.seance.domain.SeanceId;
import com.hemodialyse.backend.contexts.seance.domain.SeancesARegulariser;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** ACL sortantes de la Séance vers notifications.v1 : après commit, rollback, journal ignoré, panne (PL13b, ADR 0154). */
class NotificationsDeLaSeanceParLeCentreDeNotificationsTest {

    private static final UUID CENTRE = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
    private static final UUID PATIENT = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID SEANCE = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
    private static final LocalDate JOUR = LocalDate.of(2026, 11, 12);
    private static final Instant MAINTENANT = Instant.parse("2026-11-12T08:00:00Z");

    private final List<DemandeDeNotification> demandes = new ArrayList<>();
    private final List<String> diffusions = new ArrayList<>();
    private boolean dejaJournalisee;
    private RuntimeException panneDuNotifier;

    private final com.hemodialyse.backend.contexts.notifications.publishedlanguage.NotifierEnDeuxTemps notifier = demande -> {
        if (panneDuNotifier != null) throw panneDuNotifier;
        demandes.add(demande);
        if (dejaJournalisee) return Optional.empty();
        return Optional.of(new NotificationAEnvoyer(demande.centre(), demande.type(), demande.contenu(), demande.identifiantDeterministe(),
                MAINTENANT, null));
    };
    private final com.hemodialyse.backend.contexts.notifications.publishedlanguage.DiffusionEnTempsReel diffusion =
            (centre, type, contenu, identifiant, emisLe) -> diffusions.add(type);

    private final CentreId centre = new CentreId(CENTRE);
    private final Seance seance = Seance.creer(new SeanceId(SEANCE), centre, new PatientId(PATIENT), JOUR, MAINTENANT);
    private final FicheDuPatient patient = new FicheDuPatient(new PatientId(PATIENT), "P-1", "Dupont", "Jean", null, null, null, null, null,
            null, null, null, null, null, null, null);

    @AfterEach
    void nettoyer() {
        TransactionSynchronizationManager.clear();
    }

    private void validerLaTransaction() {
        for (TransactionSynchronization synchronisation : new ArrayList<>(TransactionSynchronizationManager.getSynchronizations())) {
            synchronisation.afterCommit();
        }
    }

    // ------------------------------------------------------------ diffusions

    @Test
    void chaque_diffusion_de_seance_est_emise_aussitot_sans_journal_dans_l_ordre_des_appels() {
        var diffusionsDeSeance = new DiffusionsDeSeanceParLeCentreDeNotifications(notifier, diffusion);

        diffusionsDeSeance.seanceCreee(centre, seance, patient);
        diffusionsDeSeance.seanceValidee(centre, seance, patient);
        diffusionsDeSeance.saisieDeLInfirmier(centre, "SEANCE_VALIDEE", seance, patient, "marie");
        diffusionsDeSeance.voletParamedicalEnregistre(centre, seance, patient);
        diffusionsDeSeance.seanceDeverrouillee(centre, seance, patient);
        diffusionsDeSeance.consommablesModifies(centre, seance);

        assertEquals(List.of("SEANCE_CREATED", "SEANCE_VALIDATED", "SAISIE_INFIRMIER", "SEANCE_PARAMEDICAL_SAVED", "SEANCE_DEVERROUILLEE",
                "SEANCE_CONSOMMABLE_CHANGED"), diffusions);
        assertTrue(demandes.stream().noneMatch(DemandeDeNotification::journalisee));
        assertEquals("marie", demandes.get(2).contenu().get("auteur"));
        assertEquals("Dupont", demandes.getFirst().contenu().get("patientNom"));
    }

    @Test
    void la_diffusion_ne_remonte_aucune_panne_a_la_seance_enregistree() {
        panneDuNotifier = new IllegalStateException("courtier indisponible");
        var diffusionsDeSeance = new DiffusionsDeSeanceParLeCentreDeNotifications(notifier, diffusion);

        assertDoesNotThrow(() -> diffusionsDeSeance.seanceCreee(centre, seance, patient));
        assertTrue(diffusions.isEmpty());
    }

    @Test
    void un_type_de_saisie_inconnu_ne_remonte_pas_et_n_emet_rien() {
        var diffusionsDeSeance = new DiffusionsDeSeanceParLeCentreDeNotifications(notifier, diffusion);

        assertDoesNotThrow(() -> diffusionsDeSeance.saisieDeLInfirmier(centre, "INCONNU", seance, patient, "marie"));
        assertTrue(diffusions.isEmpty());
    }

    // ------------------------------------------------------------ rappel

    private RappelDeRegularisation rappel() {
        return new RappelDeRegularisation(centre, JOUR, new SeancesARegulariser(3, JOUR.minusDays(5)));
    }

    @Test
    void le_rappel_hors_transaction_est_journalise_puis_diffuse_aussitot() {
        var rappels = new RappelsDeRegularisationParLeCentreDeNotifications(notifier, diffusion);

        assertTrue(rappels.rappeler(rappel()));

        assertEquals(List.of("SEANCES_A_REGULARISER"), diffusions);
        DemandeDeNotification demande = demandes.getFirst();
        assertTrue(demande.journalisee());
        assertEquals(CatalogueDesNotificationsDeLaSeance.identifiantDuRappel(CENTRE, JOUR), demande.identifiantDeterministe());
        assertEquals("3", demande.contenu().get("nbSeances"));
    }

    @Test
    void le_rappel_dans_une_transaction_journalise_tout_de_suite_et_diffuse_apres_le_commit() {
        TransactionSynchronizationManager.initSynchronization();
        var rappels = new RappelsDeRegularisationParLeCentreDeNotifications(notifier, diffusion);

        assertTrue(rappels.rappeler(rappel()));

        assertEquals(1, demandes.size(), "journalise dans la transaction");
        assertTrue(diffusions.isEmpty(), "rien en temps reel avant le commit");
        validerLaTransaction();
        assertEquals(List.of("SEANCES_A_REGULARISER"), diffusions);
    }

    @Test
    void un_rappel_annule_ne_diffuse_rien() {
        TransactionSynchronizationManager.initSynchronization();
        new RappelsDeRegularisationParLeCentreDeNotifications(notifier, diffusion).rappeler(rappel());
        TransactionSynchronizationManager.clear();

        assertTrue(diffusions.isEmpty());
    }

    @Test
    void un_rappel_deja_journalise_n_est_ni_renvoye_ni_diffuse() {
        dejaJournalisee = true;

        assertFalse(new RappelsDeRegularisationParLeCentreDeNotifications(notifier, diffusion).rappeler(rappel()));

        assertTrue(diffusions.isEmpty());
    }

    @Test
    void une_panne_du_journal_remonte_pour_que_le_centre_soit_rendu_en_incident() {
        panneDuNotifier = new IllegalStateException("notification_evenement_pkey");

        assertThrows(IllegalStateException.class,
                () -> new RappelsDeRegularisationParLeCentreDeNotifications(notifier, diffusion).rappeler(rappel()));
        assertTrue(diffusions.isEmpty());
    }

    // ------------------------------------------------------------ saisie ANEMIE

    private final SessionCourante session = Mockito.mock(SessionCourante.class);
    private final FichesDesPatients fiches = Mockito.mock(FichesDesPatients.class);

    private SignalementDesAdministrationsSaisiesParLeCentreDeNotifications signalement() {
        Mockito.when(session.utilisateur()).thenReturn("inf-01");
        Mockito.when(fiches.noms(CENTRE, List.of(PATIENT))).thenReturn(Map.of(PATIENT, new FichesDesPatients.NomDuPatient("Jean", "Dupont")));
        return new SignalementDesAdministrationsSaisiesParLeCentreDeNotifications(notifier, diffusion, fiches, session);
    }

    @Test
    void la_saisie_de_l_anemie_nomme_le_patient_et_l_auteur_et_part_aussitot_hors_transaction() {
        signalement().administrationSaisie(CENTRE, PATIENT, JOUR);

        assertEquals(List.of("SAISIE_INFIRMIER"), diffusions);
        Map<String, String> contenu = demandes.getFirst().contenu();
        assertEquals("ANEMIE", contenu.get("saisie"));
        assertEquals("Dupont", contenu.get("patientNom"));
        assertEquals("Jean", contenu.get("patientPrenom"));
        assertEquals("inf-01", contenu.get("auteur"));
        assertEquals("2026-11-12", contenu.get("date"));
        assertEquals(List.of("MEDECIN"), demandes.getFirst().audience().roles());
    }

    @Test
    void la_saisie_de_l_anemie_attend_le_commit_et_ne_signale_rien_si_la_transaction_est_annulee() {
        var signalement = signalement();
        TransactionSynchronizationManager.initSynchronization();

        signalement.administrationSaisie(CENTRE, PATIENT, JOUR);

        assertTrue(demandes.isEmpty());
        validerLaTransaction();
        assertEquals(List.of("SAISIE_INFIRMIER"), diffusions);
        TransactionSynchronizationManager.clear();
        diffusions.clear();
        TransactionSynchronizationManager.initSynchronization();
        signalement.administrationSaisie(CENTRE, PATIENT, JOUR);
        TransactionSynchronizationManager.clear();
        assertTrue(diffusions.isEmpty());
    }

    @Test
    void la_saisie_de_l_anemie_ne_fait_jamais_echouer_l_ecriture_qu_elle_accompagne() {
        var signalement = signalement();
        panneDuNotifier = new IllegalStateException("broker down");

        assertDoesNotThrow(() -> signalement.administrationSaisie(CENTRE, PATIENT, JOUR));
    }
}
