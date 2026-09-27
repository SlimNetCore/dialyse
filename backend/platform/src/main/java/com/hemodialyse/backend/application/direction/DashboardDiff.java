package com.hemodialyse.backend.application.direction;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Compare deux instantanés des indicateurs du tableau de bord d'une société et liste ce qui a changé. Classe pure
 * (aucune dépendance Spring ni JPA).
 * <p>
 * Un indicateur est identifié par centre + famille + nom. Les valeurs masquées par l'anonymat ne sont jamais
 * présentes dans un instantané : un indicateur qui devient publiable (ou masqué) est ignoré, afin qu'une variation
 * ne révèle jamais un effectif inférieur au seuil.
 */
public final class DashboardDiff {

    /**
     * Familles d'indicateurs (regroupent les notifications côté interface).
     */
    public static final String PATIENTS = "PATIENTS";
    public static final String SEANCES = "SEANCES";
    public static final String FINANCE = "FINANCE";
    public static final String CAISSES = "CAISSES";
    public static final String CLINIQUE = "CLINIQUE";
    public static final String ANEMIE = "ANEMIE";
    public static final String STOCK = "STOCK";
    public static final String ALERTES = "ALERTES";

    private DashboardDiff() {
    }

    /**
     * Changements entre {@code before} et {@code after}. Un indicateur présent d'un seul côté est ignoré, sauf les
     * alertes ({@link #ALERTES}) dont l'apparition ou la disparition est elle-même l'information.
     */
    public static List<Change> diff(Snapshot before, Snapshot after) {
        List<Change> changes = new ArrayList<>();
        for (Map.Entry<Key, BigDecimal> e : after.values().entrySet()) {
            BigDecimal old = before.values().get(e.getKey());
            if (old == null) {
                if (ALERTES.equals(e.getKey().family())) {
                    changes.add(new Change(e.getKey().centerId(), e.getKey().centre(), ALERTES, e.getKey().name(),
                            BigDecimal.ZERO, e.getValue()));
                }
                continue;
            }
            if (old.compareTo(e.getValue()) != 0) {
                changes.add(new Change(e.getKey().centerId(), e.getKey().centre(), e.getKey().family(),
                        e.getKey().name(), old, e.getValue()));
            }
        }
        for (Map.Entry<Key, BigDecimal> e : before.values().entrySet()) {
            if (ALERTES.equals(e.getKey().family()) && !after.values().containsKey(e.getKey())) {
                changes.add(new Change(e.getKey().centerId(), e.getKey().centre(), ALERTES, e.getKey().name(),
                        e.getValue(), BigDecimal.ZERO));
            }
        }
        return changes;
    }

    /**
     * Un indicateur d'un centre à un instant donné.
     */
    public record Key(UUID centerId, String centre, String family, String name) {
    }

    public record Change(UUID centerId, String centre, String family, String name, BigDecimal before,
                         BigDecimal after) {
    }

    /**
     * Instantané : indicateur → valeur (ordre d'insertion conservé pour un résultat stable).
     */
    public static final class Snapshot {
        private final Map<Key, BigDecimal> values = new LinkedHashMap<>();

        private static BigDecimal toDecimal(Number n) {
            return n instanceof BigDecimal b ? b : new BigDecimal(n.toString());
        }

        public Snapshot put(UUID centerId, String centre, String family, String name, Number value) {
            if (value != null) {
                values.put(new Key(centerId, centre, family, name), toDecimal(value));
            }
            return this;
        }

        Map<Key, BigDecimal> values() {
            return values;
        }

        public int size() {
            return values.size();
        }
    }
}
