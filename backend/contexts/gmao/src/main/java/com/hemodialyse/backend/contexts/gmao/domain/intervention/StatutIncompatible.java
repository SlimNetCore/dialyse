package com.hemodialyse.backend.contexts.gmao.domain.intervention;

/**
 * R10, R15, R17 : la commande n'est pas permise dans le statut courant de l'intervention. Étend
 * {@link IllegalStateException} pour conserver le contrat observé (HTTP 422 BUSINESS_RULE_VIOLATION) ; messages identiques
 * au legacy (parité).
 */
public class StatutIncompatible extends IllegalStateException {

    private StatutIncompatible(String message) {
        super(message);
    }

    static StatutIncompatible pourDemarrer() {
        return new StatutIncompatible("Seule une intervention planifiée peut être démarrée");
    }

    static StatutIncompatible pourTerminer() {
        return new StatutIncompatible("Seule une intervention en cours peut être terminée");
    }

    static StatutIncompatible pourAnnuler() {
        return new StatutIncompatible("Une intervention terminée ne peut pas être annulée");
    }

    static StatutIncompatible pourRectifier() {
        return new StatutIncompatible("Seule une intervention terminée peut être rectifiée");
    }

    static StatutIncompatible pourAjouterUneLigne() {
        return new StatutIncompatible("Une intervention annulée n'accepte plus de ligne de coût");
    }

    static StatutIncompatible pourAjouterUneTache() {
        return new StatutIncompatible("Une tâche ne s'ajoute qu'à une intervention planifiée ou en cours");
    }

    static StatutIncompatible pourTravaillerUneTache() {
        return new StatutIncompatible("Une tâche ne se démarre ou ne s'achève que sur une intervention en cours");
    }

    static StatutIncompatible pourRetirerUneLigne() {
        return new StatutIncompatible(
                "Les lignes de coût ne se corrigent que sur une intervention ouverte : rectifiez-la d'abord");
    }
}
