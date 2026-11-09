package com.hemodialyse.backend.contexts.pilotagedirection.domain;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Les seuils d'alerte du tableau de bord de la direction : à partir du constat d'un centre, décide quelles alertes lever, dans l'ordre des six
 * codes réels. Les valeurs masquées par l'anonymat ({@code null}) ne déclenchent jamais d'alerte sur un taux, seulement sur la présence d'un
 * effectif non nul. Les seuils de taux sont INCLUSIFS (80 % exactement : pas d'alerte).
 */
public final class PolitiqueDAlertes {

    /** Part minimale de patients évalués dont le Kt/V atteint la cible. */
    public static final BigDecimal KTV_CONFORMITE_MIN = new BigDecimal("80");
    /** Part minimale de patients évalués dont l'hémoglobine est dans la cible. */
    public static final BigDecimal HB_DANS_CIBLE_MIN = new BigDecimal("50");
    /** Horizon (jours) de l'alerte de péremption proche ; appliqué par le Stock, qui compte les lots. */
    public static final int PEREMPTION_JOURS = 90;

    public static final String KTV_CONFORMITE_BASSE = "KTV_CONFORMITE_BASSE";
    public static final String HB_HORS_CIBLE = "HB_HORS_CIBLE";
    public static final String OBSERVANCE_EN_RETARD = "OBSERVANCE_EN_RETARD";
    public static final String STOCK_SOUS_SEUIL = "STOCK_SOUS_SEUIL";
    public static final String LOTS_PERIMES = "LOTS_PERIMES";
    public static final String LOTS_PEREMPTION_PROCHE = "LOTS_PEREMPTION_PROCHE";

    private PolitiqueDAlertes() {
    }

    public static List<AlerteLevee> lever(ConstatDuCentre c) {
        List<AlerteLevee> alertes = new ArrayList<>();
        if (c.ktVDansCiblePct() != null && c.ktVDansCiblePct().compareTo(KTV_CONFORMITE_MIN) < 0) {
            alertes.add(new AlerteLevee(c.centreId(), c.centre(), KTV_CONFORMITE_BASSE, GraviteDAlerte.WARNING, c.ktVDansCiblePct()));
        }
        if (c.hbDansCiblePct() != null && c.hbDansCiblePct().compareTo(HB_DANS_CIBLE_MIN) < 0) {
            alertes.add(new AlerteLevee(c.centreId(), c.centre(), HB_HORS_CIBLE, GraviteDAlerte.WARNING, c.hbDansCiblePct()));
        }
        if (c.patientsEnRetardObservance()) {
            alertes.add(new AlerteLevee(c.centreId(), c.centre(), OBSERVANCE_EN_RETARD, GraviteDAlerte.WARNING, null));
        }
        if (c.articlesSousSeuil() > 0) {
            alertes.add(new AlerteLevee(c.centreId(), c.centre(), STOCK_SOUS_SEUIL, GraviteDAlerte.CRITICAL,
                    BigDecimal.valueOf(c.articlesSousSeuil())));
        }
        if (c.lotsPerimes() > 0) {
            alertes.add(new AlerteLevee(c.centreId(), c.centre(), LOTS_PERIMES, GraviteDAlerte.CRITICAL,
                    BigDecimal.valueOf(c.lotsPerimes())));
        }
        if (c.lotsPeremptionProche() > 0) {
            alertes.add(new AlerteLevee(c.centreId(), c.centre(), LOTS_PEREMPTION_PROCHE, GraviteDAlerte.WARNING,
                    BigDecimal.valueOf(c.lotsPeremptionProche())));
        }
        return alertes;
    }
}
