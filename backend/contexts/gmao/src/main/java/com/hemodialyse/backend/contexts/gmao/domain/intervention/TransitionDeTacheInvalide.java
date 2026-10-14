package com.hemodialyse.backend.contexts.gmao.domain.intervention;

/**
 * Règle de rejet des tâches (R21) : une tâche ne démarre que si elle est à faire et ne s'achève que si elle est en cours.
 * Étend {@link IllegalStateException} : HTTP 422 BUSINESS_RULE_VIOLATION, comme {@link StatutIncompatible}.
 */
public class TransitionDeTacheInvalide extends IllegalStateException {

    private TransitionDeTacheInvalide(String message) {
        super(message);
    }

    static TransitionDeTacheInvalide pourDemarrer(StatutTache actuel) {
        return new TransitionDeTacheInvalide("Seule une tâche à faire peut être démarrée (statut actuel : " + actuel + ")");
    }

    static TransitionDeTacheInvalide pourAchever(StatutTache actuel) {
        return new TransitionDeTacheInvalide("Seule une tâche en cours peut être achevée (statut actuel : " + actuel + ")");
    }
}
