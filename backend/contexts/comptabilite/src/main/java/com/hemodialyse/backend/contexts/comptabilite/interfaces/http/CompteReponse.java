package com.hemodialyse.backend.contexts.comptabilite.interfaces.http;

import com.hemodialyse.backend.contexts.comptabilite.application.plan.CompteVue;

/**
 * Réponse JSON d'un compte : mêmes champs que le legacy.
 */
public record CompteReponse(String numero, String libelle, boolean actif) {

    static CompteReponse depuis(CompteVue vue) {
        return new CompteReponse(vue.numero(), vue.libelle(), vue.actif());
    }
}
