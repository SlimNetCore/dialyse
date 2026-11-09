package com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Port FOURNI au Pilotage direction par la plateforme (PD3, ADR 0104) : le transport (STOMP, {@code /topic/societe/{id}/dashboard}) de la
 * diffusion temps réel. Le message ne porte que des indicateurs agrégés et anonymes. Les noms des champs sont ceux du message STOMP existant
 * (contrat avec le client Angular).
 */
public interface DiffusionDuTableauDeBordFournie {

    void publish(UUID societeId, TableauDeBordModifie evenement);

    record TableauDeBordModifie(String type, UUID societeId, Instant at, List<ChangementDuTableauDeBord> changes) {
        public static final String TYPE = "DASHBOARD_CHANGED";
    }

    record ChangementDuTableauDeBord(UUID centerId, String centre, String family, String name, BigDecimal before, BigDecimal after) {
    }
}
