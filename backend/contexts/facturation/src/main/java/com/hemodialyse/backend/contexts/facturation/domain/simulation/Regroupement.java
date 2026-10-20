package com.hemodialyse.backend.contexts.facturation.domain.simulation;

/**
 * Regroupement des séances en factures (RG-FAC-011) : une facture par patient (regroupement multi-forfait activé), ou une
 * facture par patient et par forfait.
 */
public enum Regroupement {
    PAR_PATIENT,
    PAR_PATIENT_ET_FORFAIT;

    public static Regroupement multiForfait(boolean regroupementMultiForfait) {
        return regroupementMultiForfait ? PAR_PATIENT : PAR_PATIENT_ET_FORFAIT;
    }

    String cle(SeanceFacturable seance) {
        return this == PAR_PATIENT
                ? seance.patient().id().toString()
                : seance.patient().id() + "::" + seance.forfait().cleDeForfait();
    }
}
