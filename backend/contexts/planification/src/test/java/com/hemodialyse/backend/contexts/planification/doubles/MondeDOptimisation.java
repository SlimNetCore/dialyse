package com.hemodialyse.backend.contexts.planification.doubles;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import com.hemodialyse.backend.contexts.planification.application.AppliquerUneProposition;
import com.hemodialyse.backend.contexts.planification.application.ArreterUneOptimisation;
import com.hemodialyse.backend.contexts.planification.application.CentresActifs;
import com.hemodialyse.backend.contexts.planification.application.ConsulterLesOptimisations;
import com.hemodialyse.backend.contexts.planification.application.LancerUneOptimisation;
import com.hemodialyse.backend.contexts.planification.application.PersonnelDeLaPresence;
import com.hemodialyse.backend.contexts.planification.application.ReplanifierLaNuit;
import com.hemodialyse.backend.contexts.planification.application.ReprendreLesExecutionsInterrompues;
import com.hemodialyse.backend.contexts.planification.application.SupprimerUneExecution;
import com.hemodialyse.backend.contexts.planification.application.VerrouillerLesFichesDesInfirmiers;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants;
import com.hemodialyse.backend.contexts.planification.domain.Remplacement;
import com.hemodialyse.backend.contexts.planification.domain.RemplacementsRepository;
import com.hemodialyse.backend.contexts.planification.domain.Presence.Personnel;
import com.hemodialyse.backend.contexts.planification.domain.RatioDeLaPresence;
import com.hemodialyse.backend.contexts.planification.domain.RessourcesDuCentre;
import com.hemodialyse.backend.contexts.planification.domain.JeuDePlaces;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Le « monde » des tests d'application de l'optimisation : les places (P5), le roulement et les remplacements (P3) et l'optimisation
 * (P8) en mémoire, câblés sur les mêmes ports, avec un {@link com.hemodialyse.backend.contexts.planification.application.Optimiseur}
 * FAUX que le test pilote lui-même. Une horloge fixe donne l'instant des transitions.
 */
public final class MondeDOptimisation {

    public static final CentreId CENTRE = JeuDePlaces.CENTRE;
    public static final Instant MAINTENANT = Instant.parse("2026-11-02T09:00:00Z");

    public final PlacesEnMemoire places = new PlacesEnMemoire(JeuDePlaces.ressources());
    public final PlanningDesInfirmiersEnMemoire planning = new PlanningDesInfirmiersEnMemoire();
    public final OptimisationEnMemoire optimisation = new OptimisationEnMemoire(places);
    public final ParametrageEnMemoire parametrage = new ParametrageEnMemoire();
    public final List<CentreId> centresActifs = new ArrayList<>(List.of(CENTRE));
    public Personnel personnelDeLaPresence = new Personnel(List.of(), List.of(), List.of(), List.of(), new RatioDeLaPresence(4, false));
    public RuntimeException lecturePersonnelEnEchec;
    public Instant maintenant = MAINTENANT;
    public final Clock horloge = new Clock() {
        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return maintenant;
        }
    };

    public final VerrouillerLesFichesDesInfirmiers verrouDesFiches =
            new VerrouillerLesFichesDesInfirmiers(optimisation, planning.monde.infirmiers);

    public final LancerUneOptimisation lancer = new LancerUneOptimisation(optimisation, places, parametrage.reglages,
            optimisation.executionsRepository, optimisation.donnees, optimisation.calendriers, optimisation.optimiseur,
            optimisation.identifiants, horloge);

    public final ConsulterLesOptimisations consulter =
            new ConsulterLesOptimisations(optimisation.executionsRepository, optimisation.calendriers);

    public final ArreterUneOptimisation arreter =
            new ArreterUneOptimisation(optimisation.executionsRepository, optimisation.optimiseur);

    public final SupprimerUneExecution supprimer =
            new SupprimerUneExecution(optimisation, places, optimisation.executionsRepository, optimisation.calendriers);

    public final ReprendreLesExecutionsInterrompues reprendre =
            new ReprendreLesExecutionsInterrompues(optimisation, optimisation.executionsRepository, horloge);

    public final List<String> caches = new ArrayList<>();

    /** Les remplacements enregistrés par l'application d'une proposition, dans l'ordre. */
    public final List<Remplacement> remplacementsPoses = new ArrayList<>();

    private final RemplacementsRepository remplacementsObserves = new RemplacementsRepository() {
        @Override
        public void enregistrer(Remplacement remplacement) {
            planning.remplacements.enregistrer(remplacement);
            remplacementsPoses.add(remplacement);
        }

        @Override
        public java.util.Optional<Remplacement> parId(CentreId centre, Identifiants.RemplacementId id) {
            return planning.remplacements.parId(centre, id);
        }

        @Override
        public boolean remplaceDeja(CentreId centre, Identifiants.InfirmierId infirmier, LocalDate jour, Identifiants.CreneauId creneau) {
            return planning.remplacements.remplaceDeja(centre, infirmier, jour, creneau);
        }

        @Override
        public void annuler(CentreId centre, Identifiants.RemplacementId id) {
            planning.remplacements.annuler(centre, id);
        }
    };

    public final AppliquerUneProposition appliquer = new AppliquerUneProposition(optimisation, places, verrouDesFiches,
            optimisation.executionsRepository, optimisation.donnees, places, places, planning.roulements, remplacementsObserves,
            optimisation.deplacements, optimisation.identifiants, optimisation.notificationsDeLOptimisation,
            (centre, patient) -> caches.add(centre + "|" + patient), horloge);

    public final CentresActifs actifs = () -> List.copyOf(centresActifs);

    public final PersonnelDeLaPresence personnel = (centre, du, au) -> {
        if (lecturePersonnelEnEchec != null) throw lecturePersonnelEnEchec;
        return personnelDeLaPresence;
    };

    public final ReplanifierLaNuit nuit = new ReplanifierLaNuit(parametrage.reglages, places, lancer, personnel,
            optimisation.notificationsDeLOptimisation);

    public MondeDOptimisation() {
        places.ressources = JeuDePlaces.ressources();
    }

    public MondeDOptimisation avecLesRessources(RessourcesDuCentre ressources) {
        places.ressources = ressources;
        return this;
    }

    public static UUID uuid(int n) {
        return JeuDePlaces.id(n);
    }

    public static LocalDate lundi() {
        return LocalDate.of(2026, 11, 2);
    }
}
