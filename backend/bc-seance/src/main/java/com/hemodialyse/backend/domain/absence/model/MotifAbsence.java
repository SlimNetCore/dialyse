package com.hemodialyse.backend.domain.absence.model;

/**
 * Motif d'une absence de patient à une séance de dialyse. {@link #NON_JUSTIFIEE} est le seul motif qui ne justifie
 * pas l'absence ; {@link #AUTRE} exige un commentaire.
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

    /**
     * Vrai si le motif justifie l'absence (statut « justifiée »), faux pour « non justifiée ».
     */
    public boolean justifiante() {
        return justifiante;
    }

    /**
     * Vrai si un commentaire est obligatoire avec ce motif.
     */
    public boolean commentaireRequis() {
        return this == AUTRE;
    }
}
