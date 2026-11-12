package com.hemodialyse.backend.contexts.notifications.publishedlanguage;

import java.util.Optional;

/**
 * Langage publié {@code notifications.v1}, variante de {@link Notifier} pour l'émetteur qui journalise DANS sa transaction et diffuse
 * APRÈS le commit (ADR 0143). Premier temps : {@link #journaliser} à appeler dans la transaction de l'émetteur ; second temps : après le
 * commit, l'émetteur passe le résultat à {@link DiffusionEnTempsReel#diffuser}.
 * <ul>
 *   <li>identifiant déterministe déjà au journal : rien à diffuser ({@code Optional.empty()}) ;</li>
 *   <li>journal en panne, identifiant imposé : l'exception remonte (rien n'est diffusé, l'événement rejoué recommencera) ;</li>
 *   <li>journal en panne, identifiant non imposé : la panne est avalée et rendue dans {@link NotificationAEnvoyer#panneDuJournal()} ;
 *       la diffusion reste due, sans identifiant.</li>
 * </ul>
 */
public interface NotifierEnDeuxTemps {

    Optional<NotificationAEnvoyer> journaliser(DemandeDeNotification demande);
}
