package com.hemodialyse.backend.contexts.dossiermedical.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.PagedResult;

import java.util.Optional;
import java.util.UUID;

/**
 * Port de sortie du agr\u00e9gat {@code DemandeExamen}. Seule la couche d'infrastructure fournit une impl\u00e9mentation.
 */
public interface DemandeExamenRepository {

    void save(DemandeExamen demande);

    Optional<DemandeExamen> trouver(DemandeExamenId id, CentreId centreId);

    PagedResult<DemandeExamen> listerParPatient(CentreId centreId, UUID patientId, int page, int size);
}
