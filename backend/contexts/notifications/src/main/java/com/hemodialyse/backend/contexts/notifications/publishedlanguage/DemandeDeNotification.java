package com.hemodialyse.backend.contexts.notifications.publishedlanguage;

import java.util.Map;
import java.util.UUID;

/**
 * Ordre d'un contexte émetteur de notifier. Le type et le contenu (paires de texte) sont définis et documentés par l'émetteur.
 *
 * @param identifiantDeterministe identifiant calculé par l'émetteur : un fait rejoué ne crée pas de doublon ({@code null} : aléatoire)
 * @param journalisee             conservée au journal (retrouvée à la connexion) ; sinon, simple diffusion en temps réel
 */
public record DemandeDeNotification(UUID centre, String type, Map<String, String> contenu, Audience audience, UUID identifiantDeterministe,
                                    boolean journalisee) {
}
