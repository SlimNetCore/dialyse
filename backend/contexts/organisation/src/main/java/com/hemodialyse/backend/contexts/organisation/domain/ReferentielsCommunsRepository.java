package com.hemodialyse.backend.contexts.organisation.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.util.List;

/**
 * Port de sortie des référentiels partagés orphelins (aucun contexte propriétaire) :
 * payeurs, agences, caisses, transporteurs, catégories de transport, forfaits,
 * états patient. Lecture seule, toujours filtrée par {@code center_id}
 * (règle multi-centre, AGENTS.md §2).
 */
public interface ReferentielsCommunsRepository {

    List<EntreeReferentiel> centresPayeurs(CentreId centreId);

    List<DetailPayeur> centresPayeursDetails(CentreId centreId);

    List<EntreeReferentiel> agences(CentreId centreId);

    List<EntreeReferentiel> caisses(CentreId centreId);

    List<EntreeReferentiel> transporteurs(CentreId centreId);

    List<EntreeReferentiel> categoriesTransport(CentreId centreId);

    List<EntreeReferentiel> forfaits(CentreId centreId);
}
