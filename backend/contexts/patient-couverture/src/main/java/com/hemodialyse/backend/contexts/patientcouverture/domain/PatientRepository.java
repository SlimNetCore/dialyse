package com.hemodialyse.backend.contexts.patientcouverture.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.PagedResult;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PatientRepository {
    Optional<Patient> trouver(PatientId id, CentreId centreId);

    void sauvegarder(Patient patient);

    PagedResult<Patient> lister(CentreId centreId, int page, int size);

    List<Patient> listerTous(CentreId centreId);
}