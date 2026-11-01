package com.hemodialyse.backend.contexts.dossiermedical.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.PagedResult;

import java.util.Optional;
import java.util.UUID;

/**
 * Port de sortie du agrégat {@code Serologie}. Seule la couche d'infrastructure fournit une implémentation.
 * Toutes les requêtes sont filtrées par {@code center_id} (règle multi-centre, AGENTS.md §2).
 */
public interface SerologieRepository {

    void save(Serologie serologie);

    Optional<Serologie> trouver(SerologieId id, CentreId centreId);

    PagedResult<Serologie> listerParPatient(CentreId centreId, UUID patientId, int page, int size);
}
