package com.hemodialyse.backend.contexts.comptabilite.application.tva;

import com.hemodialyse.backend.contexts.comptabilite.application.PageDe;
import com.hemodialyse.backend.contexts.comptabilite.domain.CentreId;
import com.hemodialyse.backend.contexts.comptabilite.domain.tva.RegleTvaId;

import java.util.Optional;

/**
 * Port sortant de lecture de l'écran « Types de TVA » : les règles de TVA du centre avec leurs informations de création
 * (que le modèle de la règle ne porte pas).
 */
public interface LectureDesTypesDeTva {

    /**
     * Une page des types du centre, tous états confondus, triés par début décroissant puis libellé.
     */
    PageDe<TypeDeTvaVue> page(CentreId centre, int page, int taille);

    /**
     * Le type de ce centre portant cet identifiant ; vide s'il n'existe pas ou appartient à un autre centre.
     */
    Optional<TypeDeTvaVue> type(CentreId centre, RegleTvaId id);
}


