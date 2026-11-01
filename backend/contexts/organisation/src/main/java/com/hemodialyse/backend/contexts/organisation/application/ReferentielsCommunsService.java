package com.hemodialyse.backend.contexts.organisation.application;

import com.hemodialyse.backend.contexts.organisation.domain.DetailPayeur;
import com.hemodialyse.backend.contexts.organisation.domain.EntreeReferentiel;
import com.hemodialyse.backend.contexts.organisation.domain.ReferentielsCommunsRepository;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.util.List;

/**
 * Implémentation des use cases {@link ReferentielsCommunsUseCases}.
 * Simple délégation au port de sortie ; les {@code etatsPatients} sont une liste
 * statique (comme le legacy), sans accès base.
 */
public class ReferentielsCommunsService implements ReferentielsCommunsUseCases {

    private final ReferentielsCommunsRepository referentiels;

    public ReferentielsCommunsService(ReferentielsCommunsRepository referentiels) {
        this.referentiels = referentiels;
    }

    @Override
    public List<EntreeReferentiel> centresPayeurs(CentreId centreId) {
        return referentiels.centresPayeurs(centreId);
    }

    @Override
    public List<DetailPayeur> centresPayeursDetails(CentreId centreId) {
        return referentiels.centresPayeursDetails(centreId);
    }

    @Override
    public List<EntreeReferentiel> agences(CentreId centreId) {
        return referentiels.agences(centreId);
    }

    @Override
    public List<EntreeReferentiel> caisses(CentreId centreId) {
        return referentiels.caisses(centreId);
    }

    @Override
    public List<EntreeReferentiel> transporteurs(CentreId centreId) {
        return referentiels.transporteurs(centreId);
    }

    @Override
    public List<EntreeReferentiel> categoriesTransport(CentreId centreId) {
        return referentiels.categoriesTransport(centreId);
    }

    @Override
    public List<EntreeReferentiel> forfaits(CentreId centreId) {
        return referentiels.forfaits(centreId);
    }

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
