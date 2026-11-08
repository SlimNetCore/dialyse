package com.hemodialyse.backend.contexts.seance.interfaces.http.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Corps de {@code PUT /api/v1/seances/{id}/forfait} : mêmes champs JSON et mêmes contraintes que le legacy. {@code userId} est l'auteur
 * de l'imposition, lu dans le corps (KB-SEA-42, reproduit : jamais le compte de session) ; au-delà de 100 caractères : 400 (KB-SEA-44).
 */
public record ImpositionDeForfaitDemande(@NotNull UUID centerId, @NotNull UUID forfaitId, @NotBlank String userId) {
}
