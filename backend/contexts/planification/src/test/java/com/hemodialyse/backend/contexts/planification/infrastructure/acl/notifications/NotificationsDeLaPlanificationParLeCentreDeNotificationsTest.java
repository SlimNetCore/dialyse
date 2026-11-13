package com.hemodialyse.backend.contexts.planification.infrastructure.acl.notifications;

import com.hemodialyse.backend.contexts.notifications.publishedlanguage.DemandeDeNotification;
import com.hemodialyse.backend.contexts.notifications.publishedlanguage.DiffusionEnTempsReel;
import com.hemodialyse.backend.contexts.notifications.publishedlanguage.JournalDesNotifications;
import com.hemodialyse.backend.contexts.notifications.publishedlanguage.NotificationAEnvoyer;
import com.hemodialyse.backend.contexts.notifications.publishedlanguage.NotifierEnDeuxTemps;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** ACL sortantes de la Planification vers notifications.v1 : une seule fois, après commit, rollback, concurrence, panne (PL13c, ADR 0155). */
class NotificationsDeLaPlanificationParLeCentreDeNotificationsTest {

    private static final UUID CENTRE = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
    private static final UUID PATIENT = UUID.fromString("00000000-0000-0000-0000-0000000000d1");
    private static final UUID INFIRMIER = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID EXECUTION = UUID.fromString("00000000-0000-0000-0000-0000000000e1");
    private static final LocalDate JOUR = LocalDate.of(2026, 10, 27);
    private static final Instant MAINTENANT = Instant.parse("2026-10-27T08:00:00Z");

    private final List<DemandeDeNotification> demandes = new ArrayList<>();
    private final List<String> diffusions = new ArrayList<>();
    private final Set<UUID> journalises = new HashSet<>();
    private RuntimeException pannePremierTemps;

    private final NotifierEnDeuxTemps notifier = demande -> {
        if (pannePremierTemps != null) throw pannePremierTemps;
        demandes.add(demande);
        UUID id = demande.identifiantDeterministe();
        if (id != null && !journalises.add(id)) return Optional.empty();
        return Optional.of(new NotificationAEnvoyer(demande.centre(), demande.type(), demande.contenu(), id, MAINTENANT, null));
    };
    private final DiffusionEnTempsReel diffusion = (centre, type, contenu, identifiant, emisLe) -> diffusions.add(type);
    private final JournalDesNotifications journal = new JournalDesNotifications() {
        @Override
        public void enregistrer(UUID id, UUID centre, String type, java.util.Map<String, String> contenu, Instant creeLe) {
            journalises.add(id);
        }

        @Override
        public boolean existe(UUID id) {
            return journalises.contains(id);
        }
    };

    private final NotificationsDeLaPresenceParLeCentreDeNotifications presence =
            new NotificationsDeLaPresenceParLeCentreDeNotifications(notifier, diffusion, journal);

    @AfterEach
    void nettoyer() {
        TransactionSynchronizationManager.clear();
    }

    private void validerLaTransaction() {
        for (TransactionSynchronization synchronisation : new ArrayList<>(TransactionSynchronizationManager.getSynchronizations())) {
            synchronisation.afterCommit();
        }
    }

    @Test
    void une_alerte_a_identifiant_deterministe_n_est_emise_qu_une_fois_par_centre_et_par_jour() {
        assertTrue(presence.sousEffectif(CENTRE, JOUR, 2, JOUR));
        assertFalse(presence.sousEffectif(CENTRE, JOUR, 2, JOUR));
        assertTrue(presence.sousEffectif(CENTRE, JOUR.plusDays(1), 2, JOUR));

        assertEquals(List.of("INFIRMIER_SOUS_EFFECTIF", "INFIRMIER_SOUS_EFFECTIF"), diffusions);
        assertEquals(CatalogueDesNotificationsDeLaPlanification.identifiant("INFIRMIER_SOUS_EFFECTIF", CENTRE, JOUR),
                demandes.getFirst().identifiantDeterministe());
    }

    @Test
    void sous_et_sureffectif_ont_chacun_leur_alerte_du_jour() {
        assertTrue(presence.sousEffectif(CENTRE, JOUR, 2, JOUR));
        assertTrue(presence.sureffectif(CENTRE, JOUR, 2, 1, 4, JOUR));

        assertEquals(List.of("INFIRMIER_SOUS_EFFECTIF", "INFIRMIER_SUREFFECTIF"), diffusions);
    }

    @Test
    void l_alerte_a_identifiant_attend_le_commit_pour_le_temps_reel_et_ne_diffuse_rien_si_la_transaction_est_annulee() {
        TransactionSynchronizationManager.initSynchronization();

        assertTrue(presence.sousEffectif(CENTRE, JOUR, 2, JOUR));

        assertEquals(1, demandes.size(), "journalisee dans la transaction");
        assertTrue(diffusions.isEmpty(), "rien en temps reel avant le commit");
        validerLaTransaction();
        assertEquals(List.of("INFIRMIER_SOUS_EFFECTIF"), diffusions);

        TransactionSynchronizationManager.clear();
        diffusions.clear();
        TransactionSynchronizationManager.initSynchronization();
        presence.sureffectif(CENTRE, JOUR, 2, 1, 4, JOUR);
        TransactionSynchronizationManager.clear();
        assertTrue(diffusions.isEmpty(), "transaction annulee : aucune alerte fantome");
    }

    @Test
    void une_alerte_sans_identifiant_part_aussitot_meme_dans_une_transaction() {
        var absences = new NotificationsDesAbsencesDInfirmiersParLeCentreDeNotifications(notifier, diffusion, journal);
        TransactionSynchronizationManager.initSynchronization();

        absences.absenceDeclareeParLInfirmier(CENTRE, "Sara Amrani", JOUR, JOUR);

        assertEquals(List.of("INFIRMIER_ABSENCE_DECLAREE"), diffusions);
    }

    @Test
    void les_seances_deplacees_attendent_le_commit_de_la_transaction_qui_applique_la_proposition() {
        var optimisation = new AlertesDeLOptimisationParLeCentreDeNotifications(notifier, diffusion, journal);
        TransactionSynchronizationManager.initSynchronization();

        optimisation.seancesDeplacees(CENTRE, "PATIENTS", 3, 1);

        assertEquals(1, demandes.size());
        assertTrue(diffusions.isEmpty());
        validerLaTransaction();
        assertEquals(List.of("SEANCES_DEPLACEES"), diffusions);
    }

    @Test
    void le_bilan_et_la_proposition_partent_aussitot_hors_transaction_dans_l_ordre_des_appels() {
        var optimisation = new AlertesDeLOptimisationParLeCentreDeNotifications(notifier, diffusion, journal);

        optimisation.propositionASignaler(CENTRE, EXECUTION, "COUVERTURE", "SOUS_EFFECTIF", 3);
        optimisation.bilanDeLaNuit(CENTRE, "PROPOSITIONS", 1, 0);
        optimisation.seancesDeplacees(CENTRE, "PATIENTS", 3, 1);

        assertEquals(List.of("OPTIMISATION_PROPOSITION", "REPLANIFICATION_NOCTURNE", "SEANCES_DEPLACEES"), diffusions);
    }

    @Test
    void les_alertes_personnelles_de_l_optimisation_sont_uniques_par_execution_et_par_infirmier() {
        var optimisation = new AlertesDeLOptimisationParLeCentreDeNotifications(notifier, diffusion, journal);
        UUID compte = UUID.randomUUID();

        assertTrue(optimisation.roulementModifie(CENTRE, INFIRMIER, compte, EXECUTION));
        assertFalse(optimisation.roulementModifie(CENTRE, INFIRMIER, compte, EXECUTION));
        assertTrue(optimisation.roulementModifie(CENTRE, INFIRMIER, compte, UUID.randomUUID()));
        UUID salle = UUID.randomUUID();
        UUID creneau = UUID.randomUUID();
        assertTrue(optimisation.remplacementAffecte(CENTRE, INFIRMIER, null, JOUR, salle, creneau, EXECUTION));
        assertFalse(optimisation.remplacementAffecte(CENTRE, INFIRMIER, null, JOUR, salle, creneau, EXECUTION));

        assertEquals(2, diffusions.stream().filter(t -> t.equals("INFIRMIER_ROULEMENT_MODIFIE")).count());
        assertEquals(1, diffusions.stream().filter(t -> t.equals("INFIRMIER_REMPLACEMENT_AFFECTE")).count());
        assertEquals(compte.toString(), demandes.getFirst().audience().destinataire());
    }

    @Test
    void le_patient_replace_en_isolement_est_emis_a_chaque_replacement() {
        var placement = new AlertesDuPlacementParLeCentreDeNotifications(notifier, diffusion, journal);

        assertTrue(placement.patientReplaceEnIsolement(CENTRE, PATIENT, "DUPONT Jean", "ISO-1", JOUR));
        assertTrue(placement.patientReplaceEnIsolement(CENTRE, PATIENT, "DUPONT Jean", "ISO-1", JOUR));

        assertEquals(List.of("PATIENT_REPLACE_ISOLEMENT", "PATIENT_REPLACE_ISOLEMENT"), diffusions);
    }

