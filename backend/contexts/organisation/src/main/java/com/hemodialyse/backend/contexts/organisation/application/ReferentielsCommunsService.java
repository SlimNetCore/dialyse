package com.hemodialyse.backend.contexts.organisation.application;

import com.hemodialyse.backend.contexts.organisation.domain.EntreeReferentiel;

import java.util.List;

/**
 * Implémentation des use cases {@link ReferentielsCommunsUseCases}.
 * Les {@code etatsPatients} sont une liste statique (comme le legacy), sans accès base. Les payeurs (caisses, agences, centres
 * payeurs) sont servis par Patient et couverture depuis RF3c (ADR 0128).
 */
public class ReferentielsCommunsService implements ReferentielsCommunsUseCases {

    @Override
    public List<EntreeReferentiel> etatsPatients() {
        return List.of(
                new EntreeReferentiel("PERMANENT", "PERMANENT", "PERMANENT", null, null, null, null),
                new EntreeReferentiel("OCCASIONNEL", "OCCASIONNEL", "OCCASIONNEL", null, null, null, null),
                new EntreeReferentiel("VACANCIER_LOCAL", "VACANCIER_LOCAL", "VACANCIER_LOCAL", null, null, null, null),
                new EntreeReferentiel("VACANCIER_ETRANGER", "VACANCIER_ETRANGER", "VACANCIER_ETRANGER", null, null, null, null),
                new EntreeReferentiel("TRANSFERE", "TRANSFERE", "TRANSFERE", null, null, null, null),
                new EntreeReferentiel("DECEDE", "DECEDE", "DECEDE", null, null, null, null),
                new EntreeReferentiel("GREFFE", "GREFFE", "GREFFE", null, null, null, null),
                new EntreeReferentiel("GUERRI", "GUERRI", "GUERRI", null, null, null, null));
    }
}
