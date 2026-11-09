package com.hemodialyse.backend.infrastructure.acl.pilotagedirection;

import com.hemodialyse.backend.application.direction.DirectionIndicatorsQueryService;
import com.hemodialyse.backend.application.direction.DirectionIndicatorsQueryService.CentreIndicators;
import com.hemodialyse.backend.application.direction.DirectionIndicatorsQueryService.Marker;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.IndicateursDuTempsReelFournis;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Fournit au temps réel du Pilotage direction (PD3, ADR 0104) les indicateurs de la société sur la période par défaut : ceux que
 * {@code /indicators} sert (déjà masqués), restés dans la plateforme depuis DM9 (ADR 0101). Traduction pure, aucune règle.
 */
@Component
public class IndicateursDuTempsReelParLaPlateforme implements IndicateursDuTempsReelFournis {

    private final DirectionIndicatorsQueryService indicateurs;

    public IndicateursDuTempsReelParLaPlateforme(DirectionIndicatorsQueryService indicateurs) {
        this.indicateurs = indicateurs;
    }

    private static Marqueur marqueur(Marker m) {
        return new Marqueur(m.evalues(), m.pctDansCible());
    }

    private static IndicateursDuCentre centre(CentreIndicators c) {
        var k = c.clinique();
        var s = c.stock();
        return new IndicateursDuCentre(c.centerId(), c.nom(),
                new Clinique(marqueur(k.ktV()), marqueur(k.hemoglobine()), marqueur(k.phosphore()), marqueur(k.pth()), marqueur(k.albumine()),
                        k.vhbPositifs(), k.vhcPositifs(), k.vihPositifs(), k.patientsObservanceEnRetard(), k.greffeListeAttente(),
                        k.greffesPeriode()),
                new Stock(s.articlesSousSeuil(), s.lotsPerimes(), s.lotsPeremptionProche(), s.valeurStock()));
    }

    @Override
    public IndicateursDeLaSociete pour(UUID societeId) {
        var i = indicateurs.indicators(societeId, null, null);
        return new IndicateursDeLaSociete(i.centres().stream().map(IndicateursDuTempsReelParLaPlateforme::centre).toList(), i.alertes());
    }
}
