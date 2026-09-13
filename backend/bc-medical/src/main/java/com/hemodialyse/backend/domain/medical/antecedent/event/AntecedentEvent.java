package com.hemodialyse.backend.domain.medical.antecedent.event;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Domain Events de l'agrégat {@code Antecedent} (AGENTS.md §14). Purement informatifs pour
 * l'instant — collectés par l'agrégat via {@code pullEvents()}, aucun bus n'existe encore
 * dans le projet ; ils documentent l'intention métier et servent de point d'accroche pour
 * une intégration future (journal d'audit, notifications).
 */
public sealed interface AntecedentEvent {

    UUID antecedentId();

    OffsetDateTime at();

    record AntecedentAjoute(UUID antecedentId, UUID patientId, OffsetDateTime at) implements AntecedentEvent {
    }

    record AntecedentResolu(UUID antecedentId, UUID patientId, OffsetDateTime at) implements AntecedentEvent {
    }
}
