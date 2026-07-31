package com.hemodialyse.backend.domain.stock.model;

/**
 * Lifecycle status shared by BL (bon de commande), BR (bon de reception)
 * and BS (bon de sortie).
 */
public enum BonStatut {
    BROUILLON,
    VALIDE,
    RECU,
    ANNULE
}

