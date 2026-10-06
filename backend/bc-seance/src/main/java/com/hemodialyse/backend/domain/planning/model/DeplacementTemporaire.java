package com.hemodialyse.backend.domain.planning.model;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Déplacement d'un patient pour une seule séance datée (générateur en maintenance ce jour-là) : sa place habituelle ne
 * change pas, il dialyse ce jour-là sur un autre générateur, éventuellement dans une autre salle ou un autre créneau.
 */
public record DeplacementTemporaire(UUID id, UUID centerId, UUID patientId, LocalDate date, UUID salleId,
                                    UUID creneauId, UUID generateurId, String motif) {

    public DeplacementTemporaire {
        if (centerId == null) throw new IllegalArgumentException("Centre requis");
        if (patientId == null) throw new IllegalArgumentException("Patient requis");
        if (date == null) throw new IllegalArgumentException("Date requise");
        if (salleId == null || creneauId == null || generateurId == null) {
            throw new IllegalArgumentException("Place de remplacement requise");
        }
    }

    public static DeplacementTemporaire creer(UUID centerId, UUID patientId, LocalDate date, UUID salleId,
                                              UUID creneauId, UUID generateurId, String motif) {
        return new DeplacementTemporaire(UUID.randomUUID(), centerId, patientId, date, salleId, creneauId, generateurId,
                motif);
    }
}
