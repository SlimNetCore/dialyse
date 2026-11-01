package com.hemodialyse.backend.contexts.dossiermedical.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.PagedResult;

import java.util.Optional;
import java.util.UUID;

/**
 * Port de sortie de l'agrégat {@code ObservationBiologique}. Seule la couche d'infrastructure
 * fournit une implémentation. Toutes les requêtes sont filtrées par {@code center_id}
 * (règle multi-centre, AGENTS.md §2).
 */
public interface ObservationBiologiqueRepository {

    void save(ObservationBiologique observation);

    Optional<ObservationBiologique> trouver(ObservationBiologiqueId id, CentreId centreId);

    PagedResult<ObservationBiologique> listerParPatient(CentreId centreId, UUID patientId, int page, int size);
}
