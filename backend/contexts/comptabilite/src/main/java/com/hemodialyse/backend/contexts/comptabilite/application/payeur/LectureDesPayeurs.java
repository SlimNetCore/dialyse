package com.hemodialyse.backend.contexts.comptabilite.application.payeur;

import com.hemodialyse.backend.contexts.comptabilite.application.PageDe;
import com.hemodialyse.backend.contexts.comptabilite.domain.CentreId;
import com.hemodialyse.backend.contexts.comptabilite.domain.payeur.PayeurId;

import java.util.Optional;

/**
 * Port sortant de lecture : les payeurs d'un centre (référentiel tenu hors de la comptabilité) avec le compte client propre
 * à chacun. Les comptes sont rendus tels qu'enregistrés, même hors plan (KB-CPT-09).
 */
public interface LectureDesPayeurs {

    /**
     * Page de payeurs triés par nom puis code ; {@code recherche} porte sur le code et le nom ({@code %} et {@code _} sont des jokers).
     */
    PageDe<PayeurVue> lister(CentreId centre, String recherche, int page, int taille);

    /**
     * Le payeur du centre (sans son compte) ; vide s'il n'existe pas dans ce centre.
     */
    Optional<PayeurVue> trouver(CentreId centre, PayeurId payeur);

    /**
     * Compte client propre au payeur ; vide s'il n'en a pas.
     */
    Optional<String> compteDuPayeur(CentreId centre, PayeurId payeur);
}
