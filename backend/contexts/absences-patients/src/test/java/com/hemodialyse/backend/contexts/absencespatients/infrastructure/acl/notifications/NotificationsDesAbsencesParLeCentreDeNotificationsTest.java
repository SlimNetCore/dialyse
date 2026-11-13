package com.hemodialyse.backend.contexts.absencespatients.infrastructure.acl.notifications;

import com.hemodialyse.backend.contexts.notifications.publishedlanguage.Audience;
import com.hemodialyse.backend.contexts.notifications.publishedlanguage.DemandeDeNotification;
import com.hemodialyse.backend.contexts.notifications.publishedlanguage.DiffusionEnTempsReel;
import com.hemodialyse.backend.contexts.notifications.publishedlanguage.JournalDesNotifications;
import com.hemodialyse.backend.contexts.notifications.publishedlanguage.NotificationAEnvoyer;
import com.hemodialyse.backend.contexts.notifications.publishedlanguage.NotifierEnDeuxTemps;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.FichesDesPatients;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Messages des Absences de patients : alerte à qualifier et saisie ABSENCE (PL13d, ADR 0156). */
class NotificationsDesAbsencesParLeCentreDeNotificationsTest {

    private static final UUID CENTRE = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
    private static final UUID PATIENT = UUID.fromString("00000000-0000-0000-0000-0000000000d1");
    private static final LocalDate JOUR = LocalDate.of(2026, 10, 4);

    private final List<DemandeDeNotification> demandes = new ArrayList<>();
    private final List<String> diffusions = new ArrayList<>();
    private final Set<UUID> journalises = new HashSet<>();
    private RuntimeException pannePremierTemps;

    private final NotifierEnDeuxTemps notifier = demande -> {
        if (pannePremierTemps != null) throw pannePremierTemps;
        demandes.add(demande);
        UUID id = demande.identifiantDeterministe();
        if (id != null && !journalises.add(id)) return Optional.empty();
        return Optional.of(new NotificationAEnvoyer(demande.centre(), demande.type(), demande.contenu(), id, Instant.EPOCH, null));
    };
    private final DiffusionEnTempsReel diffusion = (centre, type, contenu, identifiant, emisLe) -> diffusions.add(type);
    private final JournalDesNotifications journal = new JournalDesNotifications() {
        @Override
        public void enregistrer(UUID id, UUID centre, String type, Map<String, String> contenu, Instant creeLe) {
            journalises.add(id);
        }

        @Override
        public boolean existe(UUID id) {
            return journalises.contains(id);
        }
    };
    private final FichesDesPatients patients = Mockito.mock(FichesDesPatients.class);
    private final NotificationsDuCentreParLeCentreDeNotifications alertes =
            new NotificationsDuCentreParLeCentreDeNotifications(notifier, diffusion, journal);
    private final SignalementDesSaisiesParLeCentreDeNotifications saisies =
            new SignalementDesSaisiesParLeCentreDeNotifications(notifier, diffusion, patients, () -> "inf-01");

    @AfterEach
    void nettoyer() {
        TransactionSynchronizationManager.clear();
    }

    private void validerLaTransaction() {
        for (TransactionSynchronization s : new ArrayList<>(TransactionSynchronizationManager.getSynchronizations())) {
            s.afterCommit();
        }
    }

    @Test
    void l_alerte_des_absences_a_qualifier_porte_les_comptes_et_vise_quatre_roles() {
        assertTrue(alertes.signalerLesAbsencesAQualifier(CENTRE, JOUR, 2, 1));

        DemandeDeNotification demande = demandes.getFirst();
        assertEquals("ABSENCES_A_QUALIFIER", demande.type());
        assertEquals(Map.of("nbAQualifier", "2", "nbEnRetard", "1", "targetRoles", "ADMIN,SECRETAIRE,INFIRMIER,MEDECIN"),
                demande.contenu());
        assertEquals(Audience.desRoles("ADMIN", "SECRETAIRE", "INFIRMIER", "MEDECIN"), demande.audience());
        assertTrue(demande.journalisee());
        assertEquals(List.of("ABSENCES_A_QUALIFIER"), diffusions);
    }

