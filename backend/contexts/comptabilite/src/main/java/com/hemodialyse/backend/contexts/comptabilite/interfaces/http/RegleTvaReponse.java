package com.hemodialyse.backend.contexts.comptabilite.interfaces.http;

import com.hemodialyse.backend.contexts.comptabilite.application.tva.RegleTvaVue;

import java.math.BigDecimal;

/**
 * Une règle de TVA dans la liste : mêmes champs que le legacy, la date de fin vaut {@code ""} si la règle est ouverte.
 */
public record RegleTvaReponse(String typePrestation, BigDecimal tauxApplique, boolean exonere, String dateDebutValidite,
                              String dateFinValidite) {

    static RegleTvaReponse depuis(RegleTvaVue vue) {
        return new RegleTvaReponse(vue.typePrestation(), vue.tauxApplique(), vue.exonere(), vue.dateDebutValidite().toString(),
                vue.dateFinValidite() != null ? vue.dateFinValidite().toString() : "");
    }
}
