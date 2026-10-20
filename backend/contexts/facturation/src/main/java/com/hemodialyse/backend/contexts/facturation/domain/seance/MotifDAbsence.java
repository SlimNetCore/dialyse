package com.hemodialyse.backend.contexts.facturation.domain.seance;

/**
 * Motif de l'absence que déclare l'exclusion d'une séance (KB-FAC-13). Mêmes valeurs que les motifs d'absence du module
 * Absences (traduits par l'ACL) ; {@link #AUTRE} exige un commentaire.
 */
public enum MotifDAbsence {
    HOSPITALISATION,
    MALADIE,
    VOYAGE,
    TRANSPORT,
    FAMILIAL,
    REFUS_PATIENT,
    DECES,
    AUTRE,
    NON_JUSTIFIEE;

    /**
     * Le motif de ce nom ; refusé (400) s'il est absent ou inconnu.
     */
    public static MotifDAbsence depuis(String nom) {
        if (nom == null || nom.isBlank()) {
            throw new IllegalArgumentException("Le motif d'exclusion est obligatoire");
        }
        try {
            return valueOf(nom.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Motif d'exclusion inconnu : " + nom);
        }
    }

    public boolean commentaireRequis() {
        return this == AUTRE;
    }
}
