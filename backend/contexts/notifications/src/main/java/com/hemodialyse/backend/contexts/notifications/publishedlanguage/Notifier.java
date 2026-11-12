package com.hemodialyse.backend.contexts.notifications.publishedlanguage;

/**
 * Langage publié {@code notifications.v1} : un contexte émetteur demande de notifier. À appeler APRÈS le commit de la transaction de
 * l'émetteur (jamais de notification fantôme si elle est annulée). Une demande à identifiant déterministe déjà journalisé est ignorée.
 */
public interface Notifier {

    /** @return vrai si la notification a été émise, faux si l'identifiant déterministe était déjà au journal */
    boolean notifier(DemandeDeNotification demande);
}
