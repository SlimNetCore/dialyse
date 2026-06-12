package com.hemodialyse.backend.infrastructure.web.dto.request;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record CreateBonSortieRequest(
        @NotNull UUID centerId,
        UUID seanceId,
        UUID patientId,
        String poste,
        LocalDate dateSortie,
        String userId,
        List<SortieItemDto> items
) {
}

