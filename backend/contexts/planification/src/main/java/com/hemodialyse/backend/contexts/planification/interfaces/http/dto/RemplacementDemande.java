package com.hemodialyse.backend.contexts.planification.interfaces.http.dto;

import java.time.LocalDate;
import java.util.UUID;

public record RemplacementDemande(LocalDate date, UUID salleId, UUID creneauId, UUID infirmierId, UUID remplaceId) {
}
