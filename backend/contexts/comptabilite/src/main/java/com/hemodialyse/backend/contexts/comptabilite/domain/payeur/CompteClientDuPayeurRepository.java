package com.hemodialyse.backend.contexts.comptabilite.domain.payeur;

import com.hemodialyse.backend.contexts.comptabilite.domain.CentreId;

/**
 * Port sortant : compte client de chaque payeur d'un centre. Toute écriture est bornée au centre.
 */
public interface CompteClientDuPayeurRepository {

    /**
     * Affecte le compte au payeur, ou remplace celui qu'il avait.
     */
    void enregistrer(CompteClientDuPayeur compteClient);

    /**
     * Le payeur retrouve le compte client par défaut du centre ; sans effet s'il n'avait pas de compte propre.
     */
    void retirer(CentreId centre, PayeurId payeur);
}
