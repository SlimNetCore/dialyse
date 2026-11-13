package com.hemodialyse.backend.contexts.planification.infrastructure.acl.notifications;

import com.hemodialyse.backend.contexts.notifications.publishedlanguage.DemandeDeNotification;
import com.hemodialyse.backend.contexts.notifications.publishedlanguage.DiffusionEnTempsReel;
import com.hemodialyse.backend.contexts.notifications.publishedlanguage.JournalDesNotifications;
import com.hemodialyse.backend.contexts.notifications.publishedlanguage.NotificationAEnvoyer;
import com.hemodialyse.backend.contexts.notifications.publishedlanguage.NotifierEnDeuxTemps;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Optional;

/**
 * Les deux temps de l'émission par la Planification vers {@code notifications.v1} (ADR 0143, 0155) : journaliser (dans la transaction de
 * l'émetteur) puis diffuser en temps réel. Identique à l'ancien {@code NotificationService.emettre} : une alerte à identifiant imposé, ou
 * demandée « après commit », part en temps réel après le commit de la transaction en cours (jamais d'alerte fantôme si elle est annulée).
 */
class EmissionDesNotificationsDeLaPlanification {

    private static final Logger log = LoggerFactory.getLogger(EmissionDesNotificationsDeLaPlanification.class);

    private final NotifierEnDeuxTemps notifier;
    private final DiffusionEnTempsReel diffusion;
    private final JournalDesNotifications journal;

    EmissionDesNotificationsDeLaPlanification(NotifierEnDeuxTemps notifier, DiffusionEnTempsReel diffusion, JournalDesNotifications journal) {
        this.notifier = notifier;
        this.diffusion = diffusion;
        this.journal = journal;
    }

    /** Sans identifiant imposé : toujours émise ; le temps réel part aussitôt, ou après le commit si {@code apresLeCommit}. */
    void emettre(DemandeDeNotification demande, boolean apresLeCommit) {
        envoyer(demande, apresLeCommit);
    }

    /**
     * Identifiant déterministe : une seule fois. Déjà au journal : {@code false}. Une autre instance qui insère l'identifiant d'abord
     * (violation d'intégrité) vaut « déjà émise » ; toute autre violation remonte.
     */
    boolean emettreUneFois(DemandeDeNotification demande) {
        var id = demande.identifiantDeterministe();
        if (journal.existe(id)) {
            return false;
        }
        try {
            envoyer(demande, false);
        } catch (DataIntegrityViolationException concurrente) {
            if (journal.existe(id)) {
                return false;
            }
            throw concurrente;
        }
        return true;
    }

    private void envoyer(DemandeDeNotification demande, boolean apresLeCommit) {
        // premier temps, dans la transaction de l'émetteur : identifiant déjà journalisé = rien à faire ; journal en panne = l'erreur
        // remonte si l'identifiant est imposé (rien n'est envoyé, l'événement rejoué recommencera), sinon elle est rendue
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
        if ((demande.identifiantDeterministe() != null || apresLeCommit) && TransactionSynchronizationManager.isSynchronizationActive()) {
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
