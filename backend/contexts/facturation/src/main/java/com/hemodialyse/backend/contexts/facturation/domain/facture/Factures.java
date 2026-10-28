package com.hemodialyse.backend.contexts.facturation.domain.facture;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

/**
 * Port sortant : les factures émises du centre (tables {@code factures} et {@code facture_lignes}). Une facture émise ne se
 * modifie plus.
 */
public interface Factures {

    /**
     * Enregistre la facture émise avec ses lignes.
     *
     * @throws NumeroDeFactureDejaPris si le centre a déjà une facture sous ce numéro (409)
     */
    void enregistrer(Facture facture);

    /**
     * Vrai si le centre a déjà une facture sous ce numéro.
     */
    boolean numeroDejaPris(CentreId centre, String numero);
}
