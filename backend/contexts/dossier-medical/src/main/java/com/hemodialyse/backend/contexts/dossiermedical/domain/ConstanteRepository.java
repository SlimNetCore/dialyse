package com.hemodialyse.backend.contexts.dossiermedical.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.PagedResult;

import java.util.UUID;

public interface ConstanteRepository {
    void save(Constante constante);

    PagedResult<Constante> findByPatient(CentreId centreId, UUID patientId, int page, int size);
}