package com.hemodialyse.backend.contexts.gmao.application.piecejointe;

import com.hemodialyse.backend.contexts.gmao.application.intervention.InterventionIntrouvable;
import com.hemodialyse.backend.contexts.gmao.domain.CentreId;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.InterventionId;
import com.hemodialyse.backend.contexts.gmao.domain.piecejointe.PieceJointe;
import com.hemodialyse.backend.contexts.gmao.domain.piecejointe.PieceJointeId;
import com.hemodialyse.backend.contexts.gmao.domain.piecejointe.PieceJointeRepository;

/**
 * Lit une pièce d'une intervention du centre pour la servir. Le centre stocké sur la pièce est ignoré, seul celui de
 * l'intervention compte (KB-GMAO-68, reproduit). Le nom est assaini de nouveau et le type MIME limité aux trois formats
 * acceptés (KB-GMAO-61) ; le téléchargement n'est pas journalisé (KB-GMAO-65, reproduit).
 */
public final class TelechargerPieceJointe {

    private final InterventionsHotes hotes;
    private final PieceJointeRepository pieces;

    public TelechargerPieceJointe(InterventionsHotes hotes, PieceJointeRepository pieces) {
        this.hotes = hotes;
        this.pieces = pieces;
    }

    public PieceTelechargee executer(InterventionId intervention, CentreId centre, PieceJointeId id) {
        hotes.trouver(intervention).filter(h -> h.centre().equals(centre)).orElseThrow(InterventionIntrouvable::new);
        PieceJointe piece = pieces.trouverParId(id).filter(p -> p.interventionId().equals(intervention))
                .orElseThrow(PieceJointeIntrouvable::new);
        return new PieceTelechargee(piece.nomAffiche(), piece.contentTypeServi(), piece.octets());
    }
}
