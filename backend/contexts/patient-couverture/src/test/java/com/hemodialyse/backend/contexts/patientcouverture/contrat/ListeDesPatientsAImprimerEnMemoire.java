package com.hemodialyse.backend.contexts.patientcouverture.contrat;

import com.hemodialyse.backend.contexts.patientcouverture.application.ListeDesPatientsAImprimer;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Double en mémoire de la liste des patients à imprimer : bornée au centre, triée par nom puis prénom (points de code). */
public class ListeDesPatientsAImprimerEnMemoire implements ListeDesPatientsAImprimer {

    private final Map<CentreId, List<PatientImprime>> parCentre = new HashMap<>();

    public void poser(CentreId centre, PatientImprime patient) {
        parCentre.computeIfAbsent(centre, c -> new ArrayList<>()).add(patient);
    }

    @Override
    public List<PatientImprime> duCentre(CentreId centre) {
        return parCentre.getOrDefault(centre, List.of()).stream()
                .sorted(Comparator.comparing(PatientImprime::nom).thenComparing(PatientImprime::prenom))
                .toList();
    }
}
