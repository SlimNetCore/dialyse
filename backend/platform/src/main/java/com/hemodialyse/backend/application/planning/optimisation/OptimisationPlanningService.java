package com.hemodialyse.backend.application.planning.optimisation;

import com.hemodialyse.backend.domain.planning.optimisation.model.CalendrierProposition.CaseCalendrier;
import com.hemodialyse.backend.domain.planning.optimisation.model.DonneesOptimisation;
import com.hemodialyse.backend.domain.planning.optimisation.port.CalendrierPropositionPort;
import com.hemodialyse.backend.domain.planning.optimisation.service.CalendrierPropositionService;
import com.hemodialyse.backend.domain.planning.service.PlanningSemaineService;
import com.hemodialyse.backend.domain.planning.optimisation.model.ParametresOptimisation;
import com.hemodialyse.backend.domain.planning.optimisation.model.ResultatOptimisation;
import com.hemodialyse.backend.domain.planning.optimisation.model.RunOptimisation;
import com.hemodialyse.backend.domain.planning.optimisation.port.OptimisationDonneesPort;
import com.hemodialyse.backend.domain.planning.optimisation.port.OptimisationRunRepositoryPort;
import com.hemodialyse.backend.domain.planning.optimisation.port.OptimiseurPlanningPort;
import com.hemodialyse.backend.domain.planning.optimisation.port.OptimiseurPlanningPort.Ecouteur;
import com.hemodialyse.backend.domain.planning.optimisation.port.ReglagesOptimisationPort;
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
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

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
    private final ReglagesOptimisationPort reglages;
    private final CalendrierPropositionPort calendriers;
    private final Clock horloge;
    private final Map<UUID, Object> verrous = new ConcurrentHashMap<>();

    @Autowired
    public OptimisationPlanningService(OptimisationDonneesPort donnees, OptimisationRunRepositoryPort runs,
                                       OptimiseurPlanningPort optimiseur, ReglagesOptimisationPort reglages,
                                       CalendrierPropositionPort calendriers) {
        this(donnees, runs, optimiseur, reglages, calendriers, Clock.systemUTC());
    }

    OptimisationPlanningService(OptimisationDonneesPort donnees, OptimisationRunRepositoryPort runs,
                                OptimiseurPlanningPort optimiseur, ReglagesOptimisationPort reglages,
                                CalendrierPropositionPort calendriers, Clock horloge) {
        this.donnees = donnees;
        this.runs = runs;
        this.optimiseur = optimiseur;
        this.reglages = reglages;
        this.calendriers = calendriers;
        this.horloge = horloge;
    }

    /**
     * Lance une optimisation et rend immédiatement l'exécution {@code EN_COURS}. Les contraintes de personnel (durée
     * d'une vacation, temps plein, repos hebdomadaire) sont celles des réglages du centre.
     *
     * @throws BusinessException {@code OPTIMISATION_DEJA_EN_COURS} si le centre a déjà un calcul en cours
     */
    public RunOptimisation lancer(UUID centerId, ParametresOptimisation parametres, String utilisateur) {
        return lancer(centerId, parametres, utilisateur, run -> {
        });
    }

    /**
     * Comme {@link #lancer(UUID, ParametresOptimisation, String)}, en appelant {@code aLaFin} quand l'exécution se
     * termine (avec ou sans succès) — utilisé par la replanification automatique pour enchaîner les calculs.
     */
    public RunOptimisation lancer(UUID centerId, ParametresOptimisation demandes, String utilisateur,
                                  Consumer<RunOptimisation> aLaFin) {
        ParametresOptimisation parametres = demandes.avecReglages(reglages.lire(centerId));
        synchronized (verrous.computeIfAbsent(centerId, k -> new Object())) {
            if (runs.findEnCours(centerId).isPresent()) {
                throw new BusinessException("OPTIMISATION_DEJA_EN_COURS",
                        "Une optimisation est déjà en cours pour ce centre");
            }
            DonneesOptimisation lues = donnees.charger(centerId, parametres.debutSemaine(), parametres.finHorizon());
            RunOptimisation run = runs.save(RunOptimisation.demarrer(centerId, parametres, utilisateur,
                    EmpreinteOptimisation.calculer(lues, parametres), horloge.instant()));
            runs.purger(centerId, HISTORIQUE_MAX);
            calendriers.purgerOrphelins(centerId);
            optimiseur.demarrer(run.id(), lues, parametres, new Suivi(run, lues, aLaFin));
            return run;
        }
    }

    public RunOptimisation consulter(UUID centerId, UUID runId) {
        return runs.findById(centerId, runId).orElseThrow(OptimisationPlanningService::introuvable);
    }

    /**
     * Semaines (dates de début) du planning calendaire de la proposition ; vide pour une exécution sans résultat ou
     * antérieure à l'introduction du calendrier.
     *
     * @throws BusinessException {@code OPTIMISATION_INTROUVABLE} si l'exécution n'appartient pas au centre
     */
    public List<LocalDate> semainesCalendrier(UUID centerId, UUID runId) {
        consulter(centerId, runId);
        return calendriers.semaines(centerId, runId);
    }

    /**
     * Lignes (salle × créneau) d'une semaine du planning calendaire de la proposition.
     */
    public List<CaseCalendrier> calendrier(UUID centerId, UUID runId, LocalDate semaineDebut) {
        consulter(centerId, runId);
        return calendriers.lire(centerId, runId, PlanningSemaineService.debutSemaine(semaineDebut));
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

    /**
     * Supprime une exécution de l'historique du centre, avec son planning calendaire. Un calcul en cours doit d'abord
     * être arrêté.
     *
     * @throws BusinessException {@code OPTIMISATION_INTROUVABLE} si l'exécution n'appartient pas au centre,
     *                           {@code OPTIMISATION_SUPPRESSION_EN_COURS} si elle est encore en cours
     */
    public void supprimer(UUID centerId, UUID runId) {
        RunOptimisation run = consulter(centerId, runId);
        if (run.enCours()) {
            throw new BusinessException("OPTIMISATION_SUPPRESSION_EN_COURS",
                    "Un calcul en cours ne peut pas être supprimé : arrêtez-le d'abord");
        }
        runs.supprimer(centerId, runId);
        calendriers.purgerOrphelins(centerId);
    }

    static BusinessException introuvable() {
        return new BusinessException("OPTIMISATION_INTROUVABLE", "Optimisation introuvable");
    }

    /**
     * Enregistre l'avancement (au plus une fois par seconde) puis le résultat d'une exécution. Les erreurs de
     * persistance sont journalisées : elles ne doivent pas interrompre le moteur.
     */
    private final class Suivi implements Ecouteur {
        private final Consumer<RunOptimisation> aLaFin;
        private final DonneesOptimisation lues;
        private volatile RunOptimisation courant;
        private volatile Instant derniereProgression = Instant.MIN;

        private Suivi(RunOptimisation run, DonneesOptimisation lues, Consumer<RunOptimisation> aLaFin) {
            this.courant = run;
            this.lues = lues;
            this.aLaFin = aLaFin;
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
            figerCalendrier(resultat);
            signalerFin();
        }

        /**
         * Fige le planning calendaire de la proposition tant que l'état du centre lu au lancement est en mémoire.
         * Un échec est journalisé : la proposition reste utilisable, sans calendrier imprimable.
         */
        private void figerCalendrier(ResultatOptimisation resultat) {
            try {
                calendriers.enregistrer(courant.centerId(), courant.id(),
                        CalendrierPropositionService.construire(lues, resultat, courant.parametres()));
            } catch (RuntimeException e) {
                log.warn("[OPTIMISATION] Calendrier de l'exécution {} non construit", courant.id(), e);
            }
        }

        @Override
        public void echec(String message) {
            enregistrer(courant.echouer(message, horloge.instant()));
            signalerFin();
        }

        private void signalerFin() {
            try {
                aLaFin.accept(courant);
            } catch (RuntimeException e) {
                log.warn("[OPTIMISATION] Suite de l'exécution {} en échec", courant.id(), e);
            }
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
