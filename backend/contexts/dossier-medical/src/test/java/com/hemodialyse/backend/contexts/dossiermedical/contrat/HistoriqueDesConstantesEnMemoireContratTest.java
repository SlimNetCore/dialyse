package com.hemodialyse.backend.contexts.dossiermedical.contrat;

/** Le contrat du port rejoué sur le double en mémoire. */
class HistoriqueDesConstantesEnMemoireContratTest extends HistoriqueDesConstantesContrat {

    @Override
    protected Banc nouveauBanc() {
        return new HistoriqueDesConstantesEnMemoire();
    }
}
