package com.hemodialyse.backend.infrastructure.web.dto.request;

import java.time.LocalDate;
import java.util.UUID;

public record UpsertAbordVasculaireRequest(
        UUID centerId,
        String typeAbord,
        String cote,
        String localisation,
        LocalDate dateCreation,
        LocalDate dateFin,
        Boolean actif,
        String complications
) {
}

