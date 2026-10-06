package com.hemodialyse.backend.application.planning.optimisation;

import com.hemodialyse.backend.domain.planning.optimisation.model.DonneesOptimisation;
import com.hemodialyse.backend.domain.planning.optimisation.model.ParametresOptimisation;
import com.hemodialyse.backend.domain.planning.optimisation.model.ResultatOptimisation;
import com.hemodialyse.backend.domain.planning.optimisation.model.RunOptimisation;
import com.hemodialyse.backend.domain.planning.optimisation.port.OptimisationDonneesPort;
import com.hemodialyse.backend.domain.planning.optimisation.port.OptimisationRunRepositoryPort;
import com.hemodialyse.backend.domain.planning.optimisation.port.OptimiseurPlanningPort;
import com.hemodialyse.backend.domain.planning.optimisation.port.OptimiseurPlanningPort.Ecouteur;
import com.hemodialyse.backend.domain.planning.optimisation.service.EmpreinteOptimisation;
import com.hemodialyse.backend.domain.shared.PagedResult;
import com.hemodialyse.backend.domain.shared.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lancement, suivi et arrêt des optimisations du planning d'un centre. Un seul calcul à la fois par centre ; le calcul
 * est asynchrone (le moteur répond par l'écouteur) et son avancement est enregistré, de sorte que l'écran puisse le
 * suivre et que l'historique survive à un redémarrage. Rien n'est appliqué ici : voir
 * {@link OptimisationApplicationService}.
 */
@Service
public class OptimisationPlanningService {

    /**
     * Exécutions conservées par centre : les plus anciennes sont purgées au lancement d'une nouvelle.
     */
    public static final int HISTORIQUE_MAX = 20;
    private static final Duration INTERVALLE_PROGRESSION = Duration.ofSeconds(1);
    private static final Logger log = LoggerFactory.getLogger(OptimisationPlanningService.class);

    private final OptimisationDonneesPort donnees;
    private final OptimisationRunRepositoryPort runs;
    private final OptimiseurPlanningPort optimiseur;
    private final Clock horloge;
    private final Map<UUID, Object> verrous = new ConcurrentHashMap<>();

    @Autowired
    public OptimisationPlanningService(OptimisationDonneesPort donnees, OptimisationRunRepositoryPort runs,
                                       OptimiseurPlanningPort optimiseur) {
        this(donnees, runs, optimiseur, Clock.systemUTC());
    }

    OptimisationPlanningService(OptimisationDonneesPort donnees, OptimisationRunRepositoryPort runs,
                                OptimiseurPlanningPort optimiseur, Clock horloge) {
        this.donnees = donnees;
        this.runs = runs;
        this.optimiseur = optimiseur;
        this.horloge = horloge;
    }

    /**
     * Lance une optimisation et rend immédiatement l'exécution {@code EN_COURS}.
     *
     * @throws BusinessException {@code OPTIMISATION_DEJA_EN_COURS} si le centre a déjà un calcul en cours
     */
    public RunOptimisation lancer(UUID centerId, ParametresOptimisation parametres, String utilisateur) {
        synchronized (verrous.computeIfAbsent(centerId, k -> new Object())) {
            if (runs.findEnCours(centerId).isPresent()) {
                throw new BusinessException("OPTIMISATION_DEJA_EN_COURS",
                        "Une optimisation est déjà en cours pour ce centre");
            }
            DonneesOptimisation lues = donnees.charger(centerId, parametres.debutSemaine(), parametres.finHorizon());
            RunOptimisation run = runs.save(RunOptimisation.demarrer(centerId, parametres, utilisateur,
                    EmpreinteOptimisation.calculer(lues, parametres), horloge.instant()));
            runs.purger(centerId, HISTORIQUE_MAX);
            optimiseur.demarrer(run.id(), lues, parametres, new Suivi(run));
            return run;
        }
    }

    public RunOptimisation consulter(UUID centerId, UUID runId) {
        return runs.findById(centerId, runId).orElseThrow(OptimisationPlanningService::introuvable);
    }

    public PagedResult<RunOptimisation> historique(UUID centerId, int page, int size) {
        return runs.findPaged(centerId, page, size);
    }

    /**
     * Demande l'arrêt anticipé d'un calcul en cours : la meilleure solution trouvée est conservée.
     */
    public RunOptimisation arreter(UUID centerId, UUID runId) {
        RunOptimisation run = consulter(centerId, runId);
        if (run.enCours()) optimiseur.arreter(runId);
        return run;
    }

    static BusinessException introuvable() {
        return new BusinessException("OPTIMISATION_INTROUVABLE", "Optimisation introuvable");
    }

    /**
     * Enregistre l'avancement (au plus une fois par seconde) puis le résultat d'une exécution. Les erreurs de
     * persistance sont journalisées : elles ne doivent pas interrompre le moteur.
     */
    private final class Suivi implements Ecouteur {
        private volatile RunOptimisation courant;
        private volatile Instant derniereProgression = Instant.MIN;

        private Suivi(RunOptimisation run) {
            this.courant = run;
        }

        @Override
        public void progression(String phase, String score) {
            Instant maintenant = horloge.instant();
            if (Duration.between(derniereProgression, maintenant).compareTo(INTERVALLE_PROGRESSION) < 0) return;
            derniereProgression = maintenant;
            enregistrer(courant.progression(phase, score));
        }

        @Override
        public void termine(ResultatOptimisation resultat, String score) {
            enregistrer(courant.terminer(resultat, score, horloge.instant()));
        }

        @Override
        public void echec(String message) {
            enregistrer(courant.echouer(message, horloge.instant()));
        }

        private void enregistrer(RunOptimisation run) {
            try {
                courant = runs.save(run);
            } catch (RuntimeException e) {
                log.warn("[OPTIMISATION] Enregistrement impossible de l'exécution {}", run.id(), e);
            }
        }
    }
}
