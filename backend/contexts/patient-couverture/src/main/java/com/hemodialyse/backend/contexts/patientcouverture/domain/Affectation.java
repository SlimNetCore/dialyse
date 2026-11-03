package com.hemodialyse.backend.contexts.patientcouverture.domain;

import java.util.UUID;

/**
 * Affectation de la fiche : médecin, transport et PLACE (salle, créneau, générateur, jours). La place est contrôlée et écrite
 * par la Planification (ADR 0052) ; la fiche la porte et la rend telle quelle. Jours de dialyse : dimanche à samedi.
 */
public record Affectation(UUID medecinTraitantId, UUID salleId, UUID creneauId, UUID transporteurAllerId,
                          UUID transporteurRetourId, UUID categorieTransportId, UUID generateurId, Jours jours) {

    public Affectation {
        jours = jours == null ? Jours.aucun() : jours;
    }

    /** Même place (salle, créneau, générateur, jours) que l'autre affectation ? */
    public boolean memePlaceQue(Affectation autre) {
        return java.util.Objects.equals(salleId, autre.salleId) && java.util.Objects.equals(creneauId, autre.creneauId)
                && java.util.Objects.equals(generateurId, autre.generateurId) && jours.equals(autre.jours);
    }

    public record Jours(boolean dimanche, boolean lundi, boolean mardi, boolean mercredi, boolean jeudi, boolean vendredi,
                        boolean samedi) {
        public static Jours aucun() {
            return new Jours(false, false, false, false, false, false, false);
        }

        /** Codes en toutes lettres séparés par des virgules (colonne {@code mouvement_patient.jours_dialyse}). */
        public String codes() {
            StringBuilder s = new StringBuilder();
            ajouter(s, dimanche, "DIMANCHE");
            ajouter(s, lundi, "LUNDI");
            ajouter(s, mardi, "MARDI");
            ajouter(s, mercredi, "MERCREDI");
            ajouter(s, jeudi, "JEUDI");
            ajouter(s, vendredi, "VENDREDI");
            ajouter(s, samedi, "SAMEDI");
            return s.toString();
        }

        private static void ajouter(StringBuilder s, boolean jour, String code) {
            if (!jour) return;
            if (s.length() > 0) s.append(',');
            s.append(code);
        }
    }
}
