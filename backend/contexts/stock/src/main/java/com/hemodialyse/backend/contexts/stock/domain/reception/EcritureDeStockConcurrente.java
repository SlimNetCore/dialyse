package com.hemodialyse.backend.contexts.stock.domain.reception;

/**
 * Une écriture de stock n'a pas obtenu le verrou de ses articles dans le délai (un recalcul, une réception ou une sortie la
 * précède) : 409 {@code STOCK_ECRITURE_CONCURRENTE}, rien n'est écrit (ADR 0043, 0045).
 */
public class EcritureDeStockConcurrente extends RuntimeException {

    public EcritureDeStockConcurrente() {
        super("Une autre écriture porte sur ces articles : réessayez dans un instant");
    }
}