    @Test
    void les_alertes_du_placement_sont_uniques_par_patient_ou_generateur_et_par_jour() {
        var placement = new AlertesDuPlacementParLeCentreDeNotifications(notifier, diffusion, journal);
        UUID generateur = UUID.randomUUID();

        assertTrue(placement.isolementImpossible(CENTRE, PATIENT, "DUPONT Jean", JOUR));
        assertFalse(placement.isolementImpossible(CENTRE, PATIENT, "DUPONT Jean", JOUR));
        assertTrue(placement.retourEnSalleOrdinairePropose(CENTRE, PATIENT, "DUPONT Jean", "A-2", JOUR));
        assertTrue(placement.generateurIndisponible(CENTRE, generateur, "A-G1", "HORS_SERVICE", 1, List.of("BENALI Karim"), JOUR));
        assertFalse(placement.generateurIndisponible(CENTRE, generateur, "A-G1", "HORS_SERVICE", 1, List.of("BENALI Karim"), JOUR));

        assertEquals(List.of("ISOLEMENT_IMPOSSIBLE", "RETOUR_SALLE_ORDINAIRE_PROPOSE", "GENERATEUR_INDISPONIBLE"), diffusions);
    }

    @Test
    void la_liberation_en_echec_est_emise_une_fois_par_centre_et_par_jour() {
        var liberation = new NotificationsDeLaLiberationParLeCentreDeNotifications(notifier, diffusion, journal);

        assertTrue(liberation.liberationEnEchec(CENTRE, JOUR, 2));
        assertFalse(liberation.liberationEnEchec(CENTRE, JOUR, 2));

        assertEquals(List.of("LIBERATION_PLACES_EN_ECHEC"), diffusions);
        assertEquals(CatalogueDesNotificationsDeLaPlanification.identifiant("LIBERATION_PLACES_EN_ECHEC", CENTRE, JOUR),
                demandes.getFirst().identifiantDeterministe());
    }

    @Test
    void une_autre_instance_qui_a_insere_l_identifiant_d_abord_vaut_alerte_deja_emise() {
        var inserteeParUneAutre = new boolean[1];
        NotifierEnDeuxTemps concurrent = demande -> {
            inserteeParUneAutre[0] = true;
            journalises.add(demande.identifiantDeterministe());
            throw new DataIntegrityViolationException("doublon");
        };
        var liberation = new NotificationsDeLaLiberationParLeCentreDeNotifications(concurrent, diffusion, journal);

        assertFalse(liberation.liberationEnEchec(CENTRE, JOUR, 1));

        assertTrue(inserteeParUneAutre[0]);
        assertTrue(diffusions.isEmpty());
    }

    @Test
    void une_vraie_violation_d_integrite_remonte() {
        NotifierEnDeuxTemps casse = demande -> {
            throw new DataIntegrityViolationException("autre");
        };
        var liberation = new NotificationsDeLaLiberationParLeCentreDeNotifications(casse, diffusion, journal);

        assertThrows(DataIntegrityViolationException.class, () -> liberation.liberationEnEchec(CENTRE, JOUR, 1));
    }

    @Test
    void une_panne_du_journal_remonte_sans_rien_diffuser() {
        pannePremierTemps = new IllegalStateException("notification_evenement_pkey");

        assertThrows(IllegalStateException.class, () -> presence.sousEffectif(CENTRE, JOUR, 2, JOUR));
        assertTrue(diffusions.isEmpty());
    }

    @Test
    void une_panne_du_journal_rendue_par_le_notifier_n_empeche_pas_le_temps_reel() {
        NotifierEnDeuxTemps enPanne = demande -> Optional.of(new NotificationAEnvoyer(demande.centre(), demande.type(), demande.contenu(),
                demande.identifiantDeterministe(), MAINTENANT, new IllegalStateException("base indisponible")));
        var absences = new NotificationsDesAbsencesDInfirmiersParLeCentreDeNotifications(enPanne, diffusion, journal);

        absences.absenceEnregistree(CENTRE, "Sara Amrani", JOUR, JOUR, true);

        assertEquals(List.of("INFIRMIER_ABSENCE_ENREGISTREE"), diffusions);
    }

    @Test
    void les_retraits_d_absence_visent_les_roles_de_leur_auteur() {
        var absences = new NotificationsDesAbsencesDInfirmiersParLeCentreDeNotifications(notifier, diffusion, journal);

        absences.absenceRetireeParLAdministration(CENTRE, "Sara Amrani", JOUR, JOUR, true);
        absences.absenceRetireeParLAdministration(CENTRE, "Sara Amrani", JOUR, JOUR, false);
        absences.absenceRetireeParLInfirmier(CENTRE, "Sara Amrani", JOUR, JOUR);

        assertEquals(List.of(List.of("ADMIN", "MEDECIN"), List.of("MEDECIN"), List.of("ADMIN", "SECRETAIRE", "MEDECIN")),
                demandes.stream().map(d -> d.audience().roles()).toList());
    }
}
