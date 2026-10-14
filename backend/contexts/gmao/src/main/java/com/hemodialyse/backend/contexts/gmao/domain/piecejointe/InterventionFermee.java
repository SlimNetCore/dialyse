package com.hemodialyse.backend.contexts.gmao.domain.piecejointe;

/**
 * KB-GMAO-64 (décision du 2026-10-12) : on ne joint ni ne retire de pièce sur une intervention annulée, terminée ou supprimée
 * logiquement. Étend {@link IllegalStateException} : HTTP 422 BUSINESS_RULE_VIOLATION, comme les autres commandes d'intervention.
 */
public class InterventionFermee extends IllegalStateException {
    public InterventionFermee() {
        super("Les pièces jointes d'une intervention annulée, terminée ou supprimée ne se modifient plus");
    }
}
