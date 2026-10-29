package com.hemodialyse.backend.contexts.planification.domain;

import java.util.List;

/**
 * Une place demandée enfreint les règles de la planification ({@code PLACEMENT_<règle>}) : porte les règles enfreintes sous forme
 * structurée, pour que l'appelant n'ait jamais à analyser le message (revue P8, m6).
 */
public class PlacementRefuse extends RegleDePlanificationViolee {

    private final List<String> regles;

    public PlacementRefuse(String code, List<String> regles) {
        super(code, "Placement refusé par la planification : " + regles);
        this.regles = List.copyOf(regles);
    }

    /**
     * Les règles enfreintes, dans l'ordre de leur constat.
     */
    public List<String> regles() {
        return regles;
    }
}
