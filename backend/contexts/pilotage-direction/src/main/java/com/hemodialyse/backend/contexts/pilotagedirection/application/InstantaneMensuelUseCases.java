package com.hemodialyse.backend.contexts.pilotagedirection.application;

import com.hemodialyse.backend.contexts.pilotagedirection.domain.InstantaneMensuel;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.InstantaneMensuelId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.Mois;
import com.hemodialyse.backend.contexts.sharedkernel.domain.PagedResult;

import java.time.Instant;

/**
 * Port d'entrée (use cases) de l'agrégat {@code InstantaneMensuel} (slice PD2).
 * Cas d'usage : {@code CapturerInstantaneMensuel}, {@code ConsulterInstantane}.
 */
public interface InstantaneMensuelUseCases {

    InstantaneMensuelId capturer(CapturerInstantaneCommande commande, Instant maintenant);

    InstantaneMensuel consulter(InstantaneMensuelId instantaneId, CentreId centreId);

    PagedResult<InstantaneMensuel> listerParCentre(CentreId centreId, int page, int size);

    record CapturerInstantaneCommande(CentreId centreId, Mois mois, String donnees) {
    }
}
