package com.hemodialyse.backend.contexts.seance.interfaces.http.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Corps de l'ajout d'un consommable ({@code POST /api/v1/seances/{id}/consommables}) : mêmes noms de champs que le legacy. Aucune
 * contrainte de bean : le centre, l'article et la quantité absents ou invalides sont refusés par le cas d'usage (400 {@code BAD_REQUEST},
 * KB-SEA-26).
 */
public record AjoutDeConsommableDemande(UUID centerId, UUID articleId, BigDecimal quantite) {
}
