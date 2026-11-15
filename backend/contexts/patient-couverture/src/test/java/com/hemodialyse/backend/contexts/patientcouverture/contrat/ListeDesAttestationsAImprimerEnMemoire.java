package com.hemodialyse.backend.contexts.patientcouverture.contrat;

import com.hemodialyse.backend.contexts.patientcouverture.application.ListeDesAttestationsAImprimer;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Double en mémoire de la liste des ouvertures de droit à imprimer : bornée au centre, patient du même centre, date de fin décroissante. */
public class ListeDesAttestationsAImprimerEnMemoire implements ListeDesAttestationsAImprimer {

    private record Posee(CentreId centreDuPatient, AttestationImprimee attestation) {
    }

    private final Map<CentreId, List<Posee>> parCentre = new HashMap<>();

    public void poser(CentreId centre, AttestationImprimee attestation) {
        poserPourUnPatientDuCentre(centre, centre, attestation);
    }

    public void poserPourUnPatientDuCentre(CentreId centre, CentreId centreDuPatient, AttestationImprimee attestation) {
        parCentre.computeIfAbsent(centre, c -> new ArrayList<>()).add(new Posee(centreDuPatient, attestation));
    }

    @Override
    public List<AttestationImprimee> duCentre(CentreId centre) {
        return parCentre.getOrDefault(centre, List.of()).stream()
                .filter(p -> p.centreDuPatient().equals(centre))
                .map(Posee::attestation)
                .sorted(Comparator.comparing(AttestationImprimee::dateFin, Comparator.nullsLast(Comparator.<LocalDate>naturalOrder())).reversed())
                .toList();
    }
}
