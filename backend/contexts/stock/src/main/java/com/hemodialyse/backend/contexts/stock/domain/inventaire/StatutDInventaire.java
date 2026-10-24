package com.hemodialyse.backend.contexts.stock.domain.inventaire;

/**
 * Statut d'un inventaire : un seul {@code EN_COURS} par centre ; {@code CLOTURE} et {@code ANNULE} sont définitifs.
 */
public enum StatutDInventaire {
    EN_COURS, CLOTURE, ANNULE
}

