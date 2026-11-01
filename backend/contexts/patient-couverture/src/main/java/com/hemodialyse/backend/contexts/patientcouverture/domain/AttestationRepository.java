package com.hemodialyse.backend.contexts.patientcouverture.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.PagedResult;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface AttestationRepository {
    Optional<AttestationDroit> trouver(AttestationDroitId id, CentreId centreId);

    void sauvegarder(AttestationDroit attestation);

    void supprimer(AttestationDroitId id, CentreId centreId);

    PagedResult<AttestationDroit> lister(CentreId centreId, int page, int size);

    List<AttestationDroit> listerParPatient(PatientId patientId, CentreId centreId);

    boolean existsValidAt(CentreId centreId, PatientId patientId, LocalDate date);
}