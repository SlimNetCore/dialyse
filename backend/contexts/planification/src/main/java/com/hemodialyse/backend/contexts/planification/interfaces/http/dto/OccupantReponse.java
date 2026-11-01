package com.hemodialyse.backend.contexts.planification.interfaces.http.dto;

import java.time.LocalDate;
import java.util.UUID;

public record OccupantReponse(UUID patientId, String nom, String generateurCode, boolean aRisque, LocalDate libereLe,
                              boolean temporaire, boolean realiseeHorsPlanning, LocalDate dejaRealiseeLe) {
}
