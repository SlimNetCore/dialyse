package com.hemodialyse.backend.contexts.seance.interfaces.http.dto;

import java.util.List;
import java.util.UUID;

/**
 * Corps du remplacement des raccourcis de consommables ({@code PUT /api/v1/seances/raccourcis-consommables}). Une liste absente ou nulle
 * efface tous les raccourcis du centre (KB-SEA-31, reproduit : à faire confirmer avant la mise en production).
 */
public record RaccourcisDemande(List<UUID> articleIds) {
}
