package com.hemodialyse.backend.contexts.facturation.contrat;

import com.hemodialyse.backend.contexts.comptabilite.publishedlanguage.FacturesEmises;
import com.hemodialyse.backend.contexts.comptabilite.publishedlanguage.ReglementsEncaisses;
import com.hemodialyse.backend.contexts.facturation.doubles.LecturesPubliquesEnMemoire;
import com.hemodialyse.backend.contexts.facturation.publishedlanguage.FacturationDuPatient;

import java.util.UUID;

/**
 * Le double en mémoire passe le contrat partagé des lectures publiées.
 */
class LecturesPubliquesEnMemoireContratTest extends LecturesPubliquesContrat {

    private final LecturesPubliquesEnMemoire memoire = new LecturesPubliquesEnMemoire();

    @Override
    protected FacturationDuPatient facturationDuPatient() {
        return memoire;
    }

    @Override
    protected FacturesEmises facturesEmises() {
        return memoire;
    }

    @Override
    protected ReglementsEncaisses reglementsEncaisses() {
        return memoire;
    }

    @Override
    protected void poserLaFacture(UUID id, UUID centre, UUID patient, String numero, String debut, String fin,
                                  String dateFacturation, String ht, String tva, String ttc, UUID payeur) {
        memoire.poserLaFacture(id, centre, patient, numero, debut, fin, dateFacturation, ht, tva, ttc, payeur);
    }

    @Override
    protected void poserLeReglement(UUID id, UUID centre, UUID facture, String montant, String date, String mode, String auteur) {
        memoire.poserLeReglement(id, centre, facture, montant, date, mode, auteur);
    }
}
