package com.hemodialyse.backend.contexts.pilotagedirection.domain;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * L'évolution mensuelle du rapport : les mois de tous les centres cumulés, triés par mois ({@code AAAA-MM}). Les sommes se font en
 * {@code double} comme le legacy (les arrondis imprimés en dépendent : parité des goldens du rapport).
 */
public final class EvolutionMensuelle {

    private EvolutionMensuelle() {
    }

    public record PointDuMois(String mois, double seances, double caHt, double caTtc) {
    }

    public record MoisCumule(String mois, long seances, double caHt, double caTtc) {
    }

    public static List<MoisCumule> cumuler(List<PointDuMois> points) {
        Map<String, double[]> parMois = new LinkedHashMap<>();
        for (PointDuMois p : points) {
            double[] cumul = parMois.computeIfAbsent(p.mois(), k -> new double[3]);
            cumul[0] += p.seances();
            cumul[1] += p.caHt();
            cumul[2] += p.caTtc();
        }
        List<MoisCumule> mois = new ArrayList<>();
        parMois.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(e -> mois.add(new MoisCumule(e.getKey(), Math.round(e.getValue()[0]), e.getValue()[1], e.getValue()[2])));
        return mois;
    }
}
