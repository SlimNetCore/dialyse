package com.hemodialyse.backend.contexts.planification.domain;

/**
 * Règle de capacité théorique (RG-PLN-060, revue P8 m5) : un générateur de secours pour huit générateurs en service, arrondi au
 * supérieur. Une salle sans générateur en service n'a pas de secours. La copie du legacy (GMAO, capacité des salles) disparaît avec P9.
 */
public final class GenerateursDeSecours {

    public static final int GENERATEURS_PAR_SECOURS = 8;

    private GenerateursDeSecours() {
    }

    /**
     * Le nombre de générateurs de secours à réserver pour ces générateurs en service.
     */
    public static int pour(int generateursEnService) {
        if (generateursEnService <= 0) return 0;
        return (generateursEnService + GENERATEURS_PAR_SECOURS - 1) / GENERATEURS_PAR_SECOURS;
    }
}
