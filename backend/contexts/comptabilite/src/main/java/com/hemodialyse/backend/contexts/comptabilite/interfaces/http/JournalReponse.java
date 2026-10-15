package com.hemodialyse.backend.contexts.comptabilite.interfaces.http;

import com.hemodialyse.backend.contexts.comptabilite.application.journal.JournalVue;

/**
 * Réponse JSON d'un journal : mêmes champs que le legacy.
 */
public record JournalReponse(String code, String libelle, boolean actif) {

    static JournalReponse depuis(JournalVue vue) {
        return new JournalReponse(vue.code(), vue.libelle(), vue.actif());
    }
}
