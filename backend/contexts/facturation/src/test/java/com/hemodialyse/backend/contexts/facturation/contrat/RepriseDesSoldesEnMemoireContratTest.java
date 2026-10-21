package com.hemodialyse.backend.contexts.facturation.contrat;

import com.hemodialyse.backend.contexts.facturation.doubles.RepriseDesSoldesEnMemoire;
import com.hemodialyse.backend.contexts.facturation.publishedlanguage.RepriseDesSoldes;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Le double en mémoire passe le contrat partagé de la reprise des soldes.
 */
class RepriseDesSoldesEnMemoireContratTest extends RepriseDesSoldesContrat {

    private final RepriseDesSoldesEnMemoire memoire = new RepriseDesSoldesEnMemoire();

    @Override
    protected RepriseDesSoldes reprise() {
        return memoire;
    }

    @Override
    protected void poserUnReglementDeLaPlateforme(UUID centre, UUID facture, String montant) {
        memoire.poserUnReglementDeLaPlateforme(facture);
    }

    @Override
    protected int lignes(UUID facture) {
        return memoire.lignes(facture);
    }

    @Override
    protected int reglementsDeReprise(UUID facture) {
        return memoire.reglementsDeReprise(facture);
    }

    @Override
    protected BigDecimal totalTtc(UUID facture) {
        return memoire.totalTtc(facture);
    }
}
