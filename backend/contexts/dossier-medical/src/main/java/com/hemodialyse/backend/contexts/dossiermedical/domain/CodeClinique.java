package com.hemodialyse.backend.contexts.dossiermedical.domain;

import java.util.regex.Pattern;

/**
 * Code clinique (diagnostic d'un antécédent, substance d'une allergie) : un système de codage, un code normalisé (espaces retirés,
 * majuscules) et un libellé d'affichage facultatif. Contrôle de FORME seulement (CIM-10 et LOINC), pas d'existence dans le référentiel
 * (reproduit du legacy, KB-MED-64 et 70). Deux codes sont égaux s'ils ont le même système et le même code.
 * <p>
 * Bornes de colonne (400 au lieu du 500 du legacy, KB-MED-58) : code 20 caractères, libellé d'affichage 255.
 */
public record CodeClinique(SystemeDeCodage systeme, String code, String affichage) {
    static final int CODE_MAXIMAL = 20;
    static final int AFFICHAGE_MAXIMAL = 255;
    private static final Pattern CIM10 = Pattern.compile("^[A-Z][0-9]{2}(\\.[0-9A-Z]{1,4})?$");
    private static final Pattern LOINC = Pattern.compile("^\\d{1,5}-\\d$");

    /** Code saisi : normalisé puis contrôlé. Les règles métier (forme) précèdent les bornes de colonne (ordre du legacy). */
    public static CodeClinique saisir(SystemeDeCodage systeme, String brut, String affichage) {
        if (brut == null || brut.isBlank()) throw new RegleDeGestionViolee("CONCEPT_CODE_REQUIRED", "Le code est obligatoire");
        String normalise = brut.trim().toUpperCase();
        switch (systeme) {
            case CIM10 -> {
                if (!CIM10.matcher(normalise).matches()) {
                    throw new RegleDeGestionViolee("CONCEPT_CODE_INVALID_CIM10", "Code CIM-10 invalide : " + normalise);
                }
            }
            case LOINC -> {
                if (!LOINC.matcher(normalise).matches()) {
                    throw new RegleDeGestionViolee("CONCEPT_CODE_INVALID_LOINC", "Code LOINC invalide : " + normalise);
                }
            }
            case ATC, LOCAL -> {
                // Aucun format imposé (reproduit).
            }
        }
        return new CodeClinique(systeme, normalise, affichage == null ? null : affichage.trim());
    }

    /**
     * Code relu de la base : normalisé comme le legacy (espaces retirés, majuscules : un code importé en minuscules est rendu en
     * majuscules), mais SANS re-validation de forme (le legacy répondait 500 pour toute la liste si un code importé était invalide).
     */
    public static CodeClinique reconstituer(SystemeDeCodage systeme, String code, String affichage) {
        return new CodeClinique(systeme, code.trim().toUpperCase(), affichage == null ? null : affichage.trim());
    }

    /** Refuse (400) un code ou un libellé d'affichage plus long que sa colonne. */
    void verifierLesBornes() {
        if (code.length() > CODE_MAXIMAL) throw new SaisieInvalide("Le code est limité à 20 caractères");
        if (affichage != null && affichage.length() > AFFICHAGE_MAXIMAL) {
            throw new SaisieInvalide("Le libellé du code est limité à 255 caractères");
        }
    }

    public boolean memeCodeQue(CodeClinique autre) {
        return autre != null && systeme == autre.systeme && code.equals(autre.code);
    }
}
