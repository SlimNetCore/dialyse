package com.hemodialyse.backend.contexts.stock.infrastructure.acl.notifications;

import com.hemodialyse.backend.contexts.notifications.publishedlanguage.DemandeDeNotification;
import com.hemodialyse.backend.contexts.notifications.publishedlanguage.DiffusionEnTempsReel;
import com.hemodialyse.backend.contexts.notifications.publishedlanguage.NotificationAEnvoyer;
import com.hemodialyse.backend.contexts.notifications.publishedlanguage.NotifierEnDeuxTemps;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Optional;

/**
 * Les deux temps de l'émission par le Stock vers {@code notifications.v1} (ADR 0143, 0156) : journaliser (dans la transaction de
 * l'émetteur) puis diffuser en temps réel. Identique à l'ancien {@code NotificationService.emettre} : une alerte à identifiant imposé part
 * en temps réel après le commit de la transaction en cours (jamais d'alerte fantôme si elle est annulée).
 */
class EmissionDesNotificationsDuStock {

    private static final Logger log = LoggerFactory.getLogger(EmissionDesNotificationsDuStock.class);

    private final NotifierEnDeuxTemps notifier;
    private final DiffusionEnTempsReel diffusion;

    EmissionDesNotificationsDuStock(NotifierEnDeuxTemps notifier, DiffusionEnTempsReel diffusion) {
        this.notifier = notifier;
        this.diffusion = diffusion;
    }

    void emettre(DemandeDeNotification demande) {
        Optional<NotificationAEnvoyer> journalisee = notifier.journaliser(demande);
        if (journalisee.isEmpty()) {
            return;
        }
        NotificationAEnvoyer aEnvoyer = journalisee.get();
        if (aEnvoyer.panneDuJournal() != null) {
            // le temps réel reste prioritaire : une base indisponible n'empêche pas d'alerter les connectés
            log.warn("[NOTIFICATION] Alerte {} non enregistrée pour le centre {}", demande.type(), demande.centre(),
                    aEnvoyer.panneDuJournal());
        }
        Runnable tempsReel = () -> diffusion.diffuser(aEnvoyer.centre(), aEnvoyer.type(), aEnvoyer.contenu(), aEnvoyer.identifiant(),
                aEnvoyer.emisLe());
        if (demande.identifiantDeterministe() != null && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    tempsReel.run();
                }
            });
        } else {
            tempsReel.run();
        }
    }
}
