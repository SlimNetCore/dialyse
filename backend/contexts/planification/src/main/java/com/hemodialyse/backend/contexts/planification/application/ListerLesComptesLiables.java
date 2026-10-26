package com.hemodialyse.backend.contexts.planification.application;

import com.hemodialyse.backend.contexts.planification.application.ComptesUtilisateurs.Compte;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CentreId;
import com.hemodialyse.backend.contexts.planification.domain.PageDeResultats;
import com.hemodialyse.backend.contexts.planification.domain.Pagination;

/**
 * Liste les comptes INFIRMIER du centre qu'aucune fiche du centre n'utilise encore (liste déroulante de la liaison).
 */
public class ListerLesComptesLiables {
    private final ComptesUtilisateurs comptes;

    public ListerLesComptesLiables(ComptesUtilisateurs comptes) {
        this.comptes = comptes;
    }

    public PageDeResultats<Compte> executer(CentreId centre, Pagination pagination) {
        return comptes.comptesLiables(centre, pagination);
    }
}
