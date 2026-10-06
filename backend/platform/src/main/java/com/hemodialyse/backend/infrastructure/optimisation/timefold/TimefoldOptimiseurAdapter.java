package com.hemodialyse.backend.infrastructure.optimisation.timefold;

import ai.timefold.solver.core.api.score.stream.ConstraintProvider;
import ai.timefold.solver.core.api.solver.Solver;
import ai.timefold.solver.core.api.solver.SolverFactory;
import ai.timefold.solver.core.config.constructionheuristic.ConstructionHeuristicPhaseConfig;
import ai.timefold.solver.core.config.localsearch.LocalSearchPhaseConfig;
import ai.timefold.solver.core.config.localsearch.decider.acceptor.LocalSearchAcceptorConfig;
import ai.timefold.solver.core.config.localsearch.decider.forager.LocalSearchForagerConfig;
import ai.timefold.solver.core.config.solver.EnvironmentMode;
import ai.timefold.solver.core.config.solver.SolverConfig;
import ai.timefold.solver.core.config.solver.termination.TerminationConfig;
import com.hemodialyse.backend.domain.planning.optimisation.model.DonneesOptimisation;
import com.hemodialyse.backend.domain.planning.optimisation.model.ParametresOptimisation;
import com.hemodialyse.backend.domain.planning.optimisation.model.Poste;
import com.hemodialyse.backend.domain.planning.optimisation.model.ResultatOptimisation;
import com.hemodialyse.backend.domain.planning.optimisation.model.ResultatOptimisation.DeplacementPatient;
import com.hemodialyse.backend.domain.planning.optimisation.model.ResultatOptimisation.PatientNonPlace;
import com.hemodialyse.backend.domain.planning.optimisation.model.ResultatOptimisation.VacationNonPourvue;
import com.hemodialyse.backend.domain.planning.optimisation.model.ResultatOptimisation.VacationPlanifiee;
import com.hemodialyse.backend.domain.planning.optimisation.port.OptimiseurPlanningPort;
import com.hemodialyse.backend.domain.planning.optimisation.service.IndicateursOptimisationService;
import com.hemodialyse.backend.infrastructure.optimisation.timefold.infirmiers.InfirmiersConstraintProvider;
import com.hemodialyse.backend.infrastructure.optimisation.timefold.infirmiers.InfirmiersMapper;
import com.hemodialyse.backend.infrastructure.optimisation.timefold.infirmiers.PlanInfirmiers;
import com.hemodialyse.backend.infrastructure.optimisation.timefold.infirmiers.Vacation;
import com.hemodialyse.backend.infrastructure.optimisation.timefold.patients.PatientsConstraintProvider;
import com.hemodialyse.backend.infrastructure.optimisation.timefold.patients.PatientsMapper;
import com.hemodialyse.backend.infrastructure.optimisation.timefold.patients.PlacementPatient;
import com.hemodialyse.backend.infrastructure.optimisation.timefold.patients.PlanPatients;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Adaptateur du port {@link OptimiseurPlanningPort} fondé sur Timefold Solver. Le calcul se fait en deux phases
 * indépendantes : placement des patients puis planification des infirmiers (sur les nouveaux placements pour le
 * périmètre complet). Chaque phase dispose de la durée demandée ; après un arrêt, les phases restantes ne reçoivent
 * qu'un court délai pour livrer une proposition complète.
 * <p>
 * Le moteur ne touche ni à la base ni au contexte de sécurité : il reçoit des données, renvoie une proposition. La
 * graine aléatoire est fixe et le mode d'environnement reproductible sans vérifications coûteuses
 * ({@code NO_ASSERT}) ; la limite de calcul étant un temps, deux exécutions peuvent malgré tout différer légèrement.
 */
@Component
public class TimefoldOptimiseurAdapter implements OptimiseurPlanningPort, DisposableBean {

    static final String PHASE_PATIENTS = "PATIENTS";
    static final String PHASE_INFIRMIERS = "INFIRMIERS";
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
        List<String> scores = new ArrayList<>();
        Map<UUID, Poste> placements = donnees.placementsActuels();
        List<DeplacementPatient> deplacements = List.of();
        List<PatientNonPlace> nonPlaces = List.of();
        List<VacationPlanifiee> vacations = null;
        List<VacationNonPourvue> manques = null;

        if (parametres.perimetre().placePatients()) {
            PlanPatients probleme = PatientsMapper.versProbleme(donnees, parametres);
            PlanPatients solution = resoudre(PlanPatients.class, PlacementPatient.class, PatientsConstraintProvider.class,
                    probleme, probleme.getPatients().isEmpty(), parametres, execution, PHASE_PATIENTS, ecouteur);
            scores.add(PHASE_PATIENTS + " " + solution.getScore());
            PatientsMapper.Resultat patients = PatientsMapper.versResultat(solution, donnees);
            deplacements = patients.deplacements();
            nonPlaces = patients.nonPlaces();
            placements = patients.placements();
        }

        if (parametres.perimetre().planifieInfirmiers()) {
            DonneesOptimisation avecPlacements = IndicateursOptimisationService
                    .reference(donnees, parametres).avecPlacements(placements);
            PlanInfirmiers probleme = InfirmiersMapper.versProbleme(avecPlacements, parametres);
            PlanInfirmiers solution = resoudre(PlanInfirmiers.class, Vacation.class, InfirmiersConstraintProvider.class,
                    probleme, probleme.getVacations().isEmpty(), parametres, execution, PHASE_INFIRMIERS, ecouteur);
            scores.add(PHASE_INFIRMIERS + " " + solution.getScore());
            InfirmiersMapper.Resultat infirmiers = InfirmiersMapper.versResultat(solution, avecPlacements);
            vacations = infirmiers.vacations();
            manques = infirmiers.manques();
        }

        ResultatOptimisation resultat = new ResultatOptimisation(donnees.planning().salles(),
                donnees.planning().creneaux(), deplacements, nonPlaces,
                vacations == null ? List.of() : vacations, manques == null ? List.of() : manques,
                IndicateursOptimisationService.avant(donnees, parametres),
                IndicateursOptimisationService.apres(donnees, placements, parametres, vacations, manques));
        return new ResultatEtScore(resultat, String.join(" ; ", scores));
    }

    private <S> S resoudre(Class<S> solutionClass, Class<?> entiteClass, Class<? extends ConstraintProvider> provider,
                           S probleme, boolean vide, ParametresOptimisation parametres, Execution execution,
                           String phase, Ecouteur ecouteur) {
        SolverConfig config = new SolverConfig()
                .withSolutionClass(solutionClass)
                .withEntityClasses(entiteClass)
                .withConstraintProviderClass(provider)
                .withEnvironmentMode(EnvironmentMode.NO_ASSERT)
                .withRandomSeed(GRAINE)
                .withPhases(new ConstructionHeuristicPhaseConfig(), recuitSimule())
                .withTerminationConfig(terminaison(execution.arretDemande()
                        ? DUREE_APRES_ARRET : Duration.ofSeconds(parametres.dureeMaxSecondes())));
        SolverFactory<S> usine = SolverFactory.create(config);
        Solver<S> solver = usine.buildSolver();
        solver.addEventListener(event -> ecouteur.progression(phase, String.valueOf(event.getNewBestScore())));
        execution.suivre(solver);
        return vide ? probleme : solver.solve(probleme);
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

    private static TerminationConfig terminaison(Duration duree) {
        Duration sansAmelioration = duree.dividedBy(DIVISEUR_SANS_AMELIORATION);
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
