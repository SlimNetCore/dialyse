package com.hemodialyse.backend.infrastructure.web.dto.referential;

import jakarta.validation.constraints.NotNull;

import java.util.Map;
import java.util.UUID;

/**
 * Création / modification d'une ligne de référentiel.
 *
 * @param centerId centre (facultatif : à défaut, celui de la session)
 * @param values   valeurs par clé de champ ; une référence peut être un identifiant ou un code
 */
public record UpsertReferentialEntryRequest(UUID centerId, @NotNull Map<String, String> values) {
}

