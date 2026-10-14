package com.hemodialyse.backend.contexts.gmao.domain.piecejointe;

import com.hemodialyse.backend.contexts.gmao.domain.CentreId;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.InterventionId;

import java.time.Instant;

/** Événement de domaine d'une pièce jointe (nom au passé, immuable, jamais les octets du fichier). */
public interface EvenementDePieceJointe {

    PieceJointeId pieceJointeId();

    InterventionId interventionId();

    CentreId centreId();

    Instant survenuLe();
}
