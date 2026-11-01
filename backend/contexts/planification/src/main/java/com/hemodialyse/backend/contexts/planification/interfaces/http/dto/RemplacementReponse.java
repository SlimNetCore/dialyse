package com.hemodialyse.backend.contexts.planification.interfaces.http.dto;

import com.hemodialyse.backend.contexts.planification.application.RemplacementLu;

import java.time.LocalDate;
import java.util.UUID;

public record RemplacementReponse(UUID id, LocalDate date, UUID salleId, UUID creneauId, UUID infirmierId,
                                  UUID remplaceId) {
    public static RemplacementReponse depuis(RemplacementLu r) {
        return new RemplacementReponse(r.id(), r.date(), r.salle(), r.creneau(), r.infirmier(), r.remplace());
    }
}
