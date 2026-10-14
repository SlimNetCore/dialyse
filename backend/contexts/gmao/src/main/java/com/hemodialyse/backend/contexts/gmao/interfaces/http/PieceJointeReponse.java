package com.hemodialyse.backend.contexts.gmao.interfaces.http;

import com.hemodialyse.backend.contexts.gmao.application.piecejointe.PageDePiecesJointes;
import com.hemodialyse.backend.contexts.gmao.application.piecejointe.PieceJointeVue;
import com.hemodialyse.backend.contexts.gmao.domain.piecejointe.TypeDePiece;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/**
 * Métadonnées d'une pièce jointe, mêmes champs et mêmes noms que le contrat observé ({@code DocumentInterventionResponse}) ;
 * les octets se téléchargent par la route dédiée. L'instant d'ajout est exposé en {@link OffsetDateTime} UTC.
 */
public record PieceJointeReponse(UUID id, UUID interventionId, TypeDePiece type, String nom, String contentType, long taille,
                                 OffsetDateTime ajouteLe) {

    static PieceJointeReponse depuis(PieceJointeVue vue) {
        return new PieceJointeReponse(vue.id(), vue.interventionId(), vue.type(), vue.nom(), vue.contentType(), vue.taille(),
                vue.ajouteLe() == null ? null : vue.ajouteLe().atOffset(ZoneOffset.UTC));
    }

    /** Même forme que le contrat observé : items, total, page, size. */
    public record Page(List<PieceJointeReponse> items, long total, int page, int size) {

        static Page depuis(PageDePiecesJointes page) {
            return new Page(page.items().stream().map(PieceJointeReponse::depuis).toList(), page.total(), page.page(), page.size());
        }
    }
}
