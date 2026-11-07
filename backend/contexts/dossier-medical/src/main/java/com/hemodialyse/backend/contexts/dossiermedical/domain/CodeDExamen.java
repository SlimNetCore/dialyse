package com.hemodialyse.backend.contexts.dossiermedical.domain;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Code d'un examen dans une nomenclature, normalisé en majuscules. Reproduit du legacy (KB-MED-42, à confirmer avec les métiers) : la
 * nomenclature n'est pas limitée à LOINC (CIM10, ATC, LOCAL acceptés) ; seuls CIM10 et LOINC ont un format (422, codes stables
 * {@code CONCEPT_CODE_INVALID_*}). Écart décidé (KB-MED-35) : une nomenclature absente ou inconnue, un code trop long sont des saisies
 * invalides (400) ; le legacy répondait 500.
 */
public record CodeDExamen(String nomenclature, String code, String libelle) {

    public static final Set<String> NOMENCLATURES = Set.of("CIM10", "LOINC", "ATC", "LOCAL");
    private static final Pattern CIM10 = Pattern.compile("^[A-Z][0-9]{2}(\\.[0-9A-Z]{1,4})?$");
    private static final Pattern LOINC = Pattern.compile("^\\d{1,5}-\\d$");

    public CodeDExamen {
        if (nomenclature == null || !NOMENCLATURES.contains(nomenclature)) throw new SaisieInvalide("Nomenclature de codage inconnue");
        if (code == null || code.isBlank()) throw new SaisieInvalide("Le code de l'examen est obligatoire");
        code = code.trim().toUpperCase(Locale.ROOT);
        if ("CIM10".equals(nomenclature) && !CIM10.matcher(code).matches()) {
            throw new RegleDeGestionViolee("CONCEPT_CODE_INVALID_CIM10", "Code CIM-10 invalide");
        }
        if ("LOINC".equals(nomenclature) && !LOINC.matcher(code).matches()) {
            throw new RegleDeGestionViolee("CONCEPT_CODE_INVALID_LOINC", "Code LOINC invalide");
        }
        if (code.length() > 20) throw new SaisieInvalide("Le code de l'examen est limité à 20 caractères");
        libelle = libelle == null ? null : libelle.trim();
        if (libelle != null && libelle.length() > 255) throw new SaisieInvalide("Le libellé du code est limité à 255 caractères");
    }
}
