package com.hemodialyse.backend.contexts.dossiermedical.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Répartition d'un ensemble de dernières valeurs de patients face à une cible KDIGO : patients évalués (masqué sous le seuil), taux dans,
 * sous et au-dessus de la cible (nuls sous le seuil). Repris mot pour mot de {@code DirectionIndicatorsQueryService.marker} (DM9, ADR 0101).
 */
public record RepartitionFaceALaCible(Long evalues, BigDecimal pctDansCible, BigDecimal pctSousCible, BigDecimal pctAuDessus) {

    public static RepartitionFaceALaCible de(List<BigDecimal> valeurs, RegleDeCible regle) {
        long n = valeurs.size();
        if (EffectifMasque.sousLeSeuil(n)) {
            return new RepartitionFaceALaCible(EffectifMasque.masquer(n), null, null, null);
        }
        long dans = 0, sous = 0, dessus = 0;
        for (BigDecimal v : valeurs) {
            StatutDeCible s = CiblesKdigo.evaluer(regle, v).statut();
            if (s == StatutDeCible.DANS_CIBLE) dans++;
            else if (s == StatutDeCible.SOUS_CIBLE) sous++;
            else if (s == StatutDeCible.AU_DESSUS_CIBLE) dessus++;
        }
        return new RepartitionFaceALaCible(n, pct(dans, n), pct(sous, n), pct(dessus, n));
    }

    private static BigDecimal pct(long part, long total) {
        return BigDecimal.valueOf(part * 100.0 / total).setScale(1, RoundingMode.HALF_UP);
    }
}
