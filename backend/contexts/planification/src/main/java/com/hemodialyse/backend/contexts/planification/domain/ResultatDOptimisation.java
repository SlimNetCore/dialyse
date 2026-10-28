package com.hemodialyse.backend.contexts.planification.domain;

import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CreneauId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.InfirmierId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.PatientId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.SalleId;
import com.hemodialyse.backend.contexts.planification.domain.RessourcesDuCentre.Creneau;
import com.hemodialyse.backend.contexts.planification.domain.RessourcesDuCentre.Salle;

import java.time.LocalDate;
import java.util.List;

/**
 * Proposition d'optimisation : ce qui change (patients déplacés, vacations des infirmiers), ce qui reste à résoudre (patients non
 * placés, vacations non pourvues) et les indicateurs avant / après. Rien n'est appliqué tant que l'administration ne valide pas la
 * proposition (P23).
 */
public record ResultatDOptimisation(
        List<Salle> salles,
        List<Creneau> creneaux,
        List<DeplacementDePatient> deplacements,
        List<PatientNonPlace> nonPlaces,
        List<VacationPlanifiee> vacations,
        List<VacationNonPourvue> manques,
        Indicateurs avant,
        Indicateurs apres,
        List<DeplacementTemporairePropose> temporaires,
        List<SeanceSansSolution> seancesSansSolution
) {

    public ResultatDOptimisation {
        salles = salles == null ? List.of() : List.copyOf(salles);
        creneaux = creneaux == null ? List.of() : List.copyOf(creneaux);
        deplacements = deplacements == null ? List.of() : List.copyOf(deplacements);
        nonPlaces = nonPlaces == null ? List.of() : List.copyOf(nonPlaces);
        vacations = vacations == null ? List.of() : List.copyOf(vacations);
        manques = manques == null ? List.of() : List.copyOf(manques);
        temporaires = temporaires == null ? List.of() : List.copyOf(temporaires);
        seancesSansSolution = seancesSansSolution == null ? List.of() : List.copyOf(seancesSansSolution);
    }

    public ResultatDOptimisation(List<Salle> salles, List<Creneau> creneaux, List<DeplacementDePatient> deplacements,
                                 List<PatientNonPlace> nonPlaces, List<VacationPlanifiee> vacations,
                                 List<VacationNonPourvue> manques, Indicateurs avant, Indicateurs apres) {
        this(salles, creneaux, deplacements, nonPlaces, vacations, manques, avant, apres, List.of(), List.of());
    }

    /** Synthèse affichée dans l'historique (sans le détail des déplacements ni des vacations). */
    public Resume resume() {
        return new Resume(deplacements.size(), nonPlaces.size(), vacations.size(), manques.size(), avant, apres,
                temporaires.size(), seancesSansSolution.size());
    }

    public record Resume(int deplacements, int nonPlaces, int vacations, int manques, Indicateurs avant, Indicateurs apres,
                         int temporaires, int seancesSansSolution) {
    }

    /**
     * Patient dont la place change ({@code de} nul : patient jusqu'ici non placé). {@code jours} : nouveaux jours de dialyse quand
     * l'optimisation les a choisis, nul sinon (jours inchangés).
     */
    public record DeplacementDePatient(PatientId patient, String nom, Poste de, Poste vers, List<JourDeLaSemaine> jours) {
        public DeplacementDePatient {
            jours = jours == null ? null : List.copyOf(jours);
        }

        public DeplacementDePatient(PatientId patient, String nom, Poste de, Poste vers) {
            this(patient, nom, de, vers, null);
        }
    }

    /** Séance datée déplacée parce que le générateur habituel est indisponible ce jour-là. */
    public record DeplacementTemporairePropose(PatientId patient, String nom, LocalDate date, JourDeLaSemaine jour, Poste de,
                                               Poste vers, String motif) {
    }

    /** Séance datée dont le générateur est indisponible et qu'aucune place libre ne peut accueillir : à organiser. */
    public record SeanceSansSolution(PatientId patient, String nom, LocalDate date, JourDeLaSemaine jour, Poste de, String motif) {
    }

    public enum CauseNonPlace {
        AUCUNE_PLACE,
        ISOLEMENT_IMPOSSIBLE,
        JOURS_FERMES
    }

    public record PatientNonPlace(PatientId patient, String nom, CauseNonPlace cause) {
    }

    /** @param existante vrai si l'infirmier était déjà prévu sur cette case (roulement ou remplacement en place) */
    public record VacationPlanifiee(LocalDate date, JourDeLaSemaine jour, SalleId salle, CreneauId creneau, InfirmierId infirmier,
                                    String nom, boolean existante) {
    }

    public record VacationNonPourvue(LocalDate date, JourDeLaSemaine jour, SalleId salle, CreneauId creneau, int manque) {
    }

    /**
     * Mesures de consommation de ressources (par semaine type pour les patients et les salles ; sur tout l'horizon pour le
     * personnel), recalculées par le domaine indépendamment du solveur (P24).
     *
     * @param generateursUtilises        générateurs qui servent au moins un patient
     * @param sallesOuvertes             cases (salle, créneau, jour) où au moins un patient dialyse, sur la semaine type
     * @param vacationsRequises          vacations d'infirmiers exigées par le ratio de sécurité, sur la semaine type
     * @param placesInfirmierInutilisees patients que les infirmiers requis pourraient encore suivre
     * @param patientsNonPlaces          patients sans place complète
     * @param vacationsNonPourvues       vacations exigées sans infirmier prévu, sur l'horizon
     * @param infirmiersMobilises        infirmiers qui travaillent au moins une fois sur l'horizon
     * @param ecartCharge                écart entre l'infirmier le plus et le moins sollicité (vacations)
     * @param depassementsHebdo          vacations au-delà du maximum hebdomadaire, tous infirmiers confondus
     */
    public record Indicateurs(int generateursUtilises, int sallesOuvertes, int vacationsRequises, int placesInfirmierInutilisees,
                              int patientsNonPlaces, int vacationsNonPourvues, int infirmiersMobilises, int ecartCharge,
                              int depassementsHebdo) {
    }
}
