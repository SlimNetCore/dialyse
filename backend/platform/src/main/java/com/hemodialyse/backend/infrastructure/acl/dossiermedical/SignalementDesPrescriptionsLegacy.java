package com.hemodialyse.backend.infrastructure.acl.dossiermedical;

import com.hemodialyse.backend.application.notification.NotificationService;
import com.hemodialyse.backend.contexts.dossiermedical.publishedlanguage.SignalementDesPrescriptions;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;

/**
 * Fournit au Dossier médical le langage publié {@link SignalementDesPrescriptions} (ACL vers le centre de notifications legacy,
 * retirée quand les notifications seront un contexte) : la diffusion temps réel {@code PRESCRIPTION_CHANGED}. Dans une transaction,
 * le signal attend la validation : les écrans ne relisent jamais un état non validé et une écriture annulée ne signale rien.
 */
@Component
public class SignalementDesPrescriptionsLegacy implements SignalementDesPrescriptions {

    private final NotificationService notifications;

    public SignalementDesPrescriptionsLegacy(NotificationService notifications) {
        this.notifications = notifications;
    }

    @Override
    public void prescriptionChangee(UUID centre, UUID patient) {
        Runnable signal = () -> notifications.notifyPrescriptionChanged(centre, patient);
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
