package com.hemodialyse.backend.infrastructure.optimisation.timefold;

import ai.timefold.solver.core.api.score.stream.ConstraintProvider;
import ai.timefold.solver.core.api.solver.Solver;
import ai.timefold.solver.core.api.solver.SolverFactory;
import ai.timefold.solver.core.config.constructionheuristic.ConstructionHeuristicPhaseConfig;
import ai.timefold.solver.core.config.constructionheuristic.placer.QueuedEntityPlacerConfig;
import ai.timefold.solver.core.api.domain.variable.PlanningVariable;
import ai.timefold.solver.core.config.heuristic.selector.common.SelectionCacheType;
import ai.timefold.solver.core.config.heuristic.selector.entity.EntitySelectorConfig;
import ai.timefold.solver.core.config.heuristic.selector.move.MoveSelectorConfig;
import ai.timefold.solver.core.config.heuristic.selector.move.composite.CartesianProductMoveSelectorConfig;
import ai.timefold.solver.core.config.heuristic.selector.move.generic.ChangeMoveSelectorConfig;
import ai.timefold.solver.core.config.heuristic.selector.value.ValueSelectorConfig;
import ai.timefold.solver.core.config.phase.PhaseConfig;
import ai.timefold.solver.core.config.localsearch.LocalSearchPhaseConfig;
import ai.timefold.solver.core.config.localsearch.decider.acceptor.LocalSearchAcceptorConfig;
import ai.timefold.solver.core.config.localsearch.decider.forager.LocalSearchForagerConfig;
import ai.timefold.solver.core.config.solver.EnvironmentMode;
import ai.timefold.solver.core.config.solver.SolverConfig;
import ai.timefold.solver.core.config.solver.termination.TerminationConfig;
import com.hemodialyse.backend.domain.planning.model.JourSemaine;
import com.hemodialyse.backend.domain.planning.optimisation.model.DonneesOptimisation;
import com.hemodialyse.backend.domain.planning.optimisation.model.ParametresOptimisation;
import com.hemodialyse.backend.domain.planning.optimisation.model.PerimetreOptimisation;
import com.hemodialyse.backend.domain.planning.optimisation.model.Poste;
import com.hemodialyse.backend.domain.planning.optimisation.model.ResultatOptimisation;
import com.hemodialyse.backend.domain.planning.optimisation.model.ResultatOptimisation.DeplacementPatient;
import com.hemodialyse.backend.domain.planning.optimisation.model.ResultatOptimisation.PatientNonPlace;
import com.hemodialyse.backend.domain.planning.optimisation.model.ResultatOptimisation.VacationNonPourvue;
import com.hemodialyse.backend.domain.planning.optimisation.model.ResultatOptimisation.VacationPlanifiee;
import com.hemodialyse.backend.domain.planning.optimisation.port.OptimiseurPlanningPort;
import com.hemodialyse.backend.domain.planning.optimisation.service.IndicateursOptimisationService;
import com.hemodialyse.backend.infrastructure.optimisation.timefold.complet.CompletConstraintProvider;
import com.hemodialyse.backend.infrastructure.optimisation.timefold.complet.CompletMapper;
import com.hemodialyse.backend.infrastructure.optimisation.timefold.complet.PlanComplet;
import com.hemodialyse.backend.infrastructure.optimisation.timefold.infirmiers.InfirmiersConstraintProvider;
import com.hemodialyse.backend.infrastructure.optimisation.timefold.infirmiers.InfirmiersMapper;
import com.hemodialyse.backend.infrastructure.optimisation.timefold.infirmiers.PlanInfirmiers;
import com.hemodialyse.backend.infrastructure.optimisation.timefold.infirmiers.Vacation;
import com.hemodialyse.backend.infrastructure.optimisation.timefold.maintenance.MaintenanceConstraintProvider;
import com.hemodialyse.backend.infrastructure.optimisation.timefold.maintenance.MaintenanceMapper;
import com.hemodialyse.backend.infrastructure.optimisation.timefold.maintenance.PlanMaintenance;
import com.hemodialyse.backend.infrastructure.optimisation.timefold.maintenance.SeanceTemporaire;
import com.hemodialyse.backend.infrastructure.optimisation.timefold.patients.PatientsConstraintProvider;
import com.hemodialyse.backend.infrastructure.optimisation.timefold.patients.PatientsMapper;
import com.hemodialyse.backend.infrastructure.optimisation.timefold.patients.PlacementPatient;
import com.hemodialyse.backend.infrastructure.optimisation.timefold.patients.PlanPatients;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Adaptateur du port {@link OptimiseurPlanningPort} fondé sur Timefold Solver. Selon le périmètre :
 * <ul>
 *   <li>patients, roulement, couverture : une phase de placement des patients et / ou une phase de planification des
 *   infirmiers ;</li>
 *   <li>complet : un seul modèle conjoint, patients et vacations ensemble ;</li>
 *   <li>maintenance : déplacements temporaires des séances dont le générateur est indisponible.</li>
 * </ul>
 * Chaque phase dispose de la durée demandée ; après un arrêt, les phases restantes ne reçoivent qu'un court délai pour
 * livrer une proposition complète.
 * <p>
 * Le moteur ne touche ni à la base ni au contexte de sécurité : il reçoit des données, renvoie une proposition. La
 * graine aléatoire est fixe et le mode d'environnement reproductible sans vérifications coûteuses
 * ({@code NO_ASSERT}) ; la limite de calcul étant un temps, deux exécutions peuvent malgré tout différer légèrement.
 */
