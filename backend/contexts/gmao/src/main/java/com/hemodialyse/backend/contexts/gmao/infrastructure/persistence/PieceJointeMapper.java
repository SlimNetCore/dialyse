package com.hemodialyse.backend.contexts.gmao.infrastructure.persistence;

import com.hemodialyse.backend.contexts.gmao.domain.CentreId;
import com.hemodialyse.backend.contexts.gmao.domain.UtilisateurId;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.InterventionId;
import com.hemodialyse.backend.contexts.gmao.domain.piecejointe.PieceJointe;
import com.hemodialyse.backend.contexts.gmao.domain.piecejointe.PieceJointeId;
import com.hemodialyse.backend.contexts.gmao.domain.piecejointe.TypeDePiece;

/** Mapping explicite agrégat {@link PieceJointe} <-> modèle de persistance. */
final class PieceJointeMapper {

    private PieceJointeMapper() {
    }

    static PieceJointe versDomaine(PieceJointeJpa e) {
        return PieceJointe.reconstituer(new PieceJointeId(e.id), new InterventionId(e.interventionId), new CentreId(e.centreId),
                TypeDePiece.depuis(e.type), e.nom, e.contentType, e.taille, e.contenu,
                e.ajoutePar == null ? null : new UtilisateurId(e.ajoutePar), e.ajouteLe);
    }

    static PieceJointe versDomaineSansOctets(PieceEnTete e) {
        return PieceJointe.reconstituer(new PieceJointeId(e.id()), new InterventionId(e.interventionId()), new CentreId(e.centreId()),
                TypeDePiece.depuis(e.type()), e.nom(), e.contentType(), e.taille(), new byte[0],
                e.ajoutePar() == null ? null : new UtilisateurId(e.ajoutePar()), e.ajouteLe());
    }

    static void recopier(PieceJointe piece, PieceJointeJpa e) {
        e.interventionId = piece.interventionId().valeur();
        e.centreId = piece.centreId().valeur();
        e.type = piece.type().name();
        e.nom = piece.nom();
        e.contentType = piece.contentType();
        e.taille = piece.taille();
        e.contenu = piece.octets();
        e.ajoutePar = piece.ajoutePar() == null ? null : piece.ajoutePar().valeur();
        e.ajouteLe = piece.ajouteLe();
    }
}
