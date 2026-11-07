package com.hemodialyse.backend.infrastructure.acl.dossiermedical;

import com.hemodialyse.backend.application.notification.NotificationService;
import com.hemodialyse.backend.application.notification.SaisieInfirmierNotifier;
import com.hemodialyse.backend.contexts.dossiermedical.publishedlanguage.NotificationsDeLAnemie;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/**
 * Fournit au Dossier médical le langage publié {@link NotificationsDeLAnemie} (ACL vers le centre de notifications legacy, retirée quand
 * les notifications seront un contexte) : la saisie temps réel {@code SAISIE_INFIRMIER / ANEMIE} et l'alerte durable
 * {@code OBSERVANCE_NON_RESPECTEE}. Dans une transaction, le signal attend la validation (jamais d'alerte fantôme si l'écriture est annulée).
 */
@Component
public class NotificationsDeLAnemieLegacy implements NotificationsDeLAnemie {

    private final SaisieInfirmierNotifier saisies;
    private final NotificationService notifications;

    public NotificationsDeLAnemieLegacy(SaisieInfirmierNotifier saisies, NotificationService notifications) {
        this.saisies = saisies;
        this.notifications = notifications;
    }

    @Override
    public void administrationSaisie(UUID centre, UUID patient, LocalDate jourDeLAdministration) {
        apresLeCommit(() -> saisies.saisie(centre, "ANEMIE", patient, jourDeLAdministration));
    }

    @Override
    public void observanceNonRespectee(UUID centre, UUID patient, String message, Map<String, String> details) {
        apresLeCommit(() -> notifications.notifyObservanceNonRespectee(centre, patient, message, details));
    }

    private static void apresLeCommit(Runnable signal) {
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
}
