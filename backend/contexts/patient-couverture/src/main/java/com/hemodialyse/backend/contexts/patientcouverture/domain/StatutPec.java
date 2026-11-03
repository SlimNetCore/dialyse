package com.hemodialyse.backend.contexts.patientcouverture.domain;

/** Statut d'une prise en charge (vocabulaire du legacy, colonne {@code prise_en_charge.statut}) : CREE, VALIDEE, CLOTUREE. */
public enum StatutPec {
    CREE, VALIDEE, CLOTUREE;

    /** Lit la colonne ; une valeur inconnue (donnée ancienne) est refusée en 400, comme aujourd'hui. */
    public static StatutPec depuis(String texte) {
        try {
            return valueOf(texte);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new SaisieInvalide("Statut de prise en charge inconnu : " + texte);
        }
    }
}
