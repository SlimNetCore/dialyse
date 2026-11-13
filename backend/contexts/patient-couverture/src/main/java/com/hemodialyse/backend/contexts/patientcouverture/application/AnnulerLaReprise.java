package com.hemodialyse.backend.contexts.patientcouverture.application;

import com.hemodialyse.backend.contexts.patientcouverture.domain.DonneesCreeesParLeLot;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.AnnulationDeLaReprise;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Annulation d'un lot de reprise côté Patient et couverture (RP1, ADR 0169) : mêmes tables, mêmes requêtes qu'avant. L'application
 * orchestre ; la règle « assuré retenu hors du lot » est celle de {@link DonneesCreeesParLeLot}.
 */
public class AnnulerLaReprise implements AnnulationDeLaReprise {

    private final DonneesDeLaReprise donnees;

    public AnnulerLaReprise(DonneesDeLaReprise donnees) {
        this.donnees = donnees;
    }

    @Override
    public List<UUID> prisesEnChargeDuPatient(UUID patient) {
        return donnees.prisesEnChargeDuPatient(patient);
    }

    @Override
    public List<UUID> attestationsDuPatient(UUID patient) {
        return donnees.attestationsDuPatient(patient);
    }

    @Override
    public boolean assureRetenuHorsDuLot(UUID centre, String numeroAssurance, Set<String> affectationsDuLot, Set<String> patientsDuLot) {
        DonneesCreeesParLeLot lot = new DonneesCreeesParLeLot(affectationsDuLot, patientsDuLot);
        return donnees.affectationsDeLAssure(centre, numeroAssurance).stream()
                .anyMatch(a -> lot.affectationRetientLAssure(a.id(), a.patient()))
                || donnees.patientsDeLAssure(centre, numeroAssurance).stream().anyMatch(lot::patientRetientLAssure);
    }

    @Override
    public void supprimerLAffectation(UUID centre, UUID affectation) {
        donnees.supprimerLAffectation(centre, affectation);
    }

    @Override
    public void supprimerLePatient(UUID centre, UUID patient) {
        donnees.supprimerLesAffectationsDuPatient(centre, patient);
        donnees.supprimerLePatient(centre, patient);
    }

    @Override
    public void supprimerLAssure(UUID centre, String numeroAssurance) {
        donnees.supprimerLAssure(centre, numeroAssurance);
    }
}
