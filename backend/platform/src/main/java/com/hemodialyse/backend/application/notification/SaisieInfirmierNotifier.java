package com.hemodialyse.backend.application.notification;

import com.hemodialyse.backend.contexts.dossiermedical.publishedlanguage.SaisieDeLAdministrationSignalee;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.FichesDesPatients;
import com.hemodialyse.backend.infrastructure.security.CurrentUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Signale au médecin du centre une saisie de l'infirmier : retrouve l'identité du patient et l'auteur de la requête puis
 * publie l'évènement {@code SAISIE_INFIRMIER}. Une notification ne fait jamais échouer la saisie qu'elle accompagne.
 */
@Service
public class SaisieInfirmierNotifier implements SaisieDeLAdministrationSignalee {

    private static final Logger log = LoggerFactory.getLogger(SaisieInfirmierNotifier.class);

    private final NotificationService notifications;
    private final FichesDesPatients patients;

    public SaisieInfirmierNotifier(NotificationService notifications, FichesDesPatients patients) {
        this.notifications = notifications;
        this.patients = patients;
    }

    /**
     * Port publié du Dossier médical (PL13a, ADR 0153) : la saisie {@code ANEMIE} d'une administration, émise ici jusqu'à PL13b (catalogue
     * de la Séance). Dans une transaction, le signal attend la validation (jamais d'alerte fantôme si l'écriture est annulée).
     */
    @Override
    public void administrationSaisie(UUID centre, UUID patient, LocalDate jourDeLAdministration) {
        Runnable signal = () -> saisie(centre, "ANEMIE", patient, jourDeLAdministration);
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

    /**
     * @param type      l'une de {@link NotificationService#SAISIES_INFIRMIER}
     * @param patientId patient concerné (facultatif)
     * @param date      date de la séance ou de l'absence concernée (facultative)
     */
    public void saisie(UUID centerId, String type, UUID patientId, LocalDate date) {
        try {
            String nom = null;
            String prenom = null;
            if (patientId != null) {
                var fiche = patients.noms(centerId, List.of(patientId)).get(patientId);
                if (fiche != null) {
                    nom = fiche.nom();
                    prenom = fiche.prenom();
                }
            }
            notifications.notifySaisieInfirmier(centerId, type, patientId, nom, prenom, CurrentUser.username(),
                    date == null ? null : date.toString());
        } catch (RuntimeException e) {
            log.warn("[NOTIF] Notification de la saisie {} impossible : {}", type, e.getMessage());
        }
    }
}
