package com.hemodialyse.backend.contexts.planification.application;

import java.time.LocalDate;

/**
 * Une ligne du planning de présence des infirmiers tel qu'il s'imprime (DOC5e, ADR 0197) : un infirmier, dans une salle et un créneau, un
 * jour de la semaine, avec sa situation ce jour-là. Le nom est le nom complet prêt à imprimer (prénom puis nom, le prénom absent est omis).
 */
public record LigneDuPlanningDePresence(LocalDate jour, String salle, String creneauCode, String creneau, String infirmier,
                                        Situation situation) {

    /** Situation d'un infirmier ce jour-là, avec son libellé imprimé. */
    public enum Situation {
        PREVU("Prévu"), ABSENT("Absent"), REMPLACANT("Remplaçant");

        private final String libelle;

        Situation(String libelle) {
            this.libelle = libelle;
        }

        public String libelle() {
            return libelle;
        }
    }
}
