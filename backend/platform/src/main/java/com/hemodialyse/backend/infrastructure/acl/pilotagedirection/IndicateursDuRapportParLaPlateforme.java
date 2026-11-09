package com.hemodialyse.backend.infrastructure.acl.pilotagedirection;

import com.hemodialyse.backend.application.direction.DirectionIndicatorsQueryService;
import com.hemodialyse.backend.application.direction.DirectionIndicatorsQueryService.CentreIndicators;
import com.hemodialyse.backend.application.direction.DirectionIndicatorsQueryService.Marker;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.IndicateursDeLaDirectionFournis;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Fournit au Pilotage direction (rapport de synthèse, PD4, ADR 0102) les indicateurs de la période : ceux que {@code /indicators} sert
 * (déjà masqués), restés dans la plateforme depuis DM9 (ADR 0101). Traduction pure, aucune règle.
 */
@Component
public class IndicateursDuRapportParLaPlateforme implements IndicateursDeLaDirectionFournis {

    private final DirectionIndicatorsQueryService indicateurs;

    public IndicateursDuRapportParLaPlateforme(DirectionIndicatorsQueryService indicateurs) {
        this.indicateurs = indicateurs;
    }

    private static BigDecimal pct(Marker m) {
        return m == null ? null : m.pctDansCible();
    }

    private static IndicateursDuCentre centre(String nom, CentreIndicators c) {
        var k = c.clinique();
        var s = c.stock();
        return new IndicateursDuCentre(nom,
                new Clinique(pct(k.ktV()), pct(k.hemoglobine()), pct(k.phosphore()), pct(k.pth()), pct(k.albumine()), k.vhbPositifs(),
                        k.vhcPositifs(), k.vihPositifs(), k.patientsObservanceEnRetard(), k.greffeListeAttente(), k.greffeBilanEnCours(),
                        k.greffesPeriode()),
                new Stock(s.articlesActifs(), s.articlesSousSeuil(), s.lotsPerimes(), s.lotsPeremptionProche(), s.valeurStock()));
    }

    @Override
    public IndicateursDeLaPeriode pour(UUID societeId, LocalDate from, LocalDate to) {
        var i = indicateurs.indicators(societeId, from, to);
        return new IndicateursDeLaPeriode(i.centres().stream().map(c -> centre(c.nom(), c)).toList(), centre(null, i.totaux()),
                i.alertes().stream().map(a -> new AlerteDeLaDirection(a.centre(), a.code(), a.severity().name(), a.valeur())).toList());
    }
}
