package com.hemodialyse.backend.contexts.patientcouverture.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.PagedResult;

import java.util.List;
import java.util.Optional;

public interface PecRepository {
    Optional<Pec> trouver(PecId id, CentreId centreId);

    void sauvegarder(Pec pec);

    void supprimer(PecId id, CentreId centreId);

    PagedResult<Pec> lister(CentreId centreId, int page, int size);

    List<Pec> listerParPatient(PatientId patientId, CentreId centreId);

    List<Pec> listerTous(CentreId centreId);
}