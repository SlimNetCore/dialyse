package com.hemodialyse.backend.contexts.stock.domain.commandes;

/**
 * États d'un bon de commande : {@code BROUILLON} (lignes modifiables), {@code VALIDE} (figé), {@code RECU} (posé par la
 * réception, slice S5 ; jusque-là par le legacy de réception). Pas d'état annulé (ADR 0045).
 */
public enum StatutDuBon {
    BROUILLON,
    VALIDE,
    RECU
}

