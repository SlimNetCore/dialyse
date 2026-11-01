package com.hemodialyse.backend.contexts.dossiermedical.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.PagedResult;

import java.util.UUID;

public interface PrescriptionRepository {
    void save(Prescription prescription);

    PagedResult<Prescription> findByPatient(CentreId centreId, UUID patientId, int page, int size);
}