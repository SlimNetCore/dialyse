package com.hemodialyse.backend.contexts.dossiermedical.domain.event;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Domain Events de l'agrégat {@link com.hemodialyse.backend.contexts.dossiermedical.domain.Allergie}
 * (AGENTS.md §14). Collectés par l'agrégat via {@code pullEvents()}, publiés par la couche application.
 */
public sealed interface AllergieEvent permits AllergieEvent.AllergieDeclaree, AllergieEvent.AllergieResolue {

    UUID allergieId();

    OffsetDateTime at();

    record AllergieDeclaree(UUID allergieId, UUID patientId, OffsetDateTime at) implements AllergieEvent {
    }

    record AllergieResolue(UUID allergieId, UUID patientId, OffsetDateTime at) implements AllergieEvent {
    }
}
