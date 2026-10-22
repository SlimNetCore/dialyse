package com.hemodialyse.backend.infrastructure.acl.absences;

import com.hemodialyse.backend.application.notification.SaisieInfirmierNotifier;
import com.hemodialyse.backend.contexts.absencespatients.publishedlanguage.SignalementDesSaisies;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Fournit au contexte Absences le langage publié {@link SignalementDesSaisies} (ACL vers le centre de notifications legacy,
 * retirée quand les notifications seront un contexte) : la saisie temps réel {@code SAISIE_INFIRMIER / ABSENCE}. Dans la
 * transaction de l'écriture (ou de l'exclusion de séance qui la contient), le signal attend la validation : les écrans qui se
 * rafraîchissent ne lisent jamais un état non validé, et une écriture annulée ne signale rien.
 */
@Component
public class SignalementDesSaisiesLegacy implements SignalementDesSaisies {

    private final SaisieInfirmierNotifier notifier;

    public SignalementDesSaisiesLegacy(SaisieInfirmierNotifier notifier) {
        this.notifier = notifier;
    }

    @Override
    public void absenceSaisie(UUID centre, UUID patient, LocalDate jourDeLAbsence) {
        Runnable signal = () -> notifier.saisie(centre, "ABSENCE", patient, jourDeLAbsence);
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
