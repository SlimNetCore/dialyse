package com.hemodialyse.backend.contexts.gmao.application.piecejointe;

import com.hemodialyse.backend.contexts.gmao.domain.CentreId;
import com.hemodialyse.backend.contexts.gmao.domain.UtilisateurId;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.InterventionId;

/**
 * Commande : joindre un fichier à une intervention du centre. Le type de pièce et le nom arrivent tels que reçus : c'est le
 * domaine qui les valide et les assainit, dans l'ordre observé (intervention, statut, quota, type, contenu).
 */
public record JoindrePieceJointeCommande(InterventionId intervention, CentreId centre, String type, String nomFichier,
                                         byte[] contenu, UtilisateurId par) {
}
