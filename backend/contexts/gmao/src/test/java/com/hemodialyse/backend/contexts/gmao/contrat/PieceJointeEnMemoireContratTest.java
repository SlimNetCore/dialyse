package com.hemodialyse.backend.contexts.gmao.contrat;

import com.hemodialyse.backend.contexts.gmao.application.piecejointe.LecturePiecesJointes;
import com.hemodialyse.backend.contexts.gmao.doubles.PiecesJointesEnMemoire;
import com.hemodialyse.backend.contexts.gmao.domain.piecejointe.PieceJointeRepository;

/** Le double in-memory passe le contrat partagé des pièces jointes. */
class PieceJointeEnMemoireContratTest extends PieceJointeContrat {

    private final PiecesJointesEnMemoire memoire = new PiecesJointesEnMemoire();

    @Override
    protected PieceJointeRepository repository() {
        return memoire;
    }

    @Override
    protected LecturePiecesJointes lecture() {
        return memoire;
    }
}
