package com.hemodialyse.backend.contexts.organisation.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.util.List;

/**
 * Port de sortie des référentiels partagés orphelins (aucun contexte propriétaire) :
 * payeurs, agences, caisses, forfaits, états patient (les transporteurs et les catégories de
 * transport appartiennent à patient-couverture depuis RF1, ADR 0122). Lecture seule, toujours filtrée par {@code center_id}
 * (règle multi-centre, AGENTS.md §2).
 */
public interface ReferentielsCommunsRepository {

    List<EntreeReferentiel> centresPayeurs(CentreId centreId);

    List<DetailPayeur> centresPayeursDetails(CentreId centreId);

    List<EntreeReferentiel> agences(CentreId centreId);

    List<EntreeReferentiel> caisses(CentreId centreId);

    List<EntreeReferentiel> forfaits(CentreId centreId);
}
