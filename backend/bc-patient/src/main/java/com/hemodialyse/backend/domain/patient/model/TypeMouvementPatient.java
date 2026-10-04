package com.hemodialyse.backend.domain.patient.model;

/**
 * Nature d'un mouvement de patient dans le centre (entrée, séjour temporaire, sorties, libération de la place).
 */
public enum TypeMouvementPatient {
    /**
     * Admission du patient dans le centre.
     */
    ADMISSION,
    /**
     * Passage en séjour de durée limitée (occasionnel, vacancier) ; la date est la fin de séjour.
     */
    SEJOUR_TEMPORAIRE,
    /**
     * Retour à l'état permanent.
     */
    REPRISE,
    TRANSFERT,
    DECES,
    GREFFE,
    GUERISON,
    /**
     * La place (salle, créneau, générateur, jours) a été libérée à l'échéance d'une sortie ou d'une fin de séjour.
     */
    PLACE_LIBEREE;

    /**
     * Mouvement correspondant à l'état saisi sur la fiche.
     */
    public static TypeMouvementPatient pourEtat(String etat) {
        if (etat == null) return REPRISE;
        return switch (etat) {
            case "OCCASIONNEL", "VACANCIER_LOCAL", "VACANCIER_ETRANGER" -> SEJOUR_TEMPORAIRE;
            case "TRANSFERE" -> TRANSFERT;
            case "DECEDE" -> DECES;
            case "GREFFE" -> GREFFE;
            case "GUERRI" -> GUERISON;
            default -> REPRISE;
        };
    }

    /**
     * Le mouvement met-il fin à la prise en charge dans le centre ?
     */
    public boolean sortie() {
        return this == TRANSFERT || this == DECES || this == GREFFE || this == GUERISON;
    }
}
