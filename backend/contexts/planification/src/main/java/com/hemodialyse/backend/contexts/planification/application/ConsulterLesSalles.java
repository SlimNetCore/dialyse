package com.hemodialyse.backend.contexts.planification.application;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import com.hemodialyse.backend.contexts.planification.domain.Pagination;

/**
 * Liste paginée des salles du centre avec leurs générateurs, leur capacité et leurs places restantes. Lecture seule. Page ou taille
 * négative : {@code PaginationInvalide} (400) ; taille plafonnée à {@value Pagination#TAILLE_MAXIMALE} (décision du 27/10).
 */
public class ConsulterLesSalles {

    private final VueDesSalles salles;

    public ConsulterLesSalles(VueDesSalles salles) {
        this.salles = salles;
    }

    public VueDesSalles.Page executer(CentreId centre, int page, int taille) {
        return salles.lister(centre, new Pagination(page, taille));
    }
}
