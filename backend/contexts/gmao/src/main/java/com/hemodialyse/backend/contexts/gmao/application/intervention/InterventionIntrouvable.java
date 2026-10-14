package com.hemodialyse.backend.contexts.gmao.application.intervention;

/**
 * Intervention inconnue, ou d'un autre centre (indistinguable), sur une commande. Étend {@link IllegalArgumentException}
 * pour reproduire le contrat observé : HTTP 400 BAD_REQUEST « Intervention non trouvée » et non 404 (KB-GMAO-53).
 */
public class InterventionIntrouvable extends IllegalArgumentException {
    public InterventionIntrouvable() {
        super("Intervention non trouvée");
    }
}