    @Test
    void l_alerte_n_est_emise_qu_une_fois_par_centre_et_par_jour() {
        UUID id = CatalogueDesNotificationsDesAbsences.identifiantDesAbsencesAQualifier(CENTRE, JOUR);
        assertEquals(id, CatalogueDesNotificationsDesAbsences.identifiantDesAbsencesAQualifier(CENTRE, JOUR));
        assertNotEquals(id, CatalogueDesNotificationsDesAbsences.identifiantDesAbsencesAQualifier(CENTRE, JOUR.plusDays(1)));

        assertTrue(alertes.signalerLesAbsencesAQualifier(CENTRE, JOUR, 2, 1));
        assertFalse(alertes.signalerLesAbsencesAQualifier(CENTRE, JOUR, 2, 1));
        assertEquals(1, diffusions.size());
        assertEquals(id, demandes.getFirst().identifiantDeterministe());
    }

    @Test
    void l_alerte_attend_le_commit_pour_le_temps_reel_et_rien_ne_part_si_la_transaction_est_annulee() {
        TransactionSynchronizationManager.initSynchronization();
        alertes.signalerLesAbsencesAQualifier(CENTRE, JOUR, 2, 1);
        assertEquals(1, demandes.size());
        assertTrue(diffusions.isEmpty());
        validerLaTransaction();
        assertEquals(List.of("ABSENCES_A_QUALIFIER"), diffusions);

        TransactionSynchronizationManager.clear();
        diffusions.clear();
        TransactionSynchronizationManager.initSynchronization();
        alertes.signalerLesAbsencesAQualifier(CENTRE, JOUR.plusDays(1), 1, 0);
        TransactionSynchronizationManager.clear();
        assertTrue(diffusions.isEmpty());
    }

    @Test
    void une_panne_du_journal_remonte_et_rien_n_est_diffuse() {
        pannePremierTemps = new IllegalStateException("base inaccessible");
        assertThrows(IllegalStateException.class, () -> alertes.signalerLesAbsencesAQualifier(CENTRE, JOUR, 2, 1));
        assertTrue(diffusions.isEmpty());
    }

    @Test
    void la_saisie_d_une_absence_va_au_medecin_avec_l_auteur_et_le_patient_sans_etre_journalisee() {
        Mockito.when(patients.noms(CENTRE, List.of(PATIENT))).thenReturn(Map.of(PATIENT, new FichesDesPatients.NomDuPatient("Jean", "Dupont")));

        saisies.absenceSaisie(CENTRE, PATIENT, JOUR);

        DemandeDeNotification demande = demandes.getFirst();
        assertEquals("SAISIE_INFIRMIER", demande.type());
        assertEquals(Map.of("saisie", "ABSENCE", "patientId", PATIENT.toString(), "patientNom", "Dupont", "patientPrenom", "Jean",
                "auteur", "inf-01", "date", "2026-10-04", "targetRoles", "MEDECIN"), demande.contenu());
        assertEquals(Audience.desRoles("MEDECIN"), demande.audience());
        assertFalse(demande.journalisee());
        assertNull(demande.identifiantDeterministe());
        assertEquals(List.of("SAISIE_INFIRMIER"), diffusions);
    }

    @Test
    void la_saisie_attend_le_commit_et_ne_signale_rien_si_l_ecriture_est_annulee() {
        TransactionSynchronizationManager.initSynchronization();
        saisies.absenceSaisie(CENTRE, null, null);
        assertTrue(demandes.isEmpty(), "rien avant la validation");
        TransactionSynchronizationManager.clear();
        assertTrue(diffusions.isEmpty());

        TransactionSynchronizationManager.initSynchronization();
        saisies.absenceSaisie(CENTRE, null, null);
        validerLaTransaction();
        assertEquals(Map.of("saisie", "ABSENCE", "patientId", "", "patientNom", "", "patientPrenom", "", "auteur", "inf-01",
                "date", "", "targetRoles", "MEDECIN"), demandes.getFirst().contenu());
    }

    @Test
    void une_notification_ne_fait_jamais_echouer_la_saisie_qu_elle_accompagne() {
        Mockito.when(patients.noms(CENTRE, List.of(PATIENT))).thenThrow(new IllegalStateException("broker down"));
        saisies.absenceSaisie(CENTRE, PATIENT, JOUR);   // aucune exception ne remonte
        pannePremierTemps = new IllegalStateException("journal en panne");
        saisies.absenceSaisie(CENTRE, null, JOUR);
        assertTrue(diffusions.isEmpty());
    }
}
