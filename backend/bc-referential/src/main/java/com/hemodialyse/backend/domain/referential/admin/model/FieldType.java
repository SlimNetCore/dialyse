package com.hemodialyse.backend.domain.referential.admin.model;

/**
 * Nature d'un champ de référentiel : pilote la validation, la normalisation et le rendu du formulaire.
 */
public enum FieldType {
    /**
     * Texte libre borné par {@link ReferentialField#maxLength()}.
     */
    TEXT,
    /**
     * Montant positif à 2 décimales (virgule ou point acceptés).
     */
    DECIMAL,
    /**
     * Entier positif sans décimale (ex. capacité d'une salle).
     */
    INTEGER,
    /**
     * Valeur parmi {@link ReferentialField#allowedValues()}.
     */
    ENUM,
    /**
     * Numéro de téléphone (chiffres, espaces, +, ., -, parenthèses).
     */
    PHONE,
    /**
     * Lien vers un autre référentiel du même centre, par identifiant ou par code.
     */
    REFERENCE
}

