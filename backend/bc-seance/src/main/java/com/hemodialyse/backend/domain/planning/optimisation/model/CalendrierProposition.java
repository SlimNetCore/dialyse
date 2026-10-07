package com.hemodialyse.backend.domain.planning.optimisation.model;

import com.hemodialyse.backend.domain.planning.model.JourSemaine;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Planning calendaire d'une proposition d'optimisation : pour chaque semaine de l'horizon, chaque salle et créneau
 * affichent, jour par jour, les patients (avec leur générateur) et les infirmiers prévus. Il est figé au moment du
 * calcul, de sorte qu'une proposition reste lisible et imprimable même si le centre évolue ensuite.
 */
public final class CalendrierProposition {

    private CalendrierProposition() {
    }

    /**
     * Situation d'un infirmier sur une case : prévu au roulement, remplaçant, absent ce jour-là, ou ajouté par la
     * proposition.
     */
    public enum SituationInfirmier {
        PREVU,
        REMPLACANT,
        ABSENT,
        NOUVEAU
    }

    /**
     * @param deplace    patient dont la place change dans la proposition
     * @param temporaire patient placé ici seulement ce jour-là (générateur indisponible à sa place habituelle)
     * @param avant      place qu'il occupait avant la proposition (« salle · créneau · générateur », ou sa place
     *                   habituelle pour une place temporaire) ; null s'il n'avait pas de place ou s'il n'est pas déplacé
     */
    public record PatientCalendrier(UUID patientId, String nom, String generateurCode, boolean aRisque,
                                    boolean deplace, boolean temporaire, String avant) {
        public PatientCalendrier(UUID patientId, String nom, String generateurCode, boolean aRisque,
                                 boolean deplace, boolean temporaire) {
            this(patientId, nom, generateurCode, aRisque, deplace, temporaire, null);
        }
    }

    public record InfirmierCalendrier(String nom, SituationInfirmier situation) {
    }

    /**
     * Un jour d'une case. {@code requis} : infirmiers exigés par le ratio de sécurité pour les patients présents ;
     * {@code manque} : ce qu'il reste à pourvoir ; {@code surplus} : infirmiers prévus au-delà de l'effectif requis
     * (personnel payé sans activité utile, une salle sans patient comprise).
     */
    public record JourCalendrier(JourSemaine jour, LocalDate date, boolean ferme, String motifFermeture, int requis,
                                 int manque, int surplus, List<PatientCalendrier> patients,
                                 List<InfirmierCalendrier> infirmiers) {
        public JourCalendrier {
            patients = patients == null ? List.of() : List.copyOf(patients);
            infirmiers = infirmiers == null ? List.of() : List.copyOf(infirmiers);
        }

        public JourCalendrier(JourSemaine jour, LocalDate date, boolean ferme, String motifFermeture, int requis,
                              int manque, List<PatientCalendrier> patients, List<InfirmierCalendrier> infirmiers) {
            this(jour, date, ferme, motifFermeture, requis, manque, 0, patients, infirmiers);
        }

        public boolean vide() {
            return patients.isEmpty() && infirmiers.isEmpty();
        }
    }

    /**
     * Une ligne du calendrier : une salle et un créneau d'une semaine, avec ses sept jours (dimanche → samedi).
     */
    public record CaseCalendrier(LocalDate semaineDebut, UUID salleId, String salleNom, int salleOrdre, UUID creneauId,
                                 String creneauLibelle, int creneauOrdre, List<JourCalendrier> jours) {
        public CaseCalendrier {
            jours = jours == null ? List.of() : List.copyOf(jours);
        }
    }
}
