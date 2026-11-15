package com.hemodialyse.backend.contexts.patientcouverture.contrat;

import com.hemodialyse.backend.contexts.patientcouverture.application.PriseEnChargeAImprimer;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Double en mémoire de la prise en charge à imprimer : bornée au centre, patient du même centre, date de fin de demande décroissante. */
public class PriseEnChargeAImprimerEnMemoire implements PriseEnChargeAImprimer {

    private record Posee(CentreId centre, CentreId centreDuPatient, UUID patient, UUID priseEnCharge, PriseEnChargeImprimable valeurs) {
    }

    private final List<Posee> posees = new ArrayList<>();

    public void poser(CentreId centre, CentreId centreDuPatient, UUID patient, UUID priseEnCharge, PriseEnChargeImprimable valeurs) {
        posees.add(new Posee(centre, centreDuPatient, patient, priseEnCharge, valeurs));
    }

    private List<Posee> duCentre(CentreId centre) {
        return posees.stream().filter(p -> p.centre().equals(centre) && p.centreDuPatient().equals(centre)).toList();
    }

    @Override
    public Optional<PriseEnChargeImprimable> parIdentifiant(CentreId centre, UUID priseEnCharge) {
        return duCentre(centre).stream().filter(p -> p.priseEnCharge().equals(priseEnCharge)).findFirst().map(Posee::valeurs);
    }

    @Override
    public Optional<PriseEnChargeImprimable> laPlusRecenteDuPatient(CentreId centre, UUID patient) {
        return duCentre(centre).stream().filter(p -> p.patient().equals(patient))
                .max(Comparator.comparing((Posee p) -> p.valeurs().dateFinDemande(), Comparator.nullsFirst(Comparator.<LocalDate>naturalOrder())))
                .map(Posee::valeurs);
    }
}