@Component
public class TimefoldOptimiseurAdapter implements OptimiseurPlanningPort, DisposableBean {

    static final String PHASE_PATIENTS = "PATIENTS";
    static final String PHASE_INFIRMIERS = "INFIRMIERS";
    static final String PHASE_COMPLET = "COMPLET";
    static final String PHASE_MAINTENANCE = "MAINTENANCE";
    private static final Logger log = LoggerFactory.getLogger(TimefoldOptimiseurAdapter.class);
    private static final long GRAINE = 20260926L;
    private static final String TEMPERATURE_INITIALE = "0hard/0medium/100soft";
    private static final Duration DUREE_APRES_ARRET = Duration.ofSeconds(2);
    private static final Duration SANS_AMELIORATION_MIN = Duration.ofSeconds(2);
    private static final int DIVISEUR_SANS_AMELIORATION = 3;

    private final ExecutorService executor;
    private final Map<UUID, Execution> actives = new ConcurrentHashMap<>();

    public TimefoldOptimiseurAdapter(@Value("${app.planning.optimisation.workers:2}") int workers) {
        AtomicInteger numero = new AtomicInteger();
        this.executor = Executors.newFixedThreadPool(Math.max(1, workers), r -> {
            Thread t = new Thread(r, "optimisation-planning-" + numero.incrementAndGet());
            t.setDaemon(true);
            return t;
        });
    }

