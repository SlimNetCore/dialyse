package com.hemodialyse.backend.contexts.seance.interfaces.http.dto;

/**
 * Corps de {@code DELETE /api/v1/seances/{id}} : le seul champ {@code motif} (un nombre est lu comme texte, un champ inconnu est ignoré,
 * comme le legacy). Corps absent, {@code null} ou illisible, motif en tableau : 400 (KB-SEA-52, corrigé : le legacy répondait 500).
 */
public record SuppressionDeSeanceDemande(String motif) {
}
