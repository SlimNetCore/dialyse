package com.hemodialyse.backend.contexts.pilotagedirection.domain;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Le constat, à un instant donné, des indicateurs affichés sur le tableau de bord d'une société : valeur de chaque indicateur, identifié par
 * centre + famille + nom (l'ordre d'insertion est conservé pour un résultat stable).
 * <p>
 * Les valeurs masquées par l'anonymat ne sont jamais présentes dans un constat : un indicateur qui devient publiable (ou masqué) est ignoré,
 * afin qu'une variation ne révèle jamais un effectif inférieur au seuil. Seules les alertes ({@link #ALERTES}) font exception : leur
 * apparition ou leur disparition est elle-même l'information.
 */
public final class ConstatDesIndicateurs {

    /** Familles d'indicateurs (elles regroupent les notifications côté interface). */
    public static final String PATIENTS = "PATIENTS";
    public static final String SEANCES = "SEANCES";
    public static final String FINANCE = "FINANCE";
    public static final String CAISSES = "CAISSES";
    public static final String CLINIQUE = "CLINIQUE";
    public static final String ANEMIE = "ANEMIE";
    public static final String STOCK = "STOCK";
    public static final String ALERTES = "ALERTES";

    private record Cle(UUID centreId, String centre, String famille, String nom) {
    }

    private final Map<Cle, BigDecimal> valeurs = new LinkedHashMap<>();

    private static BigDecimal decimal(Number n) {
        return n instanceof BigDecimal b ? b : new BigDecimal(n.toString());
    }

    /** Ajoute un indicateur ; une valeur absente (masquée ou inconnue) n'est pas retenue. */
    public ConstatDesIndicateurs retenir(UUID centreId, String centre, String famille, String nom, Number valeur) {
        if (valeur != null) {
            valeurs.put(new Cle(centreId, centre, famille, nom), decimal(valeur));
        }
        return this;
    }

    public int taille() {
        return valeurs.size();
    }

    /** Les changements depuis {@code avant} jusqu'à ce constat. */
    public List<Changement> changementsDepuis(ConstatDesIndicateurs avant) {
        List<Changement> changements = new ArrayList<>();
        for (Map.Entry<Cle, BigDecimal> e : valeurs.entrySet()) {
            BigDecimal ancienne = avant.valeurs.get(e.getKey());
            if (ancienne == null) {
                if (ALERTES.equals(e.getKey().famille())) {
                    changements.add(new Changement(e.getKey().centreId(), e.getKey().centre(), ALERTES, e.getKey().nom(),
                            BigDecimal.ZERO, e.getValue()));
                }
                continue;
            }
            if (ancienne.compareTo(e.getValue()) != 0) {
                changements.add(new Changement(e.getKey().centreId(), e.getKey().centre(), e.getKey().famille(),
                        e.getKey().nom(), ancienne, e.getValue()));
            }
        }
        for (Map.Entry<Cle, BigDecimal> e : avant.valeurs.entrySet()) {
            if (ALERTES.equals(e.getKey().famille()) && !valeurs.containsKey(e.getKey())) {
                changements.add(new Changement(e.getKey().centreId(), e.getKey().centre(), ALERTES, e.getKey().nom(),
                        e.getValue(), BigDecimal.ZERO));
            }
        }
        return changements;
    }
}
