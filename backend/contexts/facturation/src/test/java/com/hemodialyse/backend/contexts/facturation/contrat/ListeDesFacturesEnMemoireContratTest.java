package com.hemodialyse.backend.contexts.facturation.contrat;

import com.hemodialyse.backend.contexts.facturation.application.consultation.ListeDesFactures;
import com.hemodialyse.backend.contexts.facturation.doubles.ListeDesFacturesEnMemoire;
import com.hemodialyse.backend.contexts.facturation.domain.facture.LigneFacture;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Le double en mémoire passe le contrat de la lecture des factures émises. */
class ListeDesFacturesEnMemoireContratTest extends ListeDesFacturesContrat {

    private final ListeDesFacturesEnMemoire memoire = new ListeDesFacturesEnMemoire();

    @Override
    protected ListeDesFactures liste() {
        return memoire;
    }

    @Override
    protected void poserLaFacture(CentreId centre, UUID id, String numero, LocalDate date, String ttc) {
        memoire.poserLaFacture(centre, id, numero, date, ttc);
    }

    @Override
    protected void poserLaLigne(CentreId centre, UUID facture, UUID forfait, String libelle, String prix, int nombre) {
        memoire.poserLaLigne(centre, facture, UUID.randomUUID(), new LigneFacture(forfait, libelle, new BigDecimal(prix), nombre));
    }

    @Override
    protected void poserLeReglement(CentreId centre, UUID facture, String montant) {
        memoire.poserLeReglement(centre, facture, montant);
    }
}
