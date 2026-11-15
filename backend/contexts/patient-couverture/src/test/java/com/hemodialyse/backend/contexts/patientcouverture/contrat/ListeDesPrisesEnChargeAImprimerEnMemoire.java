package com.hemodialyse.backend.contexts.patientcouverture.contrat;

import com.hemodialyse.backend.contexts.patientcouverture.application.ListeDesPrisesEnChargeAImprimer;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Double en mémoire de la liste des prises en charge à imprimer : bornée au centre, patient du même centre, date de fin de demande décroissante. */
public class ListeDesPrisesEnChargeAImprimerEnMemoire implements ListeDesPrisesEnChargeAImprimer {

    private record Posee(CentreId centreDuPatient, PriseEnChargeImprimee priseEnCharge) {
    }

    private final Map<CentreId, List<Posee>> parCentre = new HashMap<>();

    public void poser(CentreId centre, PriseEnChargeImprimee priseEnCharge) {
        poserPourUnPatientDuCentre(centre, centre, priseEnCharge);
    }

    public void poserPourUnPatientDuCentre(CentreId centre, CentreId centreDuPatient, PriseEnChargeImprimee priseEnCharge) {
        parCentre.computeIfAbsent(centre, c -> new ArrayList<>()).add(new Posee(centreDuPatient, priseEnCharge));
    }

    @Override
    public List<PriseEnChargeImprimee> duCentre(CentreId centre) {
        return parCentre.getOrDefault(centre, List.of()).stream()
                .filter(p -> p.centreDuPatient().equals(centre))
                .map(Posee::priseEnCharge)
                .sorted(Comparator.comparing(PriseEnChargeImprimee::dateFinDemande,
                        Comparator.nullsFirst(Comparator.<LocalDate>naturalOrder())).reversed())
                .toList();
    }
}
