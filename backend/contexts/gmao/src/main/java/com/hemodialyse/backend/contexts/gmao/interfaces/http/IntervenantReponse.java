package com.hemodialyse.backend.contexts.gmao.interfaces.http;

import com.hemodialyse.backend.contexts.gmao.application.intervenant.IntervenantVue;
import com.hemodialyse.backend.contexts.gmao.application.intervenant.PageDIntervenants;
import com.hemodialyse.backend.contexts.gmao.domain.intervenant.NatureIntervenant;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Réponse JSON d'un intervenant : mêmes champs, même ordre et mêmes types sérialisés que le legacy
 * ({@code IntervenantResponse} : {@code type} pour la nature, {@code tarifHoraireDefaut} pour le tarif horaire).
 */
public record IntervenantReponse(
        UUID id,
        UUID centreId,
        String nom,
        NatureIntervenant type,
        String telephone,
        String email,
        BigDecimal tarifHoraireDefaut,
        boolean actif) {

    static IntervenantReponse depuis(IntervenantVue vue) {
        return new IntervenantReponse(vue.id(), vue.centreId(), vue.nom(), vue.nature(), vue.telephone(), vue.email(),
                vue.tarifHoraire(), vue.actif());
    }

    /** Page d'intervenants : même forme que le contrat observé ({@code items}, {@code total}, {@code page}, {@code size}). */
    public record Page(List<IntervenantReponse> items, long total, int page, int size) {

        static Page depuis(PageDIntervenants page) {
            return new Page(page.items().stream().map(IntervenantReponse::depuis).toList(), page.total(), page.page(), page.size());
        }
    }
}
