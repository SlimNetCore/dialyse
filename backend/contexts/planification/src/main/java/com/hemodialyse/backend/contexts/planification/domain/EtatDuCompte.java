package com.hemodialyse.backend.contexts.planification.domain;

/**
 * État, vu de la fiche, du compte utilisateur qui lui est lié (le compte appartient à Identité et accès).
 */
public enum EtatDuCompte {
    /** La fiche n'a pas de compte, ou son compte n'existe plus. */
    ABSENT,
    ACTIF,
    INACTIF;

    public static EtatDuCompte de(boolean actif) {
        return actif ? ACTIF : INACTIF;
    }
}
