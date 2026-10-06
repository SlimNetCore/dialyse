package com.hemodialyse.backend.domain.planning.optimisation.model;

import com.hemodialyse.backend.domain.planning.model.JourSemaine;
import com.hemodialyse.backend.domain.planning.model.Planning.CreneauRef;
import com.hemodialyse.backend.domain.planning.model.Planning.SalleRef;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Proposition d'optimisation : ce qui change (patients déplacés, vacations des infirmiers), ce qui reste à résoudre
 * (patients non placés, vacations non pourvues) et les indicateurs avant / après. Rien n'est appliqué tant que
 * l'administration ne valide pas la proposition.
 */
public record ResultatOptimisation(
        List<SalleRef> salles,
        List<CreneauRef> creneaux,
        List<DeplacementPatient> deplacements,
        List<PatientNonPlace> nonPlaces,
        List<VacationPlanifiee> vacations,
        List<VacationNonPourvue> manques,
        Indicateurs avant,
        Indicateurs apres
) {

    public ResultatOptimisation {
        salles = salles == null ? List.of() : List.copyOf(salles);
        creneaux = creneaux == null ? List.of() : List.copyOf(creneaux);
        deplacements = deplacements == null ? List.of() : List.copyOf(deplacements);
        nonPlaces = nonPlaces == null ? List.of() : List.copyOf(nonPlaces);
        vacations = vacations == null ? List.of() : List.copyOf(vacations);
        manques = manques == null ? List.of() : List.copyOf(manques);
    }

    /**
     * Synthèse affichée dans l'historique des optimisations (sans le détail des déplacements ni des vacations).
     */
    public Resume resume() {
        return new Resume(deplacements.size(), nonPlaces.size(), vacations.size(), manques.size(), avant, apres);
    }

    public record Resume(int deplacements, int nonPlaces, int vacations, int manques, Indicateurs avant,
                         Indicateurs apres) {
    }

    /**
     * Patient dont la place change ({@code de} nul : patient jusqu'ici non placé).
     */
    public record DeplacementPatient(UUID patientId, String nom, Poste de, Poste vers) {
    }

    public enum CauseNonPlace {
        AUCUNE_PLACE,
        ISOLEMENT_IMPOSSIBLE,
        JOURS_FERMES
    }

    public record PatientNonPlace(UUID patientId, String nom, CauseNonPlace cause) {
    }

    /**
     * @param existante vrai si l'infirmier était déjà prévu sur cette case (roulement ou remplacement en place)
     */
    public record VacationPlanifiee(LocalDate date, JourSemaine jour, UUID salleId, UUID creneauId, UUID infirmierId,
                                    String nom, boolean existante) {
    }

    public record VacationNonPourvue(LocalDate date, JourSemaine jour, UUID salleId, UUID creneauId, int manque) {
    }

    /**
     * Mesures de consommation de ressources (par semaine type pour les patients et les salles ; sur tout l'horizon
     * pour le personnel).
     *
     * @param generateursUtilises        générateurs qui servent au moins un patient
     * @param sallesOuvertes             cases (salle, créneau, jour) où au moins un patient dialyse, sur la semaine type
     * @param vacationsRequises          vacations d'infirmiers exigées par le ratio de sécurité, sur la semaine type
     * @param placesInfirmierInutilisees patients que les infirmiers requis pourraient encore suivre (ratio non rempli)
     * @param patientsNonPlaces          patients sans place complète
     * @param vacationsNonPourvues       vacations exigées sans infirmier prévu, sur l'horizon
     * @param infirmiersMobilises        infirmiers qui travaillent au moins une fois sur l'horizon
     * @param ecartCharge                écart entre l'infirmier le plus et le moins sollicité (vacations)
     * @param depassementsHebdo          vacations au-delà du maximum hebdomadaire, tous infirmiers confondus
     */
    public record Indicateurs(int generateursUtilises, int sallesOuvertes, int vacationsRequises,
                              int placesInfirmierInutilisees, int patientsNonPlaces, int vacationsNonPourvues,
                              int infirmiersMobilises, int ecartCharge, int depassementsHebdo) {
    }
}
