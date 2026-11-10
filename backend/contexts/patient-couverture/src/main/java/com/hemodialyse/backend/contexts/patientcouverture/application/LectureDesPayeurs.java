package com.hemodialyse.backend.contexts.patientcouverture.application;

import com.hemodialyse.backend.contexts.patientcouverture.domain.NiveauDePayeur;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.GestionDesPayeurs.PageDePayeurs;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

/** Port sortant de lecture : la page d'un niveau de la chaîne des payeurs d'un centre (sans passer par le domaine, lecture seule). */
public interface LectureDesPayeurs {

    /**
     * Une page triée par code ; {@code recherche} (sans casse, jokers pris littéralement) porte sur le code et le nom du payeur, sur
     * l'adresse d'un centre payeur et sur le code et le nom de son parent (caisse d'une agence, agence d'un centre payeur). Une recherche
     * absente ou blanche ne filtre rien. Chaque ligne rend le libellé « code · nom » de son parent.
     */
    PageDePayeurs page(CentreId centre, NiveauDePayeur niveau, String recherche, int page, int taille);
}
