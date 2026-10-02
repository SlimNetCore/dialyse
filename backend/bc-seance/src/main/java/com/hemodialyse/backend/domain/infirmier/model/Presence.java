package com.hemodialyse.backend.domain.infirmier.model;

import com.hemodialyse.backend.domain.planning.model.JourSemaine;
import com.hemodialyse.backend.domain.planning.model.Planning.CreneauRef;
import com.hemodialyse.backend.domain.planning.model.Planning.DonneesPlanning;
import com.hemodialyse.backend.domain.planning.model.Planning.SalleRef;
import com.hemodialyse.backend.domain.planning.model.PlanningSemaine.JourPlanning;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Modèle du planning de présence des infirmiers : pour chaque salle, créneau et jour, qui est prévu (roulement,
 * absences déduites, remplaçants ajoutés) face au nombre d'infirmiers requis par le ratio de sécurité.
 */
public final class Presence {

    private Presence() {
    }

    public enum StatutCase {
        /**
         * Le centre est fermé ce jour-là (fermeture hebdomadaire, férié ou fermeture exceptionnelle).
         */
        FERME,
        /**
         * Aucun patient n'est placé sur ce créneau : aucun infirmier requis.
         */
        SANS_PATIENT,
        COUVERT,
        SOUS_EFFECTIF
    }

    public enum TypeConflit {
        /**
         * Un infirmier est prévu dans deux salles sur le même créneau le même jour.
         */
        DOUBLE_AFFECTATION,
        /**
         * Un infirmier non habilité est prévu en salle d'isolement.
         */
        NON_HABILITE
    }

    public enum RaisonRemplacement {
        SALLE_CONNUE,
        CRENEAU_HABITUEL,
        CHARGE_FAIBLE,
        JOUR_LIBRE,
        HABILITE_ISOLEMENT,
        DOUBLE_VACATION
    }

    /**
     * Situation d'un infirmier sur un créneau de sa semaine.
     */
    public enum SituationPersonnelle {
        PREVU,
        REMPLACANT,
        ABSENT
    }

    public record InfirmierRef(UUID id, String nom, QualificationInfirmier qualification, boolean habiliteIsolement) {
    }

    /**
     * Données du calcul.
     *
     * @param planning             salles, créneaux, patients placés, jours d'ouverture, isolement et fermetures
     * @param patientsParInfirmier ratio de sécurité (patients maximum par infirmier)
     * @param infirmiers           infirmiers actifs du centre
     */
    public record DonneesPresence(
            DonneesPlanning planning,
            int patientsParInfirmier,
            List<InfirmierRef> infirmiers,
            List<AffectationInfirmier> affectations,
            List<AbsenceInfirmier> absences,
            List<RemplacementInfirmier> remplacements
    ) {
    }

    /**
     * Infirmier prévu sur une case ; {@code remplacementId} est renseigné pour un remplaçant (annulation possible).
     */
    public record Present(UUID infirmierId, String nom, boolean habiliteIsolement, boolean remplacant,
                          UUID remplacementId) {
    }

    /**
     * Infirmier normalement prévu sur la case mais absent ce jour-là.
     */
    public record Absent(UUID infirmierId, String nom, TypeAbsence type) {
    }

    public record CasePresence(
            UUID salleId,
            UUID creneauId,
            JourSemaine jour,
            LocalDate date,
            int patients,
            int requis,
            boolean salleIsolement,
            StatutCase statut,
            int manque,
            List<Present> presents,
            List<Absent> absents
    ) {
    }

    public record ConflitPresence(TypeConflit type, LocalDate date, JourSemaine jour, UUID salleId, UUID creneauId,
                                  String infirmier) {
    }

    public record SemainePresence(
            LocalDate debut,
            LocalDate fin,
            List<JourPlanning> jours,
            List<SalleRef> salles,
            List<CreneauRef> creneaux,
            List<CasePresence> cases,
            List<ConflitPresence> conflits,
            int patientsParInfirmier,
            int casesSousEffectif
    ) {
    }

    /**
     * Case sous-effectif signalée à l'avance.
     */
    public record AlertePresence(LocalDate date, JourSemaine jour, UUID salleId, UUID creneauId, int patients,
                                 int requis, int manque, List<String> absents) {
    }

    /**
     * Remplaçant possible pour une case ; {@code score} de 0 à 100 (100 = idéal).
     */
    public record Candidat(UUID infirmierId, String nom, QualificationInfirmier qualification,
                           boolean habiliteIsolement, int score, List<RaisonRemplacement> raisons,
                           int seancesSemaine) {
    }

    public record ChargeInfirmier(UUID infirmierId, String nom, int seances, int remplacements, int joursAbsence,
                                  int total) {
    }

    public record ChargeMensuelle(List<ChargeInfirmier> infirmiers, double moyenne) {
    }

    /**
     * Créneau d'un infirmier dans la semaine (« mon planning »).
     */
    public record CreneauPersonnel(LocalDate date, JourSemaine jour, UUID salleId, UUID creneauId,
                                   SituationPersonnelle situation) {
    }
}
