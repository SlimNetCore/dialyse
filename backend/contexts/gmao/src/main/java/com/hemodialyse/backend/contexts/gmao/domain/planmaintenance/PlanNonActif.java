package com.hemodialyse.backend.contexts.gmao.domain.planmaintenance;

/**
 * Règle R25/R26 : la commande exige un plan actif. Étend {@link IllegalStateException} pour conserver le contrat
 * d'erreur observé (HTTP 422 BUSINESS_RULE_VIOLATION) ; messages identiques au legacy (parité).
 */
public class PlanNonActif extends IllegalStateException {
    private PlanNonActif(String message) {
        super(message);
    }

    static PlanNonActif pourExecuter() {
        return new PlanNonActif("Seul un plan actif peut être exécuté");
    }

    static PlanNonActif pourSuspendre() {
        return new PlanNonActif("Seul un plan actif peut être désactivé");
    }
}
