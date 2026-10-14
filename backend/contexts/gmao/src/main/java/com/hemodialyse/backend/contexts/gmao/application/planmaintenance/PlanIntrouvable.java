package com.hemodialyse.backend.contexts.gmao.application.planmaintenance;

/**
 * Plan inconnu sur une commande. Étend {@link IllegalArgumentException} pour reproduire le contrat observé : HTTP 400
 * BAD_REQUEST « Plan non trouvé » et non 404 (KB-GMAO-16).
 */
public class PlanIntrouvable extends IllegalArgumentException {
    public PlanIntrouvable() {
        super("Plan non trouvé");
    }
}
