package com.hemodialyse.backend.contexts.patientcouverture.contrat;

import com.hemodialyse.backend.contexts.patientcouverture.application.AttestationAImprimer;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Double en mémoire de l'ouverture de droit à imprimer : bornée au centre, patient du même centre, date de fin décroissante. */
public class AttestationAImprimerEnMemoire implements AttestationAImprimer {

    private record Posee(CentreId centre, CentreId centreDuPatient, UUID patient, UUID attestation, AttestationImprimable valeurs) {
    }

    private final List<Posee> posees = new ArrayList<>();

    public void poser(CentreId centre, CentreId centreDuPatient, UUID patient, UUID attestation, AttestationImprimable valeurs) {
        posees.add(new Posee(centre, centreDuPatient, patient, attestation, valeurs));
    }

    private List<Posee> duCentre(CentreId centre) {
        return posees.stream().filter(p -> p.centre().equals(centre) && p.centreDuPatient().equals(centre)).toList();
    }

    @Override
    public Optional<AttestationImprimable> parIdentifiant(CentreId centre, UUID attestation) {
        return duCentre(centre).stream().filter(p -> p.attestation().equals(attestation)).findFirst().map(Posee::valeurs);
    }

    @Override
    public Optional<AttestationImprimable> laPlusRecenteDuPatient(CentreId centre, UUID patient) {
        return duCentre(centre).stream().filter(p -> p.patient().equals(patient))
                .max(Comparator.comparing((Posee p) -> p.valeurs().dateFin(), Comparator.nullsFirst(Comparator.<LocalDate>naturalOrder())))
                .map(Posee::valeurs);
    }
}
