package com.hemodialyse.backend.domain.infirmier.model;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Remplacement ponctuel : à une date donnée, un infirmier renforce une salle sur un créneau, éventuellement à la place
 * d'un collègue absent ({@code remplaceId}, facultatif).
 */
public record RemplacementInfirmier(
        UUID id,
        UUID centerId,
        LocalDate date,
        UUID salleId,
        UUID creneauId,
        UUID infirmierId,
        UUID remplaceId
) {
    public RemplacementInfirmier {
        if (centerId == null) throw new IllegalArgumentException("Centre requis");
        if (date == null) throw new IllegalArgumentException("Date requise");
        if (salleId == null) throw new IllegalArgumentException("Salle requise");
        if (creneauId == null) throw new IllegalArgumentException("Créneau requis");
        if (infirmierId == null) throw new IllegalArgumentException("Infirmier requis");
        if (infirmierId.equals(remplaceId))
            throw new IllegalArgumentException("Un infirmier ne peut pas se remplacer lui-même");
    }

    public static RemplacementInfirmier creer(UUID centerId, LocalDate date, UUID salleId, UUID creneauId,
                                              UUID infirmierId, UUID remplaceId) {
        return new RemplacementInfirmier(UUID.randomUUID(), centerId, date, salleId, creneauId, infirmierId, remplaceId);
    }
}
