package com.hemodialyse.backend.contexts.planification.domain;

import java.util.Locale;

/**
 * Matricule d'un infirmier, unique dans son centre sans tenir compte de la casse (KB-PLN-19 : casse normalisée en majuscules à la
 * saisie, décision du 26/10/2026). Un matricule déjà enregistré dans le référentiel est relu tel quel ({@link #enregistre}) pour
 * qu'une opération qui ne le modifie pas ne le réécrive pas.
 */
public record Matricule(String valeur) {
    public static final int LONGUEUR_MAXIMALE = 50;

    public Matricule {
        if (valeur == null || valeur.isBlank()) {
            throw new SaisieInvalide("Matricule requis");
        }
        if (valeur.length() > LONGUEUR_MAXIMALE) {
            throw new SaisieInvalide("Le matricule ne peut pas dépasser " + LONGUEUR_MAXIMALE + " caractères");
        }
    }

    /** Le matricule saisi : espaces de bord retirés, casse normalisée en majuscules. */
    public static Matricule depuisLaSaisie(String saisie) {
        if (saisie == null || saisie.isBlank()) {
            throw new SaisieInvalide("Matricule requis");
        }
        return new Matricule(saisie.trim().toUpperCase(Locale.ROOT));
    }

    /** Un matricule déjà enregistré, relu sans normalisation (les fiches historiques peuvent être en minuscules). */
    public static Matricule enregistre(String valeur) {
        return new Matricule(valeur);
    }

    /** Même matricule, à la casse près. */
    public boolean memeQue(Matricule autre) {
        return valeur.equalsIgnoreCase(autre.valeur);
    }
}
