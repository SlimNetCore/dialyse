package com.hemodialyse.backend.contexts.facturation.contrat;

import com.hemodialyse.backend.contexts.facturation.doubles.SeancesEnMemoire;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.facturation.domain.facture.LiaisonDesSeances;
import com.hemodialyse.backend.contexts.facturation.domain.seance.SeancesDeFacturation;
import com.hemodialyse.backend.contexts.facturation.domain.simulation.ForfaitApplique;
import com.hemodialyse.backend.contexts.facturation.domain.simulation.ForfaitsDuCentre;
import com.hemodialyse.backend.contexts.facturation.domain.simulation.PriseEnChargeTarifee;
import com.hemodialyse.backend.contexts.facturation.domain.simulation.SeancesFacturables;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Le double en mémoire passe le contrat partagé des séances.
 */
class SeancesEnMemoireContratTest extends SeancesContrat {

    private final SeancesEnMemoire memoire = new SeancesEnMemoire();

    @Override
    protected SeancesDeFacturation seances() {
        return memoire;
    }

    @Override
    protected SeancesFacturables facturables() {
        return memoire;
    }

    @Override
    protected ForfaitsDuCentre forfaits() {
        return memoire;
    }

    @Override
    protected LiaisonDesSeances liaison() {
        return memoire;
    }

    @Override
    protected void payeur(UUID patient, CentreId centre, UUID centrePayeur, UUID agence) {
        memoire.patient(patient, centre, "P-001", "Amrani", "Lina", "PERMANENT", "ASS-P-001", centrePayeur, agence);
    }

    @Override
    protected String statutDe(UUID seance) {
        return memoire.statutDe(seance);
    }

    @Override
    protected UUID factureDe(UUID seance) {
        return memoire.factureDe(seance);
    }

    @Override
    protected void patient(UUID id, CentreId centre, String code, String nom, String prenom, String etat) {
        memoire.patient(id, centre, code, nom, prenom, etat);
    }

    @Override
    protected void forfait(UUID id, CentreId centre, String libelle, String prix) {
        memoire.forfait(id, centre, libelle, new BigDecimal(prix));
    }

    @Override
    protected void priseEnCharge(UUID patient, CentreId centre, PriseEnChargeTarifee pec) {
        memoire.priseEnCharge(patient, pec);
    }

    @Override
    protected void seance(UUID id, CentreId centre, UUID patient, LocalDate date, String statut, boolean facturee) {
        memoire.seance(id, centre, patient, date, statut, facturee);
    }

    @Override
    protected void surcharge(UUID seance, ForfaitApplique forfait) {
        memoire.surcharge(seance, forfait);
    }
}
