package com.hemodialyse.backend.contexts.planification.domain;

import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CentreId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CreneauId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.GenerateurId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.PatientId;
import com.hemodialyse.backend.contexts.planification.domain.Presence.DonneesDePresence;
import com.hemodialyse.backend.contexts.planification.domain.Presence.Personnel;
import com.hemodialyse.backend.contexts.planification.domain.Presence.PlanningDesPlaces;
import com.hemodialyse.backend.contexts.planification.domain.SemaineDuPlanning.SeanceRealisee;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Instantané du centre soumis à l'optimisation, lu en une fois par le cas d'usage : ressources, personnel (roulement, absences,
 * remplacements, ratio), patients à placer, profils, indisponibilités de générateurs, déplacements temporaires, séances réalisées et
 * préférences de l'horizon. Le calcul de présence est LE même que celui de la lecture (P4) : {@link #presence()} en dérive, il n'y a
 * plus de second calcul (dette P4 -> P8).
 */
public record DonneesDOptimisation(CentreId centre, RessourcesDuCentre ressources, Personnel personnel,
                                   List<PatientAPlacer> patients, Map<UUID, ProfilDePlanification> profils,
                                   List<IndisponibiliteDatee> indisponibilites, List<DeplacementTemporaire> temporaires,
                                   List<SeanceRealisee> seances, List<PreferenceDePlanification> preferences) {

    public DonneesDOptimisation {
        if (centre == null || ressources == null || personnel == null) throw new SaisieInvalide("Données d'optimisation incomplètes");
        patients = patients == null ? List.of() : List.copyOf(patients);
        profils = profils == null ? Map.of() : Map.copyOf(profils);
        indisponibilites = indisponibilites == null ? List.of() : List.copyOf(indisponibilites);
        temporaires = temporaires == null ? List.of() : List.copyOf(temporaires);
        seances = seances == null ? List.of() : List.copyOf(seances);
        preferences = preferences == null ? List.of() : List.copyOf(preferences);
    }

    /** Un générateur indisponible sur une période (intervention de maintenance planifiée ou en cours). */
    public record IndisponibiliteDatee(GenerateurId generateur, LocalDate debut, LocalDate fin, String motif) {
        public IndisponibiliteDatee {
            if (generateur == null) throw new SaisieInvalide("Générateur requis");
            if (debut == null || fin == null || fin.isBefore(debut)) throw new SaisieInvalide("Période invalide");
        }

        public boolean couvre(LocalDate date) {
            return !date.isBefore(debut) && !date.isAfter(fin);
        }
    }

    /** Un patient actif du centre, avec sa place actuelle (peut être incomplète) et ce qui contraint son placement. */
    public record PatientAPlacer(PatientId patient, String nom, Set<JourDeLaSemaine> jours, boolean aRisque, Poste actuelle,
                                 LocalDate premierJour, LocalDate dernierJour, CreneauId creneauPrefere, Integer seancesAChoisir,
                                 Set<UUID> transporteurs, Set<Competence> competencesRequises) {
        public PatientAPlacer {
            jours = jours == null ? Set.of() : Set.copyOf(jours);
            transporteurs = transporteurs == null ? Set.of() : Set.copyOf(transporteurs);
            competencesRequises = competencesRequises == null ? Set.of() : Set.copyOf(competencesRequises);
        }

        public PatientAPlacer(PatientId patient, String nom, Set<JourDeLaSemaine> jours, boolean aRisque, Poste actuelle,
                              LocalDate premierJour, LocalDate dernierJour) {
            this(patient, nom, jours, aRisque, actuelle, premierJour, dernierJour, null, null, Set.of(), Set.of());
        }

        public boolean joursAChoisir() {
            return seancesAChoisir != null;
        }

        public CategorieDIsolement categorie() {
            return aRisque ? CategorieDIsolement.A_RISQUE : CategorieDIsolement.SANS_RISQUE;
        }

        public PatientAPlacer avecJours(Set<JourDeLaSemaine> nouveaux) {
            return new PatientAPlacer(patient, nom, nouveaux, aRisque, actuelle, premierJour, dernierJour, creneauPrefere,
                    seancesAChoisir, transporteurs, competencesRequises);
        }

        /** Ce que le patient occupe à ce poste (pour les contrôles de placement). */
        public Occupation occupationA(Poste poste) {
            return new Occupation(patient, poste.salle(), poste.creneau(), poste.generateur(), jours, categorie());
        }

        private boolean occupe(Poste poste) {
            return poste != null && poste.salle() != null && poste.creneau() != null && !jours.isEmpty();
        }
    }

    /** Les données de la présence, dérivées des patients placés : la même règle de présence que la lecture (P4). */
    public DonneesDePresence presence() {
        return presenceDes(placementsActuels(), patients, personnel, ressources);
    }

    private static DonneesDePresence presenceDes(Map<PatientId, Poste> placements, List<PatientAPlacer> patients, Personnel personnel,
                                                 RessourcesDuCentre ressources) {
        List<Presence.Occupation> occupations = new ArrayList<>();
        for (PatientAPlacer p : patients) {
            Poste poste = placements.get(p.patient());
            if (p.occupe(poste)) {
                occupations.add(new Presence.Occupation(poste.salle().valeur(), poste.creneau().valeur(), p.jours(), p.premierJour(),
                        p.dernierJour()));
            }
        }
        Set<UUID> isolement = ressources.salles().stream().filter(RessourcesDuCentre.Salle::isolement)
                .map(s -> s.id().valeur()).collect(Collectors.toSet());
        PlanningDesPlaces planning = new PlanningDesPlaces(
                ressources.salles().stream().map(s -> new Presence.Salle(s.id().valeur(), s.nom())).toList(),
                ressources.creneaux().stream().map(c -> new Presence.Creneau(c.id().valeur(), c.libelle(), c.ordre())).toList(),
                occupations, ressources.joursOuverts(), isolement,
                ressources.fermetures().stream().map(f -> new Presence.Fermeture(f.date(), f.motif())).toList());
        return new DonneesDePresence(planning, personnel);
    }

    /** Mêmes données avec ces placements (et ces jours choisis) pour les patients à placer. */
    public DonneesDOptimisation avecPlacements(Map<PatientId, Poste> placements) {
        return avecPlacements(placements, Map.of());
    }

    public DonneesDOptimisation avecPlacements(Map<PatientId, Poste> placements, Map<PatientId, Set<JourDeLaSemaine>> joursChoisis) {
        List<PatientAPlacer> nouveaux = new ArrayList<>();
        for (PatientAPlacer p : patients) {
            Set<JourDeLaSemaine> jours = joursChoisis.get(p.patient());
            PatientAPlacer patient = jours == null ? p : p.avecJours(jours);
            Poste poste = placements.get(p.patient());
            nouveaux.add(new PatientAPlacer(patient.patient(), patient.nom(), patient.jours(), patient.aRisque(), poste,
                    patient.premierJour(), patient.dernierJour(), patient.creneauPrefere(), patient.seancesAChoisir(),
                    patient.transporteurs(), patient.competencesRequises()));
        }
        return new DonneesDOptimisation(centre, ressources, personnel, nouveaux, profils, indisponibilites, temporaires, seances,
                preferences);
    }

    /** Sans fermetures datées, absences ni remplacements : la semaine type, pour les périmètres non datés. */
    public DonneesDOptimisation sansAleas() {
        RessourcesDuCentre sansFermeture = new RessourcesDuCentre(ressources.salles(), ressources.creneaux(), ressources.generateurs(),
                ressources.joursOuverts(), List.of());
        Personnel sansAbsence = new Personnel(personnel.infirmiers(), personnel.affectations(), List.of(), List.of(),
                personnel.ratio());
        return new DonneesDOptimisation(centre, sansFermeture, sansAbsence, patients, profils, indisponibilites, temporaires, seances,
                preferences);
    }

    /** Les places actuelles des patients qui en ont une (complète ou non). */
    public Map<PatientId, Poste> placementsActuels() {
        Map<PatientId, Poste> placements = new LinkedHashMap<>();
        for (PatientAPlacer p : patients) {
            if (p.actuelle() != null) placements.put(p.patient(), p.actuelle());
        }
        return placements;
    }

    public ProfilDePlanification profil(UUID infirmier) {
        ProfilDePlanification profil = profils.get(infirmier);
        return profil != null ? profil : ProfilDePlanification.parDefaut(new Identifiants.InfirmierId(infirmier));
    }

    public Set<JourDeLaSemaine> joursOuverts() {
        Set<JourDeLaSemaine> ouverts = ressources.joursOuverts();
        return ouverts.isEmpty() ? EnumSet.allOf(JourDeLaSemaine.class) : EnumSet.copyOf(ouverts);
    }
}
