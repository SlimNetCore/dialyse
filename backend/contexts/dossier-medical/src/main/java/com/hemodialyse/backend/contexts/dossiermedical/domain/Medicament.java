package com.hemodialyse.backend.contexts.dossiermedical.domain;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Médicament codé d'une ligne d'ordonnance (code d'une nomenclature, normalisé en majuscules). Aucun format n'est imposé pour
 * ATC et LOCAL (reproduit : « XYZ » est accepté, KB-MED-30) ; CIM10 et LOINC ont un format indicatif.
 */
public record Medicament(String nomenclature, String code, String libelle) {

    public static final Set<String> NOMENCLATURES = Set.of("CIM10", "LOINC", "ATC", "LOCAL");
    private static final Pattern CIM10 = Pattern.compile("^[A-Z][0-9]{2}(\\.[0-9A-Z]{1,4})?$");
    private static final Pattern LOINC = Pattern.compile("^\\d{1,5}-\\d$");

    public Medicament {
        if (nomenclature == null || !NOMENCLATURES.contains(nomenclature)) throw new SaisieInvalide("Nomenclature de codage inconnue");
        if (code == null || code.isBlank()) throw new SaisieInvalide("Le code du médicament est obligatoire");
        code = code.trim().toUpperCase(Locale.ROOT);
        if ("CIM10".equals(nomenclature) && !CIM10.matcher(code).matches()) throw new SaisieInvalide("Code CIM-10 invalide");
        if ("LOINC".equals(nomenclature) && !LOINC.matcher(code).matches()) throw new SaisieInvalide("Code LOINC invalide");
        if (code.length() > 20) throw new SaisieInvalide("Le code du médicament est limité à 20 caractères");
        libelle = libelle == null ? null : libelle.trim();
        if (libelle != null && libelle.length() > 255) throw new SaisieInvalide("Le libellé du code est limité à 255 caractères");
    }
}
