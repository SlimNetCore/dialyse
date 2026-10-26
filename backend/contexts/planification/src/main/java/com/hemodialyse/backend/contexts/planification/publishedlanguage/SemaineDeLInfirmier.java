package com.hemodialyse.backend.contexts.planification.publishedlanguage;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Port consommé par la Planification (ADR 0053), fourni par le legacy (présence et planning de la semaine, slices P4 et P5) : la semaine
 * d'un infirmier dans la grille du centre, pour « Mon planning ». Lecture seule. Les jours sont les noms {@code DIMANCHE} à
 * {@code SAMEDI} ; les noms de champs sont ceux du JSON historique.
 */
public interface SemaineDeLInfirmier {

    /**
     * @param date un jour de la semaine voulue (semaine du dimanche au samedi)
     */
    PlanningDeLaSemaine semaine(UUID centre, UUID infirmier, LocalDate date);

    record Salle(UUID id, String nom) {
    }

    record Creneau(UUID id, String libelle, int ordre) {
    }

    /**
     * Situation de l'infirmier sur un créneau : {@code PREVU}, {@code REMPLACANT} ou {@code ABSENT}.
     */
    record CreneauPersonnel(LocalDate date, String jour, UUID salleId, UUID creneauId, String situation) {
    }

    record JourDuPlanning(String jour, LocalDate date, boolean ouvertHebdomadaire, String fermetureMotif) {
    }

    record Occupant(UUID patientId, String nom, String generateurCode, boolean aRisque, LocalDate libereLe,
                    boolean temporaire,
                    boolean realiseeHorsPlanning, LocalDate dejaRealiseeLe) {
    }

    record CaseDeLInfirmier(LocalDate date, String jour, UUID salleId, UUID creneauId, int patients, int requis,
                            boolean salleIsolement, int collegues, List<Occupant> occupants) {
    }

    record PlanningDeLaSemaine(LocalDate debut, LocalDate fin, List<Salle> salles, List<Creneau> creneaux,
                               List<CreneauPersonnel> mesCreneaux, List<JourDuPlanning> jours,
                               List<CaseDeLInfirmier> mesCases) {
    }
}
