package com.hemodialyse.backend.contexts.planification.doubles;

import com.hemodialyse.backend.contexts.planification.application.CreneauxDuCentre;
import com.hemodialyse.backend.contexts.planification.application.InfirmiersDuCentre;
import com.hemodialyse.backend.contexts.planification.application.PatientsDuCentre;
import com.hemodialyse.backend.contexts.planification.application.SallesDuCentre;
import com.hemodialyse.backend.contexts.planification.application.UniteDeTravail;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CentreId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CreneauId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.InfirmierId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.PatientId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.SalleId;
import com.hemodialyse.backend.contexts.planification.domain.ParametresDuPlanning;
import com.hemodialyse.backend.contexts.planification.domain.ParametresDuPlanningRepository;
import com.hemodialyse.backend.contexts.planification.domain.PreferenceDePlanification;
import com.hemodialyse.backend.contexts.planification.domain.PreferencesRepository;
import com.hemodialyse.backend.contexts.planification.domain.ProfilDePlanification;
import com.hemodialyse.backend.contexts.planification.domain.ProfilsRepository;
import com.hemodialyse.backend.contexts.planification.domain.ReglagesDePlanification;
import com.hemodialyse.backend.contexts.planification.domain.ReglagesRepository;
import com.hemodialyse.backend.contexts.planification.domain.VerrouDuCentre;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Doubles en mémoire du paramétrage : un seul « monde » partagé par les ports (organisation, patients, infirmiers) et les
 * dépôts. Chaque double passe le même contrat que l'adaptateur JDBC.
 */
public final class ParametrageEnMemoire {

    public final SallesEnMemoire salles = new SallesEnMemoire();
    public final Map<CentreId, Set<CreneauId>> creneaux = new HashMap<>();
    public final Map<PatientId, PatientsDuCentre.Situation> patientsDuCentre = new HashMap<>();
    public final Map<InfirmierId, InfirmiersDuCentre.Situation> infirmiersDuCentre = new HashMap<>();
    public final List<CentreId> verrous = new ArrayList<>();
    public int transactions;

    public final ParametresDuPlanningRepository parametres = new ParametresDuPlanningRepository() {
        private final Map<CentreId, ParametresDuPlanning> lignes = new HashMap<>();

        @Override
        public ParametresDuPlanning lire(CentreId centre) {
            return lignes.getOrDefault(centre, ParametresDuPlanning.parDefaut()).avecLesSallesDIsolement(salles.sallesDIsolement(centre));
        }

        @Override
        public void enregistrer(CentreId centre, ParametresDuPlanning p) {
            lignes.put(centre, p);
            salles.marquerIsolement(centre, p.sallesIsolement());
        }
    };

    public final ReglagesRepository reglages = new ReglagesRepository() {
        private final Map<CentreId, ReglagesDePlanification> lignes = new HashMap<>();

        @Override
        public ReglagesDePlanification lire(CentreId centre) {
            return lignes.getOrDefault(centre, ReglagesDePlanification.parDefaut());
        }

        @Override
        public void enregistrer(CentreId centre, ReglagesDePlanification r) {
            lignes.put(centre, r);
        }

        @Override
        public List<CentreId> centresEnReplanificationNocturne() {
            return lignes.entrySet().stream().filter(e -> e.getValue().replanificationNocturne()).map(Map.Entry::getKey)
                    .sorted(Comparator.comparing(c -> c.valeur())).toList();
        }
    };

    public final PreferencesRepository preferences = new PreferencesRepository() {
        private final Map<CentreId, Map<PatientId, PreferenceDePlanification>> lignes = new HashMap<>();

        @Override
        public Map<PatientId, PreferenceDePlanification> duCentre(CentreId centre) {
            return new HashMap<>(lignes.getOrDefault(centre, Map.of()));
        }

        @Override
        public void enregistrer(CentreId centre, PreferenceDePlanification p) {
            lignes.computeIfAbsent(centre, c -> new HashMap<>()).put(p.patient(), p);
        }
    };

    public final ProfilsRepository profils = new ProfilsRepository() {
        private final Map<CentreId, Map<InfirmierId, ProfilDePlanification>> lignes = new HashMap<>();

        @Override
        public Map<InfirmierId, ProfilDePlanification> duCentre(CentreId centre) {
            return new HashMap<>(lignes.getOrDefault(centre, Map.of()));
        }

        @Override
        public void enregistrer(CentreId centre, ProfilDePlanification p) {
            lignes.computeIfAbsent(centre, c -> new HashMap<>()).put(p.infirmier(), p);
        }
    };

    public final VerrouDuCentre verrou = verrous::add;

    public final UniteDeTravail unite = new UniteDeTravail() {
        @Override
        public <T> T executer(Supplier<T> action) {
            transactions++;
            return action.get();
        }
    };

    public final CreneauxDuCentre creneauxPort = (centre, creneau) -> creneaux.getOrDefault(centre, Set.of()).contains(creneau);

    public final PatientsDuCentre patients = (centre, patient) ->
            patientsDuCentre.getOrDefault(patient, PatientsDuCentre.Situation.INTROUVABLE);

    public final InfirmiersDuCentre infirmiers = (centre, infirmier) ->
            infirmiersDuCentre.getOrDefault(infirmier, InfirmiersDuCentre.Situation.INTROUVABLE);

    /** Salles d'Organisation et leur marque d'isolement. */
    public static final class SallesEnMemoire implements SallesDuCentre {
        private final Map<CentreId, Set<SalleId>> toutes = new HashMap<>();
        private final Map<CentreId, Set<SalleId>> isolement = new HashMap<>();

        public void ajouter(CentreId centre, SalleId salle, boolean estIsolement) {
            toutes.computeIfAbsent(centre, c -> new HashSet<>()).add(salle);
            if (estIsolement) isolement.computeIfAbsent(centre, c -> new HashSet<>()).add(salle);
        }

        @Override
        public Set<SalleId> toutes(CentreId centre) {
            return new HashSet<>(toutes.getOrDefault(centre, Set.of()));
        }

        @Override
        public Set<SalleId> sallesDIsolement(CentreId centre) {
            return new HashSet<>(isolement.getOrDefault(centre, Set.of()));
        }

        @Override
        public void marquerIsolement(CentreId centre, Set<SalleId> sallesDIsolement) {
            isolement.put(centre, new HashSet<>(sallesDIsolement));
        }
    }
}
