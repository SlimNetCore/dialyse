package com.hemodialyse.backend.contexts.gmao.domain.piecejointe;

import com.hemodialyse.backend.contexts.gmao.domain.CentreId;
import com.hemodialyse.backend.contexts.gmao.domain.UtilisateurId;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.InterventionId;

import java.time.Instant;

/** Une pièce a été retirée d'une intervention (suppression physique). Événement nouveau, sans consommateur (G5). */
public record PieceJointeRetiree(PieceJointeId pieceJointeId, InterventionId interventionId, CentreId centreId,
                                 String nom, long taille, UtilisateurId par, Instant survenuLe)
        implements EvenementDePieceJointe {
}
