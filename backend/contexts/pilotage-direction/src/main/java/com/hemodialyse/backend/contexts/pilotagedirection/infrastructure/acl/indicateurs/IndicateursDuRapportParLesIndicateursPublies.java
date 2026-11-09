package com.hemodialyse.backend.contexts.pilotagedirection.infrastructure.acl.indicateurs;

import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.IndicateursDeLaDirection;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.IndicateursDeLaDirection.Marqueur;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.IndicateursDeLaDirectionFournis;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Adaptateur sortant du rapport de synthèse (PD4, ADR 0102) : lui remet les indicateurs de la période sous la forme que le rapport lit
 * ({@link IndicateursDeLaDirectionFournis}, aussi celle des instantanés figés relus), à partir des indicateurs que le Pilotage compose
 * lui-même depuis PD6 (ADR 0106). Traduction pure, aucune règle : seul le taux dans la cible de chaque marqueur est retenu.
 */
public final class IndicateursDuRapportParLesIndicateursPublies implements IndicateursDeLaDirectionFournis {

    private final IndicateursDeLaDirection indicateurs;

    public IndicateursDuRapportParLesIndicateursPublies(IndicateursDeLaDirection indicateurs) {
        this.indicateurs = indicateurs;
    }

    @Override
    public IndicateursDeLaPeriode pour(UUID societeId, LocalDate from, LocalDate to) {
        var i = indicateurs.indicateurs(societeId, from, to);
        return new IndicateursDeLaPeriode(i.centres().stream().map(c -> centre(c.nom(), c)).toList(), centre(null, i.totaux()),
                i.alertes().stream().map(a -> new AlerteDeLaDirection(a.centre(), a.code(), a.severity().name(), a.valeur())).toList());
    }

    static IndicateursDuCentre centre(String nom, IndicateursDeLaDirection.IndicateursDuCentre c) {
        var k = c.clinique();
        var s = c.stock();
        return new IndicateursDuCentre(nom,
                new Clinique(pct(k.ktV()), pct(k.hemoglobine()), pct(k.phosphore()), pct(k.pth()), pct(k.albumine()), k.vhbPositifs(),
                        k.vhcPositifs(), k.vihPositifs(), k.patientsObservanceEnRetard(), k.greffeListeAttente(), k.greffeBilanEnCours(),
                        k.greffesPeriode()),
                new Stock(s.articlesActifs(), s.articlesSousSeuil(), s.lotsPerimes(), s.lotsPeremptionProche(), s.valeurStock()));
    }

    private static BigDecimal pct(Marqueur m) {
        return m == null ? null : m.pctDansCible();
    }
}
