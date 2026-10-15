package com.hemodialyse.backend.contexts.comptabilite.application.plan;

import com.hemodialyse.backend.contexts.comptabilite.application.PageDe;
import com.hemodialyse.backend.contexts.comptabilite.domain.CentreId;

/**
 * Port sortant de lecture : page des comptes enregistrés d'un centre, triés par numéro.
 */
public interface LecturePlan {

    /**
     * @param recherche fragment du numéro ou du libellé, sans tenir compte de la casse ; {@code %} et {@code _} gardent leur
     *                  sens de joker SQL (KB-CPT-05)
     */
    PageDe<CompteVue> page(CentreId centre, String recherche, boolean actifsSeulement, int page, int taille);
}
