package com.hemodialyse.backend.application.direction;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Seuils d'alerte du tableau de bord de la direction : à partir des indicateurs agrégés d'un centre, décide quelles
 * alertes lever. Classe pure (aucune dépendance Spring ni JPA) ; les valeurs masquées par l'anonymat ({@code null})
 * ne déclenchent jamais d'alerte sur un taux, seulement sur la présence d'un effectif non nul.
 */
public final class DirectionAlertPolicy {

    /**
     * Part minimale de patients évalués dont le Kt/V atteint la cible.
     */
    public static final BigDecimal KTV_CONFORMITE_MIN = new BigDecimal("80");
    /**
     * Part minimale de patients évalués dont l'hémoglobine est dans la cible.
     */
    public static final BigDecimal HB_DANS_CIBLE_MIN = new BigDecimal("50");
    /**
     * Horizon (jours) de l'alerte de péremption proche.
     */
    public static final int PEREMPTION_JOURS = 90;

    private DirectionAlertPolicy() {
    }

    public static List<Alert> evaluate(Inputs in) {
        List<Alert> alerts = new ArrayList<>();
        if (in.ktVDansCiblePct() != null && in.ktVDansCiblePct().compareTo(KTV_CONFORMITE_MIN) < 0) {
            alerts.add(new Alert(in.centerId(), in.centre(), "KTV_CONFORMITE_BASSE", Severity.WARNING, in.ktVDansCiblePct()));
        }
        if (in.hbDansCiblePct() != null && in.hbDansCiblePct().compareTo(HB_DANS_CIBLE_MIN) < 0) {
            alerts.add(new Alert(in.centerId(), in.centre(), "HB_HORS_CIBLE", Severity.WARNING, in.hbDansCiblePct()));
        }
        if (in.patientsEnRetardObservance()) {
            alerts.add(new Alert(in.centerId(), in.centre(), "OBSERVANCE_EN_RETARD", Severity.WARNING, null));
        }
        if (in.articlesSousSeuil() > 0) {
            alerts.add(new Alert(in.centerId(), in.centre(), "STOCK_SOUS_SEUIL", Severity.CRITICAL,
                    BigDecimal.valueOf(in.articlesSousSeuil())));
        }
        if (in.lotsPerimes() > 0) {
            alerts.add(new Alert(in.centerId(), in.centre(), "LOTS_PERIMES", Severity.CRITICAL,
                    BigDecimal.valueOf(in.lotsPerimes())));
        }
        if (in.lotsPeremptionProche() > 0) {
            alerts.add(new Alert(in.centerId(), in.centre(), "LOTS_PEREMPTION_PROCHE", Severity.WARNING,
                    BigDecimal.valueOf(in.lotsPeremptionProche())));
        }
        return alerts;
    }

    public enum Severity {WARNING, CRITICAL}

    public record Alert(UUID centerId, String centre, String code, Severity severity, BigDecimal valeur) {
    }

    /**
     * Entrées de la politique : uniquement des agrégats déjà anonymisés.
     */
    public record Inputs(UUID centerId, String centre,
                         BigDecimal ktVDansCiblePct, BigDecimal hbDansCiblePct,
                         boolean patientsEnRetardObservance,
                         long articlesSousSeuil, long lotsPerimes, long lotsPeremptionProche) {
    }
}
