package com.hemodialyse.backend.contexts.organisation.application;

import com.hemodialyse.backend.contexts.organisation.domain.DetailPayeur;
import com.hemodialyse.backend.contexts.organisation.domain.EntreeReferentiel;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.util.List;

/**
 * Port d'entrée (use cases) des référentiels partagés orphelins (slice O5).
 * Lecture seule pour listes déroulantes, toujours filtrée par centre.
 */
public interface ReferentielsCommunsUseCases {

    List<EntreeReferentiel> centresPayeurs(CentreId centreId);

    List<DetailPayeur> centresPayeursDetails(CentreId centreId);

    List<EntreeReferentiel> agences(CentreId centreId);

    List<EntreeReferentiel> caisses(CentreId centreId);

    List<EntreeReferentiel> forfaits(CentreId centreId);

    List<EntreeReferentiel> etatsPatients();
}
