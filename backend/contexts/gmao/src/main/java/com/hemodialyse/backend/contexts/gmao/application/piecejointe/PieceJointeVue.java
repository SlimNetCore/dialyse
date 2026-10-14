package com.hemodialyse.backend.contexts.gmao.application.piecejointe;

import com.hemodialyse.backend.contexts.gmao.domain.piecejointe.FormatReconnu;
import com.hemodialyse.backend.contexts.gmao.domain.piecejointe.NomDeFichier;
import com.hemodialyse.backend.contexts.gmao.domain.piecejointe.PieceJointe;
import com.hemodialyse.backend.contexts.gmao.domain.piecejointe.TypeDePiece;

import java.time.Instant;
import java.util.UUID;

/** Métadonnées d'une pièce jointe, sans ses octets : ce que montrent la liste et la réponse à un ajout. */
public record PieceJointeVue(UUID id, UUID interventionId, TypeDePiece type, String nom, String contentType, long taille,
                             Instant ajouteLe) {

    private static final String TYPE_PAR_DEFAUT = "application/octet-stream";

    public static PieceJointeVue depuis(PieceJointe piece) {
        return new PieceJointeVue(piece.id().valeur(), piece.interventionId().valeur(), piece.type(), piece.nomAffiche(),
                piece.contentTypeServi(), piece.taille(), piece.ajouteLe());
    }

    /** Nom assaini de nouveau et type MIME limité aux trois formats acceptés, même pour une pièce héritée (KB-GMAO-61). */
    public PieceJointeVue assainie() {
        String typeServi = FormatReconnu.depuisContentType(contentType).map(FormatReconnu::contentType).orElse(TYPE_PAR_DEFAUT);
        return new PieceJointeVue(id, interventionId, type, NomDeFichier.assainir(nom).valeur(), typeServi, taille, ajouteLe);
    }
}
