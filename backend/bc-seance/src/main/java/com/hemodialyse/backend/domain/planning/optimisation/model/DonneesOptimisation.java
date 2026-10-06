package com.hemodialyse.backend.domain.planning.optimisation.model;

import com.hemodialyse.backend.domain.infirmier.model.Presence.DonneesPresence;
import com.hemodialyse.backend.domain.planning.model.DeplacementTemporaire;
import com.hemodialyse.backend.domain.planning.model.JourSemaine;
import com.hemodialyse.backend.domain.planning.model.Planning.DonneesPlanning;
import com.hemodialyse.backend.domain.planning.model.Planning.Occupation;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Tout ce que l'optimiseur lit d'un centre : planning (salles, créneaux, générateurs en service, isolement,
 * fermetures), patients qui occupent ou attendent une place (avec leurs préférences), personnel (infirmiers, profils,
 * roulement, absences, remplacements), indisponibilités datées des générateurs et déplacements temporaires déjà
 * décidés. Toujours borné à un centre (AGENTS.md §2) par l'adaptateur de lecture.
 *
 * @param presence         données du planning de présence, dont {@code planning().occupations()} = placements actuels
 * @param patients         patients actifs du centre, placés ou non
 * @param profils          profils de planification des infirmiers (absent = temps plein, sans compétence particulière)
 * @param indisponibilites périodes d'indisponibilité des générateurs (maintenance GMAO) sur l'horizon
 * @param temporaires      déplacements temporaires déjà enregistrés sur l'horizon
 */
