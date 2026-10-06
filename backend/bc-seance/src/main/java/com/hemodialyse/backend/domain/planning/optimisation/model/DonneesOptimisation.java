package com.hemodialyse.backend.domain.planning.optimisation.model;

import com.hemodialyse.backend.domain.infirmier.model.Presence.DonneesPresence;
import com.hemodialyse.backend.domain.planning.model.JourSemaine;
import com.hemodialyse.backend.domain.planning.model.Planning.DonneesPlanning;
import com.hemodialyse.backend.domain.planning.model.Planning.Occupation;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Tout ce que l'optimiseur lit d'un centre : planning (salles, créneaux, générateurs en service, isolement,
 * fermetures), patients qui occupent ou attendent une place, et personnel (infirmiers, roulement, absences,
 * remplacements). Toujours borné à un centre (AGENTS.md §2) par l'adaptateur de lecture.
 *
 * @param presence données du planning de présence, dont {@code planning().occupations()} = placements actuels
 * @param patients patients actifs du centre, placés ou non
 */
public record DonneesOptimisation(DonneesPresence presence, List<PatientAPlacer> patients) {

    public DonneesOptimisation {
        patients = patients == null ? List.of() : List.copyOf(patients);
    }

    /**
     * Patient à placer : ses jours de dialyse sont prescrits et ne changent pas ; seule sa place est optimisée.
     *
     * @param actuelle   place actuelle, nulle si le patient n'est pas (complètement) placé
     * @param premierJour date d'admission (nulle : déjà admis)
     * @param dernierJour dernier jour d'occupation (nul : sans limite)
     */
    public record PatientAPlacer(UUID patientId, String nom, Set<JourSemaine> jours, boolean aRisque, Poste actuelle,
                                 LocalDate premierJour, LocalDate dernierJour) {
        public PatientAPlacer {
            jours = jours == null ? Set.of() : Set.copyOf(jours);
        }

        public Occupation versOccupation(Poste poste) {
            return new Occupation(patientId, poste.salleId(), poste.creneauId(), poste.generateurId(), jours, aRisque,
                    dernierJour, premierJour);
        }
    }

    public DonneesPlanning planning() {
        return presence.planning();
    }

    /**
     * Mêmes données avec d'autres placements : les patients absents de {@code placements} ne tiennent plus de place.
     */
    public DonneesOptimisation avecPlacements(Map<UUID, Poste> placements) {
        List<Occupation> occupations = new ArrayList<>();
        for (PatientAPlacer p : patients) {
            Poste poste = placements.get(p.patientId());
            if (poste != null && poste.salleId() != null && poste.creneauId() != null) {
                occupations.add(p.versOccupation(poste));
            }
        }
        DonneesPlanning ancien = presence.planning();
        DonneesPlanning planning = new DonneesPlanning(ancien.salles(), ancien.creneaux(), ancien.generateurs(),
                occupations, ancien.joursOuverts(), ancien.sallesIsolement(), ancien.fermetures());
        return new DonneesOptimisation(new DonneesPresence(planning, presence.patientsParInfirmier(),
                presence.infirmiers(), presence.affectations(), presence.absences(), presence.remplacements()), patients);
    }

    /**
     * Semaine type : sans fermeture datée, absence ni remplacement, pour concevoir un roulement indépendant des aléas.
     */
    public DonneesOptimisation sansAleas() {
        DonneesPlanning ancien = presence.planning();
        DonneesPlanning planning = new DonneesPlanning(ancien.salles(), ancien.creneaux(), ancien.generateurs(),
                ancien.occupations(), ancien.joursOuverts(), ancien.sallesIsolement(), List.of());
        return new DonneesOptimisation(new DonneesPresence(planning, presence.patientsParInfirmier(),
                presence.infirmiers(), presence.affectations(), List.of(), List.of()), patients);
    }

    /**
     * Placements actuels des patients déjà placés.
     */
    public Map<UUID, Poste> placementsActuels() {
        java.util.LinkedHashMap<UUID, Poste> placements = new java.util.LinkedHashMap<>();
        for (PatientAPlacer p : patients) {
            if (p.actuelle() != null) placements.put(p.patientId(), p.actuelle());
        }
        return placements;
    }
}
