package com.hemodialyse.backend.contexts.gmao.domain.piecejointe;

import com.hemodialyse.backend.contexts.gmao.domain.CentreId;
import com.hemodialyse.backend.contexts.gmao.domain.UtilisateurId;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.InterventionId;

import java.time.Instant;

/** Une pièce a été jointe à une intervention. Événement nouveau, sans consommateur (G5). */
public record PieceJointeAjoutee(PieceJointeId pieceJointeId, InterventionId interventionId, CentreId centreId,
                                 TypeDePiece type, String nom, long taille, UtilisateurId par, Instant survenuLe)
        implements EvenementDePieceJointe {
}