public record DonneesOptimisation(DonneesPresence presence, List<PatientAPlacer> patients,
                                  Map<UUID, ProfilInfirmier> profils,
                                  List<IndisponibiliteGenerateur> indisponibilites,
                                  List<DeplacementTemporaire> temporaires) {

    public DonneesOptimisation {
        patients = patients == null ? List.of() : List.copyOf(patients);
        profils = profils == null ? Map.of() : Map.copyOf(profils);
        indisponibilites = indisponibilites == null ? List.of() : List.copyOf(indisponibilites);
        temporaires = temporaires == null ? List.of() : List.copyOf(temporaires);
    }

    public DonneesOptimisation(DonneesPresence presence, List<PatientAPlacer> patients) {
        this(presence, patients, Map.of(), List.of(), List.of());
    }

    /**
     * Patient à placer. Ses jours prescrits ne changent pas, sauf si sa préférence demande de les choisir
     * ({@code seancesAChoisir} non nul) : l'optimisation choisit alors un schéma de ce nombre de séances.
     *
     * @param actuelle            place actuelle, nulle si le patient n'est pas (complètement) placé
     * @param premierJour         date d'admission (nulle : déjà admis)
     * @param dernierJour         dernier jour d'occupation (nul : sans limite)
     * @param creneauPrefereId    créneau souhaité, facultatif
     * @param seancesAChoisir     nombre de séances hebdomadaires dont l'optimisation choisit les jours, sinon nul
     * @param transporteurs       transporteurs (aller, retour) : partagés, ils invitent à dialyser au même créneau
     * @param competencesRequises compétences qu'au moins un infirmier de sa case doit avoir
     */
    public record PatientAPlacer(UUID patientId, String nom, Set<JourSemaine> jours, boolean aRisque, Poste actuelle,
                                 LocalDate premierJour, LocalDate dernierJour, UUID creneauPrefereId,
                                 Integer seancesAChoisir, Set<UUID> transporteurs,
                                 Set<CompetenceInfirmier> competencesRequises) {
        public PatientAPlacer {
            jours = jours == null ? Set.of() : Set.copyOf(jours);
            transporteurs = transporteurs == null ? Set.of() : Set.copyOf(transporteurs);
            competencesRequises = competencesRequises == null ? Set.of() : Set.copyOf(competencesRequises);
        }

        public PatientAPlacer(UUID patientId, String nom, Set<JourSemaine> jours, boolean aRisque, Poste actuelle,
                              LocalDate premierJour, LocalDate dernierJour) {
            this(patientId, nom, jours, aRisque, actuelle, premierJour, dernierJour, null, null, Set.of(), Set.of());
        }

        public boolean joursAChoisir() {
            return seancesAChoisir != null;
        }

        public Occupation versOccupation(Poste poste) {
            return new Occupation(patientId, poste.salleId(), poste.creneauId(), poste.generateurId(), jours, aRisque,
                    dernierJour, premierJour);
        }

        public PatientAPlacer avecJours(Set<JourSemaine> nouveaux) {
            return new PatientAPlacer(patientId, nom, nouveaux, aRisque, actuelle, premierJour, dernierJour,
                    creneauPrefereId, seancesAChoisir, transporteurs, competencesRequises);
        }
    }

    public DonneesPlanning planning() {
        return presence.planning();
    }

    /**
     * Mêmes données avec d'autres placements : les patients absents de {@code placements} ne tiennent plus de place.
     */
    public DonneesOptimisation avecPlacements(Map<UUID, Poste> placements) {
        return avecPlacements(placements, Map.of());
    }

    /**
     * Mêmes données avec d'autres placements et, pour certains patients, d'autres jours de dialyse.
     */
    public DonneesOptimisation avecPlacements(Map<UUID, Poste> placements, Map<UUID, Set<JourSemaine>> joursChoisis) {
        List<PatientAPlacer> nouveaux = new ArrayList<>();
        List<Occupation> occupations = new ArrayList<>();
        for (PatientAPlacer p : patients) {
            Set<JourSemaine> jours = joursChoisis.get(p.patientId());
            PatientAPlacer patient = jours == null ? p : p.avecJours(jours);
            nouveaux.add(patient);
            Poste poste = placements.get(p.patientId());
            if (poste != null && poste.salleId() != null && poste.creneauId() != null && !patient.jours().isEmpty()) {
                occupations.add(patient.versOccupation(poste));
            }
        }
        DonneesPlanning ancien = presence.planning();
        DonneesPlanning planning = new DonneesPlanning(ancien.salles(), ancien.creneaux(), ancien.generateurs(),
                occupations, ancien.joursOuverts(), ancien.sallesIsolement(), ancien.fermetures());
        return new DonneesOptimisation(new DonneesPresence(planning, presence.patientsParInfirmier(),
                presence.infirmiers(), presence.affectations(), presence.absences(), presence.remplacements()), nouveaux,
                profils, indisponibilites, temporaires);
    }

    /**
     * Semaine type : sans fermeture datée, absence ni remplacement, pour concevoir un roulement indépendant des aléas.
     */
    public DonneesOptimisation sansAleas() {
        DonneesPlanning ancien = presence.planning();
        DonneesPlanning planning = new DonneesPlanning(ancien.salles(), ancien.creneaux(), ancien.generateurs(),
                ancien.occupations(), ancien.joursOuverts(), ancien.sallesIsolement(), List.of());
        return new DonneesOptimisation(new DonneesPresence(planning, presence.patientsParInfirmier(),
                presence.infirmiers(), presence.affectations(), List.of(), List.of()), patients, profils,
                indisponibilites, temporaires);
    }

    /**
     * Placements actuels des patients déjà placés.
     */
    public Map<UUID, Poste> placementsActuels() {
        LinkedHashMap<UUID, Poste> placements = new LinkedHashMap<>();
        for (PatientAPlacer p : patients) {
            if (p.actuelle() != null) placements.put(p.patientId(), p.actuelle());
        }
        return placements;
    }

    public ProfilInfirmier profil(UUID infirmierId) {
        return profils.getOrDefault(infirmierId, ProfilInfirmier.parDefaut(infirmierId));
    }

    /**
     * Jours d'ouverture hebdomadaires du centre (tous si aucun n'est paramétré).
     */
    public Set<JourSemaine> joursOuverts() {
        Set<JourSemaine> ouverts = planning().joursOuverts();
        return ouverts == null || ouverts.isEmpty() ? EnumSet.allOf(JourSemaine.class) : EnumSet.copyOf(ouverts);
    }
}
