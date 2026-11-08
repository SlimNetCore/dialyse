package com.hemodialyse.backend.contexts.seance.interfaces.http.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Corps de la modification de la quantité totale d'un consommable ({@code PUT /api/v1/seances/{id}/consommables/{articleId}}) : mêmes
 * noms de champs que le legacy. {@code userId} est l'auteur transmis au Stock (KB-SEA-33 : usurpable, jamais enregistré).
 */
public record ModificationDeConsommableDemande(UUID centerId, String userId, BigDecimal quantite) {
}
