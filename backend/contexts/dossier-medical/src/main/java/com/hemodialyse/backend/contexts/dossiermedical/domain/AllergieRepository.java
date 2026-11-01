package com.hemodialyse.backend.contexts.dossiermedical.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.PagedResult;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Port de sortie de l'agrégat {@link Allergie}. Seule la couche d'infrastructure fournit une
 * implémentation. Toutes les requêtes sont filtrées par {@code center_id} (règle multi-centre,
 * AGENTS.md §2).
 */
public interface AllergieRepository {

    void save(Allergie allergie);

    Optional<Allergie> trouver(AllergieId id, CentreId centreId);

    List<Allergie> findByPatientId(UUID patientId, CentreId centreId);

    PagedResult<Allergie> findPagedByPatientId(UUID patientId, CentreId centreId, int page, int size);

    void deleteById(AllergieId id, CentreId centreId);
}