    @Override
    public void demarrer(UUID runId, DonneesOptimisation donnees, ParametresOptimisation parametres, Ecouteur ecouteur) {
        Execution execution = new Execution();
        actives.put(runId, execution);
        try {
            executor.execute(() -> {
                try {
                    ResultatEtScore resultat = executer(donnees, parametres, execution, ecouteur);
                    ecouteur.termine(resultat.resultat(), resultat.score());
                } catch (RuntimeException | StackOverflowError e) {
                    log.warn("[OPTIMISATION] Échec de l'exécution {}", runId, e);
                    ecouteur.echec(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
                } finally {
                    actives.remove(runId);
                }
            });
        } catch (RejectedExecutionException e) {
            actives.remove(runId);
            ecouteur.echec("Moteur d'optimisation indisponible");
        }
    }

    @Override
    public boolean arreter(UUID runId) {
        Execution execution = actives.get(runId);
        if (execution == null) return false;
        execution.arreter();
        return true;
    }

    @Override
    public void destroy() {
        actives.values().forEach(Execution::arreter);
        executor.shutdownNow();
    }

    private record ResultatEtScore(ResultatOptimisation resultat, String score) {
    }

    private ResultatEtScore executer(DonneesOptimisation donnees, ParametresOptimisation parametres, Execution execution,
                                     Ecouteur ecouteur) {
        if (parametres.perimetre() == PerimetreOptimisation.MAINTENANCE) {
            return maintenance(donnees, parametres, execution, ecouteur);
        }
        if (parametres.perimetre().conjoint()) return complet(donnees, parametres, execution, ecouteur);

        List<String> scores = new ArrayList<>();
        Map<UUID, Poste> placements = donnees.placementsActuels();
        Map<UUID, Set<JourSemaine>> joursChoisis = Map.of();
        List<DeplacementPatient> deplacements = List.of();
        List<PatientNonPlace> nonPlaces = List.of();
        List<VacationPlanifiee> vacations = null;
        List<VacationNonPourvue> manques = null;

        if (parametres.perimetre().placePatients()) {
            PlanPatients probleme = PatientsMapper.versProbleme(donnees, parametres);
            PlanPatients solution = resoudre(PlanPatients.class, PatientsConstraintProvider.class, probleme,
                    probleme.getPatients().isEmpty(), parametres, execution, PHASE_PATIENTS, ecouteur,
                    PlacementPatient.class);
            scores.add(PHASE_PATIENTS + " " + solution.getScore());
            PatientsMapper.Resultat patients = PatientsMapper.versResultat(solution, donnees);
            deplacements = patients.deplacements();
            nonPlaces = patients.nonPlaces();
            placements = patients.placements();
            joursChoisis = patients.joursChoisis();
        }

        if (parametres.perimetre().planifieInfirmiers()) {
            DonneesOptimisation avecPlacements = IndicateursOptimisationService
                    .reference(donnees, parametres).avecPlacements(placements, joursChoisis);
            PlanInfirmiers probleme = InfirmiersMapper.versProbleme(avecPlacements, parametres);
            PlanInfirmiers solution = resoudre(PlanInfirmiers.class, InfirmiersConstraintProvider.class, probleme,
                    probleme.getVacations().isEmpty(), parametres, execution, PHASE_INFIRMIERS, ecouteur,
                    Vacation.class);
            scores.add(PHASE_INFIRMIERS + " " + solution.getScore());
            InfirmiersMapper.Resultat infirmiers = InfirmiersMapper.versResultat(solution, avecPlacements);
            vacations = infirmiers.vacations();
            manques = infirmiers.manques();
        }

        ResultatOptimisation resultat = new ResultatOptimisation(donnees.planning().salles(),
                donnees.planning().creneaux(), deplacements, nonPlaces,
                vacations == null ? List.of() : vacations, manques == null ? List.of() : manques,
                IndicateursOptimisationService.avant(donnees, parametres),
                IndicateursOptimisationService.apres(donnees, placements, joursChoisis, parametres, vacations, manques));
        return new ResultatEtScore(resultat, String.join(" ; ", scores));
    }

    /**
     * Patients et infirmiers dans un seul modèle : le besoin en personnel suit les placements pendant le calcul.
     */
    private ResultatEtScore complet(DonneesOptimisation donnees, ParametresOptimisation parametres,
                                    Execution execution, Ecouteur ecouteur) {
        PlanComplet probleme = CompletMapper.versProbleme(donnees, parametres);
        PlanComplet solution = resoudre(PlanComplet.class, CompletConstraintProvider.class, probleme,
                probleme.getPatients().isEmpty() && probleme.getVacations().isEmpty(), parametres, execution,
                PHASE_COMPLET, ecouteur, PlacementPatient.class, Vacation.class);
        CompletMapper.Resultat r = CompletMapper.versResultat(solution, donnees, parametres);
        PatientsMapper.Resultat patients = r.patients();
        ResultatOptimisation resultat = new ResultatOptimisation(donnees.planning().salles(),
                donnees.planning().creneaux(), patients.deplacements(), patients.nonPlaces(), r.vacations(),
                r.manques(), IndicateursOptimisationService.avant(donnees, parametres),
                IndicateursOptimisationService.apres(donnees, patients.placements(), patients.joursChoisis(),
                        parametres, r.vacations(), r.manques()));
        return new ResultatEtScore(resultat, PHASE_COMPLET + " " + solution.getScore());
    }

    /**
     * Déplacements temporaires des séances dont le générateur est indisponible : les places habituelles ne changent
     * pas, les indicateurs non plus.
     */
    private ResultatEtScore maintenance(DonneesOptimisation donnees, ParametresOptimisation parametres,
                                        Execution execution, Ecouteur ecouteur) {
        PlanMaintenance probleme = MaintenanceMapper.versProbleme(donnees, parametres);
        PlanMaintenance solution = resoudre(PlanMaintenance.class, MaintenanceConstraintProvider.class, probleme,
                probleme.getSeances().isEmpty(), parametres, execution, PHASE_MAINTENANCE, ecouteur,
                SeanceTemporaire.class);
        MaintenanceMapper.Resultat r = MaintenanceMapper.versResultat(solution, donnees);
        var indicateurs = IndicateursOptimisationService.avant(donnees, parametres);
        ResultatOptimisation resultat = new ResultatOptimisation(donnees.planning().salles(),
                donnees.planning().creneaux(), List.of(), List.of(), List.of(), List.of(), indicateurs, indicateurs,
                r.deplacements(), r.sansSolution());
        return new ResultatEtScore(resultat, PHASE_MAINTENANCE + " " + solution.getScore());
    }

    private <S> S resoudre(Class<S> solutionClass, Class<? extends ConstraintProvider> provider, S probleme,
                           boolean vide, ParametresOptimisation parametres, Execution execution, String phase,
                           Ecouteur ecouteur, Class<?>... entites) {
        SolverConfig config = new SolverConfig()
                .withSolutionClass(solutionClass)
                .withEntityClasses(entites)
                .withConstraintProviderClass(provider)
                .withEnvironmentMode(EnvironmentMode.NO_ASSERT)
                .withRandomSeed(GRAINE)
                .withPhases(phases(entites))
                .withTerminationConfig(terminaison(execution.arretDemande()
                        ? DUREE_APRES_ARRET : Duration.ofSeconds(parametres.dureeMaxSecondes())));
        SolverFactory<S> usine = SolverFactory.create(config);
        Solver<S> solver = usine.buildSolver();
        solver.addEventListener(event -> ecouteur.progression(phase, String.valueOf(event.getNewBestScore())));
        execution.suivre(solver);
        return vide ? probleme : solver.solve(probleme);
    }

    /**
     * Une heuristique de construction par classe d'entités (dans l'ordre donné : les patients avant les vacations du
     * modèle conjoint, pour que le besoin en personnel soit connu), puis le recuit simulé.
     */
    private static PhaseConfig<?>[] phases(Class<?>... entites) {
        List<PhaseConfig<?>> phases = new ArrayList<>();
        for (Class<?> entite : entites) {
            String selecteur = "placeur-" + entite.getSimpleName();
            List<MoveSelectorConfig> changements = new ArrayList<>();
            for (String variable : variables(entite)) {
                changements.add(new ChangeMoveSelectorConfig()
                        .withEntitySelectorConfig(EntitySelectorConfig.newMimicSelectorConfig(selecteur))
                        .withValueSelectorConfig(new ValueSelectorConfig(variable)));
            }
            phases.add(new ConstructionHeuristicPhaseConfig().withEntityPlacerConfig(new QueuedEntityPlacerConfig()
                    .withEntitySelectorConfig(new EntitySelectorConfig().withId(selecteur).withEntityClass(entite)
                            .withCacheType(SelectionCacheType.PHASE))
                    .withMoveSelectorConfigList(changements.size() == 1 ? changements
                            : List.of(new CartesianProductMoveSelectorConfig(changements)))));
        }
        phases.add(recuitSimule());
        return phases.toArray(PhaseConfig<?>[]::new);
    }

    /**
     * Variables de planification déclarées par une classe d'entités (nom du champ annoté).
     */
    private static List<String> variables(Class<?> entite) {
        List<String> noms = new ArrayList<>();
        for (Field champ : entite.getDeclaredFields()) {
            if (champ.isAnnotationPresent(PlanningVariable.class)) noms.add(champ.getName());
        }
        return noms;
    }

    /**
     * Recuit simulé : l'acceptation tardive reste bloquée sur les « vallées » du planning (déplacer un premier patient
     * ne rapporte rien tant que le second n'a pas suivi). Température de départ : l'ordre de grandeur des poids
     * souples ; jamais de dégradation d'un niveau dur ou moyen.
     */
    private static LocalSearchPhaseConfig recuitSimule() {
        return new LocalSearchPhaseConfig()
                .withAcceptorConfig(new LocalSearchAcceptorConfig()
                        .withSimulatedAnnealingStartingTemperature(TEMPERATURE_INITIALE))
                .withForagerConfig(new LocalSearchForagerConfig().withAcceptedCountLimit(1));
    }

    static TerminationConfig terminaison(Duration duree) {
        Duration sansAmelioration = Duration.ofSeconds(
                duree.dividedBy(DIVISEUR_SANS_AMELIORATION).toSeconds());
        if (sansAmelioration.compareTo(SANS_AMELIORATION_MIN) < 0) sansAmelioration = SANS_AMELIORATION_MIN;
        return new TerminationConfig().withSpentLimit(duree).withUnimprovedSpentLimit(sansAmelioration);
    }

    /**
     * Calcul en cours : drapeau d'arrêt et solveur de la phase courante.
     */
    private static final class Execution {
        private volatile boolean arret;
        private volatile Solver<?> courant;

        void arreter() {
            arret = true;
            Solver<?> solver = courant;
            if (solver != null) solver.terminateEarly();
        }

        boolean arretDemande() {
            return arret;
        }

        void suivre(Solver<?> solver) {
            courant = solver;
            if (arret) solver.terminateEarly();
        }
    }
}
