package com.hemodialyse.backend.contexts.comptabilite.interfaces.http;

import com.hemodialyse.backend.contexts.comptabilite.application.mapping.MappingVue;

import java.util.Map;
import java.util.UUID;

/**
 * Réponse JSON du mapping : mêmes champs que le legacy ({@code centerId}, {@code compteTVACollectee} vide si absent).
 */
public record MappingReponse(
        UUID centerId,
        String compteVentes,
        String compteClientPatient,
        String compteClientDefaut,
        String compteBanque,
        String compteCaisse,
        String compteTVACollectee,
        String compteStock,
        String compteConsommation,
        String compteFacturesNonParvenues,
        String compteBoniInventaire,
        String compteMaliInventaire,
        Map<String, String> journaux) {

    static MappingReponse depuis(MappingVue vue) {
        return new MappingReponse(vue.centre(), vue.compteVentes(), vue.compteClientPatient(), vue.compteClientDefaut(),
                vue.compteBanque(), vue.compteCaisse(), vue.compteTvaCollectee(), vue.compteStock(), vue.compteConsommation(),
                vue.compteFacturesNonParvenues(), vue.compteBoniInventaire(), vue.compteMaliInventaire(), vue.journaux());
    }
}
