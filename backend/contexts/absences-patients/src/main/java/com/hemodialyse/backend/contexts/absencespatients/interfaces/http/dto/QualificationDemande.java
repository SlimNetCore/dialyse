package com.hemodialyse.backend.contexts.absencespatients.interfaces.http.dto;

import jakarta.validation.constraints.NotBlank;

public record QualificationDemande(@NotBlank String motif, String commentaire) {
}
