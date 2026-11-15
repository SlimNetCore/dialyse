package com.hemodialyse.backend.contexts.patientcouverture.contrat;

import com.hemodialyse.backend.contexts.patientcouverture.application.FicheSignaletiqueAImprimer;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Double en mémoire de la fiche signalétique à imprimer : bornée au centre du patient. */
public class FicheSignaletiqueAImprimerEnMemoire implements FicheSignaletiqueAImprimer {

    private record Posee(CentreId centre, UUID patient, FicheImprimable fiche) {
    }

    private final List<Posee> posees = new ArrayList<>();

    public void poser(CentreId centre, UUID patient, FicheImprimable fiche) {
        posees.add(new Posee(centre, patient, fiche));
    }

    @Override
    public Optional<FicheImprimable> duPatient(CentreId centre, UUID patient) {
        return posees.stream().filter(p -> p.centre().equals(centre) && p.patient().equals(patient)).findFirst().map(Posee::fiche);
    }
}
