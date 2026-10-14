package com.hemodialyse.backend.contexts.gmao.application.piecejointe;

import java.util.List;

/** Page de pièces jointes (même forme que le contrat observé : items, total, page, size). */
public record PageDePiecesJointes(List<PieceJointeVue> items, long total, int page, int size) {
}
