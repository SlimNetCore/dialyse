package com.hemodialyse.backend.contexts.absencespatients.infrastructure.acl.notifications;

import com.hemodialyse.backend.contexts.absencespatients.publishedlanguage.SignalementDesSaisies;
import com.hemodialyse.backend.contexts.notifications.publishedlanguage.DiffusionEnTempsReel;
import com.hemodialyse.backend.contexts.notifications.publishedlanguage.NotifierEnDeuxTemps;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.FichesDesPatients;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * ACL sortante des Absences vers {@code notifications.v1} (ADR 0156) : la saisie temps réel {@code SAISIE_INFIRMIER / ABSENCE}, signalée
 * au médecin du centre. Retrouve l'identité du patient et l'auteur de la session. Dans la transaction de l'écriture (ou de l'exclusion de
 * séance qui la contient), le signal attend la validation : les écrans qui se rafraîchissent ne lisent jamais un état non validé, et une
 * écriture annulée ne signale rien. Une notification ne fait jamais échouer la saisie qu'elle accompagne.
 */
public class SignalementDesSaisiesParLeCentreDeNotifications implements SignalementDesSaisies {

    private static final Logger log = LoggerFactory.getLogger(SignalementDesSaisiesParLeCentreDeNotifications.class);

    private final EmissionDesNotificationsDesAbsences emission;
    private final FichesDesPatients patients;
    private final Supplier<String> auteur;

    public SignalementDesSaisiesParLeCentreDeNotifications(NotifierEnDeuxTemps notifier, DiffusionEnTempsReel diffusion,
                                                           FichesDesPatients patients) {
        this(notifier, diffusion, patients, SignalementDesSaisiesParLeCentreDeNotifications::utilisateurDeLaSession);
    }

    SignalementDesSaisiesParLeCentreDeNotifications(NotifierEnDeuxTemps notifier, DiffusionEnTempsReel diffusion,
                                                    FichesDesPatients patients, Supplier<String> auteur) {
        this.emission = new EmissionDesNotificationsDesAbsences(notifier, diffusion);
        this.patients = patients;
        this.auteur = auteur;
    }

    /** Nom d'utilisateur de l'auteur de la requête ({@code system} hors session). */
    private static String utilisateurDeLaSession() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a == null || !a.isAuthenticated() || a.getName() == null || a.getName().isBlank()) return "system";
        return a.getName();
    }

    @Override
    public void absenceSaisie(UUID centre, UUID patient, LocalDate jourDeLAbsence) {
        Runnable signal = () -> signaler(centre, patient, jourDeLAbsence);
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
            emission.emettre(CatalogueDesNotificationsDesAbsences.absenceSaisie(centre, patient, nom, prenom, auteur.get(), jour));
        } catch (RuntimeException e) {
            log.warn("[NOTIF] Notification de la saisie ABSENCE impossible : {}", e.getMessage());
        }
    }
}
