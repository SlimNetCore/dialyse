package com.hemodialyse.backend.domain.medical.shared.valueobject;

import com.hemodialyse.backend.domain.shared.exception.BusinessException;

import java.util.regex.Pattern;

/**
 * Value Object — concept clinique codé (diagnostic, analyte, médicament...), l'équivalent
 * d'un {@code CodeableConcept} FHIR. Utilisé partout où une valeur serait sinon une chaîne
 * libre non validée (AGENTS.md §14 : « aucune primitive obsession n'est autorisée »).
 * <p>
 * Le code est normalisé (majuscules, espaces superflus retirés) et validé selon un format
 * indicatif par système — un contrôle de forme, pas une vérification d'existence dans le
 * référentiel (celle-ci reste du ressort de {@code CodeReferentielRepositoryPort}).
 */
public final class ConceptCode {

    private static final Pattern CIM10_PATTERN = Pattern.compile("^[A-Z][0-9]{2}(\\.[0-9A-Z]{1,4})?$");
    private static final Pattern LOINC_PATTERN = Pattern.compile("^\\d{1,5}-\\d$");

    private final CodingSystem system;
    private final String code;
    private final String display;

    private ConceptCode(CodingSystem system, String code, String display) {
        this.system = system;
        this.code = code;
        this.display = display;
    }

    public static ConceptCode of(CodingSystem system, String rawCode, String display) {
        if (system == null) {
            throw new BusinessException("CONCEPT_CODE_SYSTEM_REQUIRED", "Le système de codage est obligatoire");
        }
        if (rawCode == null || rawCode.isBlank()) {
            throw new BusinessException("CONCEPT_CODE_REQUIRED", "Le code est obligatoire");
        }
        String normalized = rawCode.trim().toUpperCase();
        validateFormat(system, normalized);
        String normalizedDisplay = display == null ? null : display.trim();
        return new ConceptCode(system, normalized, normalizedDisplay);
    }

    private static void validateFormat(CodingSystem system, String code) {
        switch (system) {
            case CIM10 -> {
                if (!CIM10_PATTERN.matcher(code).matches()) {
                    throw new BusinessException("CONCEPT_CODE_INVALID_CIM10", "Code CIM-10 invalide : " + code);
                }
            }
            case LOINC -> {
                if (!LOINC_PATTERN.matcher(code).matches()) {
                    throw new BusinessException("CONCEPT_CODE_INVALID_LOINC", "Code LOINC invalide : " + code);
                }
            }
            case ATC, LOCAL -> {
                // Pas de format normatif imposé : nomenclatures locales ou ATC saisi tel quel.
            }
        }
    }

    public CodingSystem system() {
        return system;
    }

    public String code() {
        return code;
    }

    public String display() {
        return display;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ConceptCode other)) return false;
        return system == other.system && code.equals(other.code);
    }

    @Override
    public int hashCode() {
        return 31 * system.hashCode() + code.hashCode();
    }

    @Override
    public String toString() {
        return system + ":" + code;
    }
}
