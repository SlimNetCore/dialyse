package com.hemodialyse.backend.contexts.absencespatients.domain;

/**
 * Cycle de vie d'une absence de patient : « à qualifier », puis justifiée ou non justifiée par un motif, éventuellement
 * rattrapée par une séance de remplacement, ou annulée (le patient était présent ou la déclaration était erronée).
 * Les noms de valeurs sont ceux du contrat JSON et de la base (RG-ABS-003).
 */
public enum StatutAbsence {
    A_QUALIFIER,
    JUSTIFIEE,
    NON_JUSTIFIEE,
    RATTRAPEE,
    ANNULEE;

    /**
     * Absence comptabilisée (perte de séance et de chiffre d'affaires) tant qu'elle n'est ni rattrapée ni annulée
     * (RG-ABS-006).
     */
    public boolean comptabilisee() {
        return this == A_QUALIFIER || this == JUSTIFIEE || this == NON_JUSTIFIEE;
    }

    /**
     * Une absence rattrapée ou annulée n'est plus modifiable (RG-ABS-003).
     */
    public boolean modifiable() {
        return this != RATTRAPEE && this != ANNULEE;
    }
}
