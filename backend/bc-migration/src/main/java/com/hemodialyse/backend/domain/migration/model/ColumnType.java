package com.hemodialyse.backend.domain.migration.model;

/**
 * Nature d'une colonne de fichier de reprise : pilote la normalisation de la valeur lue.
 */
public enum ColumnType {
    /**
     * Texte libre borné.
     */
    TEXT,
    /**
     * Date (JJ/MM/AAAA, AAAA-MM-JJ, JJ-MM-AAAA ou date Excel) → AAAA-MM-JJ.
     */
    DATE,
    /**
     * Entier positif.
     */
    INTEGER,
    /**
     * Nombre décimal positif (virgule ou point) : montants, résultats d'analyses.
     */
    DECIMAL,
    /**
     * Oui / non (oui, non, o, n, 1, 0, x, vrai, faux, true, false).
     */
    BOOLEAN,
    /**
     * Valeur parmi une liste fermée, avec synonymes et correspondances saisies par le centre.
     */
    ENUM,
    /**
     * Téléphone (8 à 15 chiffres).
     */
    PHONE,
    /**
     * Adresse électronique.
     */
    EMAIL,
    /**
     * Jours de dialyse (ex. « Lun, Mer, Ven ») → « LUN,MER,VEN ».
     */
    DAYS,
    /**
     * Code / libellé d'un référentiel ou identifiant d'origine d'une autre entité, résolu par la reprise.
     */
    REFERENCE
}


