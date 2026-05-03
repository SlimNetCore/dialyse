package com.hemodialyse.backend.infrastructure.web.dto.request;

import java.time.LocalDate;

public record UpdateAssignmentRequest(LocalDate dateDebutAffectation, LocalDate dateFinAffectation) {
}

