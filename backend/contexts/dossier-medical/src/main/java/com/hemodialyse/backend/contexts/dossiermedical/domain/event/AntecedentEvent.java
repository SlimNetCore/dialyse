package com.hemodialyse.backend.contexts.dossiermedical.domain.event;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Domain Events de l'agrégat {@link com.hemodialyse.backend.contexts.dossiermedical.domain.Antecedent}
 * (AGENTS.md §14). Collectés par l'agrégat via {@code pullEvents()}, publiés par la couche application.
 */
public sealed interface AntecedentEvent permits AntecedentEvent.AntecedentAjoute, AntecedentEvent.AntecedentResolu {

    UUID antecedentId();

    OffsetDateTime at();

    record AntecedentAjoute(UUID antecedentId, UUID patientId, OffsetDateTime at) implements AntecedentEvent {
    }

    record AntecedentResolu(UUID antecedentId, UUID patientId, OffsetDateTime at) implements AntecedentEvent {
    }
}
