package com.hemodialyse.backend.contexts.facturation.domain.reglement;

/**
 * Nature du solde d'une facture : un reste à payer, soldée exactement, ou un trop-perçu.
 */
public enum TypeDeSolde {
    RESTE,
    REGLE,
    TROP_PERCU
}
