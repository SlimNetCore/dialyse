package com.hemodialyse.backend.contexts.patientcouverture.application;

import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.GestionDuCatalogueDeForfaits.PageDeForfaits;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

/** Port sortant de lecture : la page du catalogue de forfaits d'un centre (sans passer par le domaine, lecture seule). */
public interface LectureDuCatalogueDeForfaits {

    /**
     * Une page triée par code ; {@code recherche} (sans casse, jokers pris littéralement) porte sur le code et le libellé (pas sur le
     * prix). Une recherche absente ou blanche ne filtre rien.
     */
    PageDeForfaits page(CentreId centre, String recherche, int page, int taille);
}
