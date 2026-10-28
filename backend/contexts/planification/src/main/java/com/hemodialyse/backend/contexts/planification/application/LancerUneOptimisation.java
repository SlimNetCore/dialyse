package com.hemodialyse.backend.contexts.planification.application;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import com.hemodialyse.backend.contexts.planification.domain.CalendrierDeLaProposition;
import com.hemodialyse.backend.contexts.planification.domain.DonneesDOptimisation;
import com.hemodialyse.backend.contexts.planification.domain.Empreinte;
import com.hemodialyse.backend.contexts.planification.domain.ExecutionDOptimisation;
import com.hemodialyse.backend.contexts.planification.domain.ExecutionsRepository;
import com.hemodialyse.backend.contexts.planification.domain.ParametresDOptimisation;
import com.hemodialyse.backend.contexts.planification.domain.ReglagesDePlanification;
import com.hemodialyse.backend.contexts.planification.domain.ReglagesRepository;
import com.hemodialyse.backend.contexts.planification.domain.ResultatDOptimisation;
import com.hemodialyse.backend.contexts.planification.domain.VerrouDesPlaces;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Lance une optimisation et rend immédiatement l'exécution {@code EN_COURS} (P20, RG-PLN-081). Sous le verrou du centre, EN BASE
 * (le même que celui des places : décision du 28/10, KB-PLN-92, qui corrige B3) : réglages du centre lus et figés dans les
 * paramètres, un seul calcul en cours par centre ({@code OPTIMISATION_DEJA_EN_COURS}, 409, y compris avec plusieurs instances du
 * serveur), état du centre lu et son empreinte retenue, exécution enregistrée, historique purgé à {@value #HISTORIQUE_MAX}.
 * Le solveur démarre APRÈS la validation de cette transaction et tourne hors transaction ; chaque transition de l'exécution
 * (progression, fin, échec) est ensuite une transaction. Rien n'est appliqué ici (P23).
 */
public class LancerUneOptimisation {

    /** Exécutions conservées par centre : les plus anciennes sont purgées au lancement d'une nouvelle. */
    public static final int HISTORIQUE_MAX = 20;
    private static final Duration INTERVALLE_PROGRESSION = Duration.ofSeconds(1);
    private static final Logger journal = Logger.getLogger(LancerUneOptimisation.class.getName());

    private final UniteDeTravail uniteDeTravail;
    private final VerrouDesPlaces verrou;
    private final ReglagesRepository reglages;
    private final ExecutionsRepository executions;
    private final DonneesDeLOptimisation donnees;
    private final CalendriersDesPropositions calendriers;
    private final Optimiseur optimiseur;
    private final NouveauxIdentifiantsDeLOptimisation identifiants;
    private final Clock horloge;

    @SuppressWarnings("java:S107")
    public LancerUneOptimisation(UniteDeTravail uniteDeTravail, VerrouDesPlaces verrou, ReglagesRepository reglages,
                                 ExecutionsRepository executions, DonneesDeLOptimisation donnees,
                                 CalendriersDesPropositions calendriers, Optimiseur optimiseur,
                                 NouveauxIdentifiantsDeLOptimisation identifiants, Clock horloge) {
        this.uniteDeTravail = uniteDeTravail;
        this.verrou = verrou;
        this.reglages = reglages;
        this.executions = executions;
        this.donnees = donnees;
        this.calendriers = calendriers;
        this.optimiseur = optimiseur;
        this.identifiants = identifiants;
        this.horloge = horloge;
    }

    public ExecutionDOptimisation executer(CentreId centre, ParametresDOptimisation demandes, String utilisateur) {
        return executer(centre, demandes, utilisateur, execution -> {
        });
    }

    /**
     * @param aLaFin appelé quand l'exécution se termine, avec ou sans succès (la replanification de nuit enchaîne ainsi ses calculs)
     * @throws com.hemodialyse.backend.contexts.planification.domain.RegleDePlanificationViolee {@code OPTIMISATION_DEJA_EN_COURS}
     * @throws com.hemodialyse.backend.contexts.planification.domain.EcritureConcurrente le verrou du centre n'a pas été obtenu
     */
    public ExecutionDOptimisation executer(CentreId centre, ParametresDOptimisation demandes, String utilisateur,
                                           Consumer<ExecutionDOptimisation> aLaFin) {
        Lancement lancement = uniteDeTravail.executer(() -> {
            verrou.prendre(centre);
            ReglagesDePlanification reglagesDuCentre = reglages.lire(centre);
            ParametresDOptimisation parametres = demandes.avecReglages(reglagesDuCentre);
            ExecutionDOptimisation.exigerAucuneEnCours(executions.enCours(centre).isPresent());
            DonneesDOptimisation lues = donnees.charger(centre, parametres.debutSemaine(), parametres.finHorizon());
            ExecutionDOptimisation execution = ExecutionDOptimisation.lancer(identifiants.execution(), centre, parametres, utilisateur,
                    Empreinte.calculer(lues, parametres), horloge.instant());
            executions.enregistrer(execution);
            executions.purger(centre, HISTORIQUE_MAX);
            calendriers.purgerOrphelins(centre);
            return new Lancement(execution, lues, parametres);
        });
        Suivi suivi = new Suivi(lancement.execution(), lancement.lues(), aLaFin);
        try {
            optimiseur.demarrer(lancement.execution().id(), lancement.lues(), lancement.parametres(), suivi);
        } catch (RuntimeException e) {
            suivi.echec("Le calcul n'a pas pu démarrer : " + e.getMessage());
            throw e;
        }
        return lancement.execution();
    }

    private record Lancement(ExecutionDOptimisation execution, DonneesDOptimisation lues, ParametresDOptimisation parametres) {
    }

    /**
     * Enregistre l'avancement (au plus une fois par seconde) puis le résultat d'une exécution, chacun dans sa transaction. Les
     * erreurs de persistance sont journalisées : elles ne doivent pas interrompre le solveur.
     */
    private final class Suivi implements Optimiseur.Ecouteur {
        private final Consumer<ExecutionDOptimisation> aLaFin;
        private final DonneesDOptimisation lues;
        private volatile ExecutionDOptimisation courante;
        private volatile Instant derniereProgression = Instant.MIN;

        private Suivi(ExecutionDOptimisation execution, DonneesDOptimisation lues, Consumer<ExecutionDOptimisation> aLaFin) {
            this.courante = execution;
            this.lues = lues;
            this.aLaFin = aLaFin;
        }

        @Override
        public void progression(String phase, String score) {
            Instant maintenant = horloge.instant();
            if (Duration.between(derniereProgression, maintenant).compareTo(INTERVALLE_PROGRESSION) < 0) return;
            derniereProgression = maintenant;
            enregistrer(courante.progresser(phase, score));
        }

        @Override
        public void termine(ResultatDOptimisation resultat, String score) {
            enregistrer(courante.terminer(resultat, score, horloge.instant()));
            figerLeCalendrier(resultat);
            signalerLaFin();
        }

        /** Un échec est journalisé : la proposition reste utilisable, sans calendrier imprimable. */
        private void figerLeCalendrier(ResultatDOptimisation resultat) {
            try {
                var cases = CalendrierDeLaProposition.construire(lues, resultat, courante.parametres());
                uniteDeTravail.executer(() -> {
                    calendriers.enregistrer(courante.centre(), courante.id(), cases);
                    return null;
                });
            } catch (RuntimeException e) {
                journal.log(Level.WARNING, "[OPTIMISATION] Calendrier de l'exécution " + courante.id().valeur() + " non construit", e);
            }
        }

        @Override
        public void echec(String message) {
            enregistrer(courante.echouer(message, horloge.instant()));
            signalerLaFin();
        }

        private void signalerLaFin() {
            try {
                aLaFin.accept(courante);
            } catch (RuntimeException e) {
                journal.log(Level.WARNING, "[OPTIMISATION] Suite de l'exécution " + courante.id().valeur() + " en échec", e);
            }
        }

        private void enregistrer(ExecutionDOptimisation execution) {
            try {
                uniteDeTravail.executer(() -> {
                    executions.enregistrer(execution);
                    return null;
                });
                courante = execution;
            } catch (RuntimeException e) {
                journal.log(Level.WARNING, "[OPTIMISATION] Enregistrement impossible de l'exécution " + execution.id().valeur(), e);
            }
        }
    }
}
