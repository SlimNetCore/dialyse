package com.hemodialyse.backend.contexts.organisation.application;

import com.hemodialyse.backend.contexts.organisation.domain.EntreeReferentiel;

import java.util.List;

/**
 * Port d'entrée (use cases) des référentiels partagés orphelins (slice O5) qui restent à l'Organisation : les états patient. Les
 * payeurs (RF3c, ADR 0128), les transporteurs (RF1) et les forfaits (RF2) sont servis par Patient et couverture.
 */
public interface ReferentielsCommunsUseCases {

    List<EntreeReferentiel> etatsPatients();
}
