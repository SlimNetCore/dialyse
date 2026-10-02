package com.hemodialyse.backend.domain.absence.model;

/**
 * Cycle de vie d'une absence de patient : détectée ou déclarée « à qualifier », puis qualifiée par un motif
 * (justifiée ou non), éventuellement rattrapée par une séance de remplacement, ou annulée (le patient était présent
 * ou la déclaration était erronée).
 */
public enum StatutAbsence {
    A_QUALIFIER,
    JUSTIFIEE,
    NON_JUSTIFIEE,
    RATTRAPEE,
    ANNULEE;

    /**
     * Une absence est comptabilisée (perte de séance et de chiffre d'affaires) tant qu'elle n'est ni rattrapée ni
     * annulée.
     */
    public boolean comptabilisee() {
        return this == A_QUALIFIER || this == JUSTIFIEE || this == NON_JUSTIFIEE;
    }
}
