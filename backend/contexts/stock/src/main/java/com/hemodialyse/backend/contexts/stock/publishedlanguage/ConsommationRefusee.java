package com.hemodialyse.backend.contexts.stock.publishedlanguage;

/**
 * Refus d'une consommation de séance par le Stock (séance facturée, pièce déjà rapprochée, article inconnu, quantité invalide) : le
 * code est stable ({@code SEANCE_FACTUREE_NON_MODIFIABLE}, {@code PIECE_DEJA_RAPPROCHEE}…), l'appelant le traduit en 422.
 */
public class ConsommationRefusee extends RuntimeException {
    private final String code;

    public ConsommationRefusee(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}