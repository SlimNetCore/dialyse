package com.hemodialyse.backend.contexts.absencespatients.interfaces.http.dto;

import jakarta.validation.constraints.NotBlank;

public record AnnulationDemande(@NotBlank String commentaire) {
}
