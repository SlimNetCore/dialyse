package com.hemodialyse.backend.contexts.planification.domain;

import com.hemodialyse.backend.contexts.planification.domain.DonneesDOptimisation.PatientAPlacer;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CreneauId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.GenerateurId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.InfirmierId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.PatientId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.SalleId;
import com.hemodialyse.backend.contexts.planification.domain.Presence.AbsenceConnue;
import com.hemodialyse.backend.contexts.planification.domain.Presence.AffectationPrevue;
import com.hemodialyse.backend.contexts.planification.domain.Presence.InfirmierDeLaPresence;
import com.hemodialyse.backend.contexts.planification.domain.Presence.Personnel;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Jeu de données d'optimisation pour les tests : le centre de {@link JeuDePlaces} (salles A, B et isolement, créneaux matin et soir,
 * ouvert du lundi au samedi), des patients à placer et du personnel.
 */
public final class JeuDOptimisation {

    public static final LocalDate LUNDI = LocalDate.of(2026, 11, 2);
    public static final LocalDate DIMANCHE = LocalDate.of(2026, 11, 1);
    public static final Set<JourDeLaSemaine> LMV = EnumSet.of(JourDeLaSemaine.LUNDI, JourDeLaSemaine.MERCREDI, JourDeLaSemaine.VENDREDI);

    public final List<PatientAPlacer> patients = new ArrayList<>();
    public final List<InfirmierDeLaPresence> infirmiers = new ArrayList<>();
    public final List<AffectationPrevue> affectations = new ArrayList<>();
    public final List<AbsenceConnue> absences = new ArrayList<>();
    public final List<Presence.RemplacementPose> remplacements = new ArrayList<>();
    public final List<DeplacementTemporaire> temporaires = new ArrayList<>();
    public final List<DonneesDOptimisation.IndisponibiliteDatee> indisponibilites = new ArrayList<>();
    public final List<SemaineDuPlanning.SeanceRealisee> seances = new ArrayList<>();
    public final List<PreferenceDePlanification> preferences = new ArrayList<>();
    public RessourcesDuCentre ressources = JeuDePlaces.ressources();
    public int ratio = 4;

    public static Poste poste(SalleId salle, CreneauId creneau, GenerateurId generateur) {
        return new Poste(salle, creneau, generateur, "G");
    }

    public static InfirmierId infirmier(int n) {
        return new InfirmierId(JeuDePlaces.id(2000 + n));
    }

    public JeuDOptimisation patient(int n, Set<JourDeLaSemaine> jours, boolean aRisque, Poste actuelle) {
        patients.add(new PatientAPlacer(JeuDePlaces.patient(n), "Patient " + n, jours, aRisque, actuelle, null, null));
        return this;
    }

    public JeuDOptimisation infirmierActif(int n, boolean habiliteIsolement) {
        infirmiers.add(new InfirmierDeLaPresence(infirmier(n).valeur(), "Nom" + n, "Prenom" + n, Qualification.INFIRMIER,
                habiliteIsolement));
        return this;
    }

    public JeuDOptimisation affecte(int n, SalleId salle, CreneauId creneau, Set<JourDeLaSemaine> jours) {
        affectations.add(new AffectationPrevue(infirmier(n).valeur(), salle.valeur(), creneau.valeur(), jours));
        return this;
    }

    public JeuDOptimisation absent(int n, LocalDate debut, LocalDate fin) {
        absences.add(new AbsenceConnue(infirmier(n).valeur(), debut, fin, TypeAbsence.CONGE));
        return this;
    }

    public DonneesDOptimisation donnees() {
        Personnel personnel = new Personnel(infirmiers, affectations, absences, remplacements, new RatioDeLaPresence(ratio, false));
        return new DonneesDOptimisation(JeuDePlaces.CENTRE, ressources, personnel, patients, Map.of(), indisponibilites, temporaires,
                seances, preferences);
    }

    public static ParametresDOptimisation parametres(Perimetre perimetre) {
        return ParametresDOptimisation.parDefaut(perimetre, LUNDI);
    }

    public static UUID uuid(int n) {
        return JeuDePlaces.id(n);
    }
}
