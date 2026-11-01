package com.hemodialyse.backend.contexts.patientcouverture.interfaces.http.dto;

import java.time.LocalDate;
import java.util.UUID;

public record SortieRequest(UUID centreId, LocalDate dateSortie, UUID auteurId) {
}
