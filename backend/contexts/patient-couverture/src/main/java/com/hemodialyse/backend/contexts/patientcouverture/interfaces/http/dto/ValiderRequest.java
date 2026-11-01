package com.hemodialyse.backend.contexts.patientcouverture.interfaces.http.dto;

import java.time.LocalDate;
import java.util.UUID;

public record ValiderRequest(UUID centreId, LocalDate dateDebutEffectif, LocalDate dateFinEffectif,
                             UUID forfaitEffectifId) {
}
