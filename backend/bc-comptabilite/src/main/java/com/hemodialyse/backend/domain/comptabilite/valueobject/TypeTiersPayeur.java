package com.hemodialyse.backend.domain.comptabilite.valueobject;

/**
 * Type de tiers payeur — détermine le compte collectif 411 applicable.
 * Décision actée : un compte 411 distinct par type (411100 patient, 411200 CNAS, etc.)
 * avec lettrage par tiersId à l'intérieur du compte.
 */
public enum TypeTiersPayeur {
    PATIENT_DIRECT,
    CNAS,
    CASNOS,
    MUTUELLE,
    AUTRE
}

