package com.hemodialyse.backend.contexts.seance.infrastructure.acl.notifications;

import com.hemodialyse.backend.contexts.notifications.publishedlanguage.DiffusionEnTempsReel;
import com.hemodialyse.backend.contexts.notifications.publishedlanguage.NotifierEnDeuxTemps;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.FichesDesPatients;
import com.hemodialyse.backend.contexts.seance.application.SessionCourante;
import com.hemodialyse.backend.contexts.seance.publishedlanguage.SignalementDesAdministrationsSaisies;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * ACL sortante de la Séance vers le contexte Notifications ({@code notifications.v1}, ADR 0154) : la saisie {@code ANEMIE} d'une
 * administration est signalée au médecin du centre ({@code SAISIE_INFIRMIER}). Retrouve l'identité du patient et l'auteur de la session ;
 * attend la validation de la transaction en cours ; une notification ne fait jamais échouer la saisie qu'elle accompagne.
 */
public class SignalementDesAdministrationsSaisiesParLeCentreDeNotifications implements SignalementDesAdministrationsSaisies {

    private static final Logger log = LoggerFactory.getLogger(SignalementDesAdministrationsSaisiesParLeCentreDeNotifications.class);

    private final EmissionDesNotificationsDeLaSeance emission;
    private final FichesDesPatients patients;
    private final SessionCourante session;

    public SignalementDesAdministrationsSaisiesParLeCentreDeNotifications(NotifierEnDeuxTemps notifier, DiffusionEnTempsReel diffusion,
                                                                          FichesDesPatients patients, SessionCourante session) {
        this.emission = new EmissionDesNotificationsDeLaSeance(notifier, diffusion);
        this.patients = patients;
        this.session = session;
    }

    @Override
    public void administrationSaisie(UUID centre, UUID patient, LocalDate jourDeLAdministration) {
        Runnable signal = () -> signaler(centre, patient, jourDeLAdministration);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    signal.run();
                }
            });
        } else {
            signal.run();
        }
    }

    private void signaler(UUID centre, UUID patient, LocalDate jour) {
        try {
            String nom = null;
            String prenom = null;
            if (patient != null) {
                var fiche = patients.noms(centre, List.of(patient)).get(patient);
                if (fiche != null) {
                    nom = fiche.nom();
                    prenom = fiche.prenom();
                }
            }
            emission.emettre(CatalogueDesNotificationsDeLaSeance.saisieDeLInfirmier(centre, "ANEMIE", patient, nom, prenom,
                    session.utilisateur(), jour));
        } catch (RuntimeException e) {
            log.warn("[NOTIF] Notification de la saisie ANEMIE impossible : {}", e.getMessage());
        }
    }
}
