package com.hemodialyse.backend.contexts.absencespatients.domain;

/**
 * Motif d'une absence de patient. {@link #NON_JUSTIFIEE} est le seul motif qui ne justifie pas l'absence ; {@link #AUTRE}
 * exige un commentaire (RG-ABS-004, 005).
 */
public enum MotifAbsence {
    HOSPITALISATION(true),
    MALADIE(true),
    VOYAGE(true),
    TRANSPORT(true),
    FAMILIAL(true),
    REFUS_PATIENT(true),
    DECES(true),
    AUTRE(true),
    NON_JUSTIFIEE(false);

    private final boolean justifiante;

    MotifAbsence(boolean justifiante) {
        this.justifiante = justifiante;
    }

    public boolean justifiante() {
        return justifiante;
    }

    public boolean commentaireRequis() {
        return this == AUTRE;
    }
}
