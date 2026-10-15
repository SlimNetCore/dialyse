package com.hemodialyse.backend.contexts.comptabilite.application.plan;

import com.hemodialyse.backend.contexts.comptabilite.domain.plan.CompteComptable;

/**
 * Un compte du plan, tel que l'écran le lit.
 */
public record CompteVue(String numero, String libelle, boolean actif) {

    public static CompteVue depuis(CompteComptable compte) {
        return new CompteVue(compte.numero().valeur(), compte.libelle(), compte.actif());
    }
}
