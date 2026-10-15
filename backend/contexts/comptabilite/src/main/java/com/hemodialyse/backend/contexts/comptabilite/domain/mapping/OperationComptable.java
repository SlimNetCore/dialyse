package com.hemodialyse.backend.contexts.comptabilite.domain.mapping;

import com.hemodialyse.backend.contexts.comptabilite.domain.journal.CodeJournal;

/**
 * Fait économique qui donne une écriture (RG-CPT-010). Chaque centre choisit le journal de chacune ; {@link #journalParDefaut}
 * est celui d'un centre qui n'a rien paramétré.
 */
public enum OperationComptable {
    /**
     * Facture émise.
     */
    VENTE(CodeJournal.VE),
    /**
     * Règlement encaissé en banque.
     */
    REGLEMENT_BANQUE(CodeJournal.BQ),
    /**
     * Règlement encaissé en caisse.
     */
    REGLEMENT_CAISSE(CodeJournal.CA),
    /**
     * Bon de réception validé : entrée en stock.
     */
    STOCK_RECEPTION(CodeJournal.AC),
    /**
     * Sorties de stock d'une journée (consommations de séance, sorties manuelles).
     */
    STOCK_SORTIE(CodeJournal.ST),
    /**
     * Écarts constatés à la clôture d'un inventaire.
     */
    STOCK_INVENTAIRE(CodeJournal.ST);

    private final CodeJournal journalParDefaut;

    OperationComptable(CodeJournal journalParDefaut) {
        this.journalParDefaut = journalParDefaut;
    }

    /**
     * L'opération désignée par son nom exact (majuscules) ; toute autre saisie est refusée (400 observé).
     */
    public static OperationComptable de(String nom) {
        for (OperationComptable operation : values()) {
            if (operation.name().equals(nom)) {
                return operation;
            }
        }
        throw new IllegalArgumentException("Opération comptable inconnue : " + nom);
    }

    public CodeJournal journalParDefaut() {
        return journalParDefaut;
    }
}
