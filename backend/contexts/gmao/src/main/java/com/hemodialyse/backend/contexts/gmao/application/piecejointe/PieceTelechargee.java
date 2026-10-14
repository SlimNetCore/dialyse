package com.hemodialyse.backend.contexts.gmao.application.piecejointe;

/** Ce qu'il faut pour servir une pièce : nom assaini, type MIME à servir (jamais celui du client) et octets. */
public record PieceTelechargee(String nom, String contentType, byte[] octets) {
}
