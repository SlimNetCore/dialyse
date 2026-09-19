package com.hemodialyse.backend.domain.medical.kdigo.valueobject;

/**
 * Stade de maladie rénale chronique (classification KDIGO par DFG estimé, mL/min/1,73 m²) :
 * G1 ≥ 90, G2 60-89, G3a 45-59, G3b 30-44, G4 15-29, G5 &lt; 15.
 */
public enum StadeCkd {
    G1,
    G2,
    G3A,
    G3B,
    G4,
    G5,
    NON_EVALUABLE
}
