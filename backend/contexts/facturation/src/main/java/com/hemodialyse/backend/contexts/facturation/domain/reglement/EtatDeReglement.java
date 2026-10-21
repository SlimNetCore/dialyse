package com.hemodialyse.backend.contexts.facturation.domain.reglement;

/**
 * État de règlement d'une facture (F13). Le trop-perçu n'est pas un état : une facture {@code REGLEE} porte alors le solde
 * {@link TypeDeSolde#TROP_PERCU} (KB-FAC-41).
 */
public enum EtatDeReglement {
    NON_REGLEE,
    PARTIELLEMENT_REGLEE,
    REGLEE
}
