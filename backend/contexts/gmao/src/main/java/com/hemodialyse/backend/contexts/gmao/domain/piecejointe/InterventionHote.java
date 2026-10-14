package com.hemodialyse.backend.contexts.gmao.domain.piecejointe;

import com.hemodialyse.backend.contexts.gmao.domain.CentreId;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.InterventionId;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.StatutIntervention;

/**
 * Ce que les pièces jointes savent de l'intervention qui les porte : son centre, son statut, et si elle est supprimée
 * logiquement. Les pièces ne chargent jamais l'agrégat {@code Intervention} entier.
 */
public record InterventionHote(InterventionId id, CentreId centre, StatutIntervention statut, boolean supprimee) {

    /** Joindre ou retirer une pièce exige une intervention ni annulée, ni terminée, ni supprimée (KB-GMAO-64). */
    public void exigerOuverte() {
        if (supprimee || statut == StatutIntervention.ANNULEE || statut == StatutIntervention.TERMINEE) {
            throw new InterventionFermee();
        }
    }
}
