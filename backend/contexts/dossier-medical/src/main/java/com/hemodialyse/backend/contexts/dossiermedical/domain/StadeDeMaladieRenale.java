package com.hemodialyse.backend.contexts.dossiermedical.domain;

/**
 * Stade de maladie rénale chronique (classification KDIGO par DFG estimé, mL/min/1,73 m²) : G1 ≥ 90, G2 60-89, G3a 45-59, G3b 30-44,
 * G4 15-29, G5 &lt; 15. Noms rendus tels quels par l'API (parité DM7).
 */
public enum StadeDeMaladieRenale {
    G1,
    G2,
    G3A,
    G3B,
    G4,
    G5,
    NON_EVALUABLE
}
