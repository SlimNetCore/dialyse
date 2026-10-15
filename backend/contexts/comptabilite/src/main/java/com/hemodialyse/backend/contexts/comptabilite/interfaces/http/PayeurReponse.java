package com.hemodialyse.backend.contexts.comptabilite.interfaces.http;

import com.hemodialyse.backend.contexts.comptabilite.application.payeur.PayeurVue;

import java.util.UUID;

/**
 * Réponse JSON d'un payeur et de son compte client : mêmes champs que le legacy ({@code compte} {@code null} : compte par défaut).
 */
public record PayeurReponse(UUID payeurId, String code, String nom, String compte) {

    static PayeurReponse depuis(PayeurVue vue) {
        return new PayeurReponse(vue.payeurId(), vue.code(), vue.nom(), vue.compte());
    }
}
