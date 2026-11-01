package com.hemodialyse.backend.contexts.pilotagedirection.application;

import com.hemodialyse.backend.contexts.pilotagedirection.domain.AlerteDirection;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.AlerteDirectionId;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.TypeAlerte;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.PagedResult;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Port d'entrée (use cases) de l'agrégat {@code AlerteDirection} (slice PD3).
 * Cas d'usage : {@code GenererAlerte}, {@code ConsulterAlertesDuCentre}, {@code AcquitterAlerte}.
 */
public interface AlerteDirectionUseCases {

    AlerteDirectionId generer(GenererAlerteCommande commande, Instant maintenant);

    AlerteDirection acquitter(AlerteDirectionId alerteId, CentreId centreId, Instant maintenant);

    AlerteDirection consulter(AlerteDirectionId alerteId, CentreId centreId);

    PagedResult<AlerteDirection> listerParCentre(CentreId centreId, int page, int size);

    record GenererAlerteCommande(CentreId centreId, TypeAlerte type, String seuil,
                                 String message, LocalDate date) {
    }
}
