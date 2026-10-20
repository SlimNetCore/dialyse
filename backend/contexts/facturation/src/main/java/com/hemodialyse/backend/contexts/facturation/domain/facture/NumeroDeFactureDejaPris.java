package com.hemodialyse.backend.contexts.facturation.domain.facture;

/**
 * Le numéro de facture réservé est déjà celui d'une facture du centre (409, KB-FAC-24/25) : la séquence de numérotation est en
 * retard sur les factures existantes. Rien n'est émis ; une nouvelle tentative réserve le numéro suivant.
 */
public class NumeroDeFactureDejaPris extends RuntimeException {

    private final String numero;

    public NumeroDeFactureDejaPris(String numero) {
        super("Le numero de facture " + numero + " est deja pris : la sequence de numerotation du centre est en retard sur les "
                + "factures existantes");
        this.numero = numero;
    }

    public String numero() {
        return numero;
    }
}
