package com.hemodialyse.backend.contexts.planification.interfaces.http.dto;

import com.hemodialyse.backend.contexts.planification.domain.Competence;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record ProfilDemande(@NotNull Integer tauxActivite, List<Competence> competences) {
}
