package com.hemodialyse.backend.contexts.notifications.publishedlanguage;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Ce qu'il reste à diffuser en temps réel après le commit, une fois la notification journalisée ({@link NotifierEnDeuxTemps}). À passer tel
 * quel à {@link DiffusionEnTempsReel#diffuser}.
 *
 * @param identifiant   identifiant au journal ({@code null} : non journalisée, ou journal en panne sans identifiant imposé)
 * @param panneDuJournal échec du journal avalé (pas d'identifiant imposé) : le temps réel reste prioritaire, l'émetteur le consigne
 */
public record NotificationAEnvoyer(UUID centre, String type, Map<String, String> contenu, UUID identifiant, Instant emisLe,
                                   RuntimeException panneDuJournal) {
}
