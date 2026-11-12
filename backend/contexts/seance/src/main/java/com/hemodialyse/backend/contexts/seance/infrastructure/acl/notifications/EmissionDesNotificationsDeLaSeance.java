package com.hemodialyse.backend.contexts.seance.infrastructure.acl.notifications;

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
 * Les deux temps de l'émission par la Séance vers {@code notifications.v1} (ADR 0143, 0154) : journaliser (dans la transaction de
 * l'émetteur) puis diffuser en temps réel. Identique à l'ancien {@code NotificationService.emettre}.
 */
class EmissionDesNotificationsDeLaSeance {

    private static final Logger log = LoggerFactory.getLogger(EmissionDesNotificationsDeLaSeance.class);

    private final NotifierEnDeuxTemps notifier;
    private final DiffusionEnTempsReel diffusion;

    EmissionDesNotificationsDeLaSeance(NotifierEnDeuxTemps notifier, DiffusionEnTempsReel diffusion) {
        this.notifier = notifier;
        this.diffusion = diffusion;
    }

    /** Journalise, puis diffuse aussitôt. Identifiant imposé déjà au journal : rien n'est diffusé, {@code false}. */
    boolean emettre(DemandeDeNotification demande) {
        return journaliser(demande).map(aEnvoyer -> {
            diffuser(aEnvoyer);
            return true;
        }).orElse(false);
    }

    /**
     * Journalise dans la transaction en cours, puis diffuse après son commit (aussitôt hors transaction). {@code false} : identifiant déjà
     * au journal, rien à diffuser. Une panne du journal remonte si l'identifiant est imposé (rien n'est diffusé).
     */
    boolean emettreApresLeCommit(DemandeDeNotification demande) {
        Optional<NotificationAEnvoyer> journalisee = journaliser(demande);
        if (journalisee.isEmpty()) {
            return false;
        }
        NotificationAEnvoyer aEnvoyer = journalisee.get();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    diffuser(aEnvoyer);
                }
            });
        } else {
            diffuser(aEnvoyer);
        }
        return true;
    }

    private Optional<NotificationAEnvoyer> journaliser(DemandeDeNotification demande) {
        Optional<NotificationAEnvoyer> journalisee = notifier.journaliser(demande);
        journalisee.ifPresent(aEnvoyer -> {
            if (aEnvoyer.panneDuJournal() != null) {
                // le temps réel reste prioritaire : une base indisponible n'empêche pas d'alerter les connectés
                log.warn("[NOTIFICATION] Alerte {} non enregistrée pour le centre {}", demande.type(), demande.centre(),
                        aEnvoyer.panneDuJournal());
            }
        });
        return journalisee;
    }

    private void diffuser(NotificationAEnvoyer aEnvoyer) {
        diffusion.diffuser(aEnvoyer.centre(), aEnvoyer.type(), aEnvoyer.contenu(), aEnvoyer.identifiant(), aEnvoyer.emisLe());
    }
}
