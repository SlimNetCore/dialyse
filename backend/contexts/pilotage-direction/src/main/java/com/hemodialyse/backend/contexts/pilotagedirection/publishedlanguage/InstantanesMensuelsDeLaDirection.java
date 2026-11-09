package com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage;

import tools.jackson.databind.JsonNode;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Langage publié du Pilotage direction ({@code pilotagedirection.v1}, ADR 0103, PD2) : les instantanés mensuels figés de la société. Les
 * enregistrements portent EXACTEMENT les noms du JSON servi par {@code /api/v1/direction/snapshots}. Le contrôle d'accès (jeton, garde de
 * société) est celui de l'appelant. Les refus sont des {@code RegleDuPilotageViolee} (422 + code : {@code MOIS_INVALIDE},
 * {@code MOIS_NON_TERMINE}, {@code MOIS_TROP_ANCIEN}, {@code SNAPSHOT_INTROUVABLE}).
 */
public interface InstantanesMensuelsDeLaDirection {

    /** Les mois figés, du plus récent au plus ancien. */
    List<Info> lister(UUID societeId);

    /** L'instantané figé d'un mois {@code AAAA-MM}, relu tel quel. */
    Instantane lire(UUID societeId, String mois);

    /** Fige le mois s'il ne l'est pas (mois écoulé de moins de cinq ans) puis le renvoie ; idempotent. */
    Instantane figer(UUID societeId, String mois);

    /** Fige le mois écoulé de chaque société active (tâche du 1er du mois) ; renvoie le nombre d'instantanés créés. */
    int figerLeMoisEcoule();

    record Info(String mois, OffsetDateTime generatedAt) {
    }

    /** {@code breakdown} : nœud absent d'un instantané antérieur aux répartitions (servi {@code null}). */
    record Instantane(String mois, OffsetDateTime generatedAt, JsonNode overview, JsonNode indicators, JsonNode breakdown) {
    }
}
