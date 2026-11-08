package com.hemodialyse.backend.contexts.seance.domain;

/**
 * Nombre de séances récentes rendues : au moins 1, au plus {@value #MAXIMUM}. Une demande de 0 ou négative est RAMENÉE à 1, une demande
 * au-dessus du maximum à {@value #MAXIMUM}, sans erreur ni alerte (KB-SEA-60, reproduit : comportement observable).
 */
public record NombreDeSeancesRecentes(int valeur) {

    public static final int MAXIMUM = 10;
    public static final int PAR_DEFAUT = 3;

    public NombreDeSeancesRecentes {
        if (valeur < 1 || valeur > MAXIMUM) throw new SaisieInvalide("Nombre de séances récentes hors bornes");
    }

    /** Ramène une demande quelconque dans les bornes. */
    public static NombreDeSeancesRecentes demande(int demande) {
        return new NombreDeSeancesRecentes(Math.max(1, Math.min(demande, MAXIMUM)));
    }
}
