package com.hemodialyse.backend.domain.medical.shared.valueobject;

/**
 * Référentiel de codage d'un concept clinique — la contrepartie du {@code system} FHIR
 * d'un {@code CodeableConcept} (AGENTS.md §14 : pas de primitive obsession sur les codes).
 */
public enum CodingSystem {
    /**
     * Classification Internationale des Maladies, 10e révision — diagnostics/antécédents.
     */
    CIM10,
    /**
     * Logical Observation Identifiers Names and Codes — analytes biologiques/examens.
     */
    LOINC,
    /**
     * Anatomical Therapeutic Chemical — médicaments (ordonnances).
     */
    ATC,
    /**
     * Code interne au centre, hors nomenclature standard (dernier recours).
     */
    LOCAL
}
