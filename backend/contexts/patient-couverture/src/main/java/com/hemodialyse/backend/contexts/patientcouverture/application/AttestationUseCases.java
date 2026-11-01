package com.hemodialyse.backend.contexts.patientcouverture.application;

import com.hemodialyse.backend.contexts.patientcouverture.domain.AttestationDroit;
import com.hemodialyse.backend.contexts.patientcouverture.domain.AttestationDroitId;
import com.hemodialyse.backend.contexts.patientcouverture.domain.PatientId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.PagedResult;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public interface AttestationUseCases {
    AttestationDroitId enregistrer(EnregistrerAttestationCommande commande, Instant maintenant);

    AttestationDroit consulter(AttestationDroitId id, CentreId centreId);

    PagedResult<AttestationDroit> lister(CentreId centreId, int page, int size);

    List<AttestationDroit> listerParPatient(PatientId patientId, CentreId centreId);

    void supprimer(AttestationDroitId id, CentreId centreId);

    record EnregistrerAttestationCommande(PatientId patientId, CentreId centreId, LocalDate dateDebut,
                                          LocalDate dateFin) {
    }
}