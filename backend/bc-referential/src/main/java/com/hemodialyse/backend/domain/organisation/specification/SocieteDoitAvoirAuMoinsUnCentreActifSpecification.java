package com.hemodialyse.backend.domain.organisation.specification;

import com.hemodialyse.backend.domain.organisation.model.Centre;

import java.util.Collection;

/**
 * Specification — une société active doit toujours conserver au moins un centre actif.
 * <p>
 * Évaluée par l'agrégat {@code Societe} avant toute désactivation ou tout retrait de centre.
 */
public final class SocieteDoitAvoirAuMoinsUnCentreActifSpecification {

    public boolean isSatisfiedBy(Collection<Centre> centres) {
        return centres != null && centres.stream().anyMatch(Centre::actif);
    }
}
