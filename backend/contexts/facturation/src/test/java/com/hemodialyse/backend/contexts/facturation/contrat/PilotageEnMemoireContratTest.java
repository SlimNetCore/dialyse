package com.hemodialyse.backend.contexts.facturation.contrat;

import com.hemodialyse.backend.contexts.facturation.application.pilotage.LectureDeLaSynthese;
import com.hemodialyse.backend.contexts.facturation.application.pilotage.LectureDuTableauDeBord;
import com.hemodialyse.backend.contexts.facturation.doubles.PilotageEnMemoire;
import com.hemodialyse.backend.contexts.facturation.doubles.PilotageEnMemoire.Facture;
import com.hemodialyse.backend.contexts.facturation.publishedlanguage.SyntheseFinanciereDesCentres;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Le double en mémoire passe le contrat partagé des lectures de pilotage.
 */
class PilotageEnMemoireContratTest extends PilotageContrat {

    private final PilotageEnMemoire memoire = new PilotageEnMemoire();

    @Override
    protected LectureDuTableauDeBord tableau() {
        return memoire;
    }

    @Override
    protected LectureDeLaSynthese synthese() {
        return memoire;
    }

    @Override
    protected SyntheseFinanciereDesCentres finance() {
        return memoire;
    }

    @Override
    protected void poserLaFacture(Facture facture) {
        memoire.poserLaFacture(facture);
    }

    @Override
    protected void poserLaSeance(UUID centre, UUID facture, LocalDate date, String statut) {
        memoire.poserLaSeance(centre, facture, date, statut);
    }

    @Override
    protected void poserLeReglement(UUID centre, UUID facture, String montant) {
        memoire.poserLeReglement(facture, new BigDecimal(montant));
    }
}
