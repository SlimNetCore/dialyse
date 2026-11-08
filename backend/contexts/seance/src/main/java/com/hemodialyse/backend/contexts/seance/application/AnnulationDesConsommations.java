package com.hemodialyse.backend.contexts.seance.application;

import com.hemodialyse.backend.contexts.seance.domain.SeanceId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

/**
 * Port sortant : le Stock annule toutes les consommations imputées à une séance supprimée (SE6). Le Stock décide : il n'écrit AUCUN
 * mouvement ni retour en stock (KB-SEA-51, reproduit : le Javadoc du legacy parlait de « restitution », elle n'existe pas) et refuse
 * ({@link ConsommationRefusee}, 422, code du Stock) une séance facturée ou une pièce de sortie déjà rapprochée.
 */
public interface AnnulationDesConsommations {
    void annuler(CentreId centre, SeanceId seance, String auteur);
}
