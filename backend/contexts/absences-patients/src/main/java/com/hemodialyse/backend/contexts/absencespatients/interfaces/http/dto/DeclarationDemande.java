package com.hemodialyse.backend.contexts.absencespatients.interfaces.http.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

public record DeclarationDemande(@NotNull UUID patientId, @NotNull LocalDate dateSeance, String motif,
                                 String commentaire) {
}
