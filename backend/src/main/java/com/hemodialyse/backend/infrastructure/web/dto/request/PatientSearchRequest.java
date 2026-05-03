package com.hemodialyse.backend.infrastructure.web.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

public record PatientSearchRequest(
        @NotNull UUID centerId,
        @Min(0) int page,
        @Min(1) @Max(200) int size,
        String search,
        String code,
        String nom,
        String prenom,
        String sexe,
        LocalDate dateAdmissionFrom,
        LocalDate dateAdmissionTo,
        String numeroAssurance,
        String etatPatient,
        Boolean nonFacturable
) {
}



