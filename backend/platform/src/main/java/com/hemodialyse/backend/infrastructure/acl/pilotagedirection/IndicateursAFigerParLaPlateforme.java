package com.hemodialyse.backend.infrastructure.acl.pilotagedirection;

import com.hemodialyse.backend.application.direction.DirectionIndicatorsQueryService;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.IndicateursAFigerFournis;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Fournit au Pilotage direction (instantanés mensuels, PD2, ADR 0103) les indicateurs d'un mois À FIGER : le modèle que {@code /indicators}
 * sert (déjà masqué), resté dans la plateforme depuis DM9 (ADR 0101). Aucune règle : l'objet est remis tel quel, le Pilotage le sérialise
 * avec le même {@code ObjectMapper} qu'avant.
 */
@Component
public class IndicateursAFigerParLaPlateforme implements IndicateursAFigerFournis {

    private final DirectionIndicatorsQueryService indicateurs;

    public IndicateursAFigerParLaPlateforme(DirectionIndicatorsQueryService indicateurs) {
        this.indicateurs = indicateurs;
    }

    @Override
    public Object pour(UUID societeId, LocalDate debut, LocalDate fin) {
        return indicateurs.indicators(societeId, debut, fin);
    }
}
