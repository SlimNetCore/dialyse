package com.hemodialyse.backend.contexts.gmao.application.piecejointe;

import com.hemodialyse.backend.contexts.gmao.domain.intervention.InterventionId;
import com.hemodialyse.backend.contexts.gmao.domain.piecejointe.InterventionHote;

import java.util.Optional;

/**
 * Port sortant : ce que les pièces jointes ont besoin de savoir de l'intervention qui les porte (centre, statut, supprimée ou
 * non), sans charger l'agrégat {@code Intervention}. Une intervention supprimée logiquement reste trouvable (KB-GMAO-64).
 */
public interface InterventionsHotes {

    Optional<InterventionHote> trouver(InterventionId id);

    /**
     * Comme {@link #trouver}, en posant un verrou d'écriture sur l'intervention jusqu'à la fin de la transaction : deux ajouts
     * simultanés se suivent, et le quota de 50 est réellement tenu (KB-GMAO-58, corrigé le 2026-10-12).
     */
    Optional<InterventionHote> verrouiller(InterventionId id);
}
