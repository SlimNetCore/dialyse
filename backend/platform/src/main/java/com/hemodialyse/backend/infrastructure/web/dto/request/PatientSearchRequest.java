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
        String code,
        String nom,
        String prenom,
        String sexe,
        LocalDate dateAdmissionFrom,
        LocalDate dateAdmissionTo,
        String numeroAssurance,
        String etatPatient,
        Boolean nonFacturable,
        // Filtres de colonne des référentiels : texte recherché dans le LIBELLÉ affiché
        // (nom du médecin, créneau, transporteur, forfait) — le nom de champ reste l'id de colonne.
        String medecinTraitantId,
        String positionId,
        String transporteurAllerId,
        String transporteurRetourId,
        String pecForfaitId,
        String sortBy,
        String sortDirection
) {
}



