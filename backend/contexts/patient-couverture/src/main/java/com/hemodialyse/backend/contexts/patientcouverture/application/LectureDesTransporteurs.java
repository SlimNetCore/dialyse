package com.hemodialyse.backend.contexts.patientcouverture.application;

import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.GestionDesTransporteurs.PageDeTransporteurs;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

/** Port sortant de lecture : la page des transporteurs d'un centre (sans passer par le domaine, lecture seule). */
public interface LectureDesTransporteurs {

    /**
     * Une page triée par nom ; {@code recherche} (sans casse, jokers pris littéralement) porte sur le nom et le téléphone.
     * Une recherche absente ou blanche ne filtre rien.
     */
    PageDeTransporteurs page(CentreId centre, String recherche, int page, int taille);
}
