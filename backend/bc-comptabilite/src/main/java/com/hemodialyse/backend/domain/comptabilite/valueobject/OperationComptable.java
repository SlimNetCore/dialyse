package com.hemodialyse.backend.domain.comptabilite.valueobject;

/**
 * Opérations de gestion qui produisent une écriture. Chaque centre choisit le journal de chacune ; {@link #journalParDefaut}
 * est celui d'un centre qui n'a rien paramétré.
 */
public enum OperationComptable {
    /**
     * Facture émise.
     */
    VENTE(JournalCode.VE),
    /**
     * Règlement encaissé en banque.
     */
    REGLEMENT_BANQUE(JournalCode.BQ),
    /**
     * Règlement encaissé en caisse.
     */
    REGLEMENT_CAISSE(JournalCode.CA),
    /**
     * Bon de réception validé : entrée en stock.
     */
    STOCK_RECEPTION(JournalCode.AC),
    /**
     * Sorties de stock d'une journée (consommations de séance, sorties manuelles).
     */
    STOCK_SORTIE(JournalCode.ST),
    /**
     * Écarts constatés à la clôture d'un inventaire.
     */
    STOCK_INVENTAIRE(JournalCode.ST);

    private final JournalCode journalParDefaut;

    OperationComptable(JournalCode journalParDefaut) {
        this.journalParDefaut = journalParDefaut;
    }

    public JournalCode journalParDefaut() {
        return journalParDefaut;
    }
}
