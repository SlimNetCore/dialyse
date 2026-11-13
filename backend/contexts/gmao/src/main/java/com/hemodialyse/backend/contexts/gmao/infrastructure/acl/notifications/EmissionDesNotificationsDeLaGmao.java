package com.hemodialyse.backend.contexts.gmao.infrastructure.acl.notifications;

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
 * Les deux temps de l'émission par la GMAO vers {@code notifications.v1} (ADR 0143, 0156) : journaliser puis diffuser en temps réel.
 * Identique à l'ancien {@code NotificationService.emettre} : une alerte à identifiant imposé part en temps réel après le commit de la
 * transaction en cours ; si l'enregistrement durable échoue, l'exception remonte sans rien diffuser (l'événement est rejoué).
 */
class EmissionDesNotificationsDeLaGmao {

    private static final Logger log = LoggerFactory.getLogger(EmissionDesNotificationsDeLaGmao.class);

    private final NotifierEnDeuxTemps notifier;
    private final DiffusionEnTempsReel diffusion;

    EmissionDesNotificationsDeLaGmao(NotifierEnDeuxTemps notifier, DiffusionEnTempsReel diffusion) {
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
