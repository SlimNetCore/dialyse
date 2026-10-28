package com.hemodialyse.backend.contexts.planification.doubles;

import com.hemodialyse.backend.contexts.planification.application.CalendriersDesPropositions;
import com.hemodialyse.backend.contexts.planification.application.DonneesDeLOptimisation;
import com.hemodialyse.backend.contexts.planification.application.NotificationsDeLOptimisation;
import com.hemodialyse.backend.contexts.planification.application.NouveauxIdentifiantsDeLOptimisation;
import com.hemodialyse.backend.contexts.planification.application.Optimiseur;
import com.hemodialyse.backend.contexts.planification.application.UniteDeTravail;
import com.hemodialyse.backend.contexts.planification.domain.CalendrierDeLaProposition.CaseDuCalendrier;
import com.hemodialyse.backend.contexts.planification.domain.DeplacementTemporaire;
import com.hemodialyse.backend.contexts.planification.domain.DeplacementsTemporairesRepository;
import com.hemodialyse.backend.contexts.planification.domain.DonneesDOptimisation;
import com.hemodialyse.backend.contexts.planification.domain.ExecutionDOptimisation;
import com.hemodialyse.backend.contexts.planification.domain.ExecutionsRepository;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.AffectationId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CentreId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CreneauId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.DeplacementTemporaireId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.ExecutionId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.InfirmierId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.RemplacementId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.SalleId;
import com.hemodialyse.backend.contexts.planification.domain.PageDeResultats;
import com.hemodialyse.backend.contexts.planification.domain.Pagination;
import com.hemodialyse.backend.contexts.planification.domain.ParametresDOptimisation;
import com.hemodialyse.backend.contexts.planification.domain.Perimetre;
import com.hemodialyse.backend.contexts.planification.domain.ResultatDOptimisation;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Doubles en mémoire de l'optimisation (P8) : exécutions, déplacements temporaires, calendriers, notifications, identifiants et lecture
 * des données, plus une FAUSSE implémentation du port {@link Optimiseur} : elle ne calcule rien, elle mémorise les demandes et le test
 * répond lui-même par l'écouteur (déterministe, comme le veut RG-PLN-090). La transaction est simulée : une exception restaure les
 * exécutions et les déplacements temporaires tels qu'ils étaient à l'entrée de la transaction englobante.
 */
public final class OptimisationEnMemoire implements UniteDeTravail {

    /** Une demande de calcul faite à la fausse implémentation de l'optimiseur. */
    public record Demande(ExecutionId execution, DonneesDOptimisation donnees, ParametresDOptimisation parametres,
                          Optimiseur.Ecouteur ecouteur) {
    }

    public final Map<ExecutionId, ExecutionDOptimisation> executions = new LinkedHashMap<>();
    public final List<DeplacementTemporaire> temporaires = new ArrayList<>();
    public final Map<ExecutionId, List<CaseDuCalendrier>> calendriersEnregistres = new LinkedHashMap<>();
    public final CalendriersDesPropositions calendriers = new CalendriersDesPropositions() {
        @Override
        public void enregistrer(CentreId centre, ExecutionId execution, List<CaseDuCalendrier> cases) {
            calendriersEnregistres.put(execution, List.copyOf(cases));
            centresDesCalendriers.put(execution, centre);
        }

        private List<CaseDuCalendrier> duCentre(CentreId centre, ExecutionId execution) {
            return centre.equals(centresDesCalendriers.get(execution)) ? calendriersEnregistres.getOrDefault(execution, List.of())
                    : List.of();
        }

        @Override
        public List<CaseDuCalendrier> lire(CentreId centre, ExecutionId execution, LocalDate semaineDebut) {
            return duCentre(centre, execution).stream().filter(c -> c.semaineDebut().equals(semaineDebut))
                    .sorted(Comparator.comparingInt(CaseDuCalendrier::creneauOrdre).thenComparingInt(CaseDuCalendrier::salleOrdre))
                    .toList();
        }

        @Override
        public List<LocalDate> semaines(CentreId centre, ExecutionId execution) {
            return duCentre(centre, execution).stream().map(CaseDuCalendrier::semaineDebut).distinct().sorted().toList();
        }

        @Override
        public void purgerOrphelins(CentreId centre) {
            calendriersEnregistres.keySet().removeIf(id -> centre.equals(centresDesCalendriers.get(id)) && !executions.containsKey(id));
        }
    };
    public final List<String> notifications = new ArrayList<>();
    public final List<Demande> demandes = new ArrayList<>();
    public final List<ExecutionId> arrets = new ArrayList<>();
    public final List<String> journal = new ArrayList<>();
    public DonneesDOptimisation donneesDuCentre;
    public int lectures;
    public boolean demarrageImpossible;
    public int purges;
    private final UniteDeTravail englobante;
    private int profondeur;
    private int compteur = 1;

    public OptimisationEnMemoire(UniteDeTravail englobante) {
        this.englobante = englobante;
    }

    public Demande derniereDemande() {
        return demandes.getLast();
    }

    private UUID suivant() {
        return new UUID(0x8008L, compteur++);
    }

    // ── UniteDeTravail ──
    @Override
    public <T> T executer(Supplier<T> action) {
        return englobante.executer(() -> {
            if (profondeur > 0) return action.get();
            Map<ExecutionId, ExecutionDOptimisation> avant = new LinkedHashMap<>(executions);
            List<DeplacementTemporaire> temporairesAvant = new ArrayList<>(temporaires);
            int notificationsAvant = notifications.size();
            profondeur++;
            try {
                return action.get();
            } catch (RuntimeException e) {
                executions.clear();
                executions.putAll(avant);
                temporaires.clear();
                temporaires.addAll(temporairesAvant);
                while (notifications.size() > notificationsAvant) notifications.removeLast();
                throw e;
            } finally {
                profondeur--;
            }
        });
    }

    public final ExecutionsRepository executionsRepository = new ExecutionsRepository() {
        @Override
        public void enregistrer(ExecutionDOptimisation execution) {
            executions.put(execution.id(), execution);
        }

        @Override
        public Optional<ExecutionDOptimisation> parId(CentreId centre, ExecutionId id) {
            return Optional.ofNullable(executions.get(id)).filter(e -> e.centre().equals(centre));
        }

        @Override
        public Optional<ExecutionDOptimisation> enCours(CentreId centre) {
            return executions.values().stream().filter(e -> e.centre().equals(centre) && e.enCours()).findFirst();
        }

        @Override
        public PageDeResultats<ExecutionDOptimisation> page(CentreId centre, Pagination pagination) {
            List<ExecutionDOptimisation> toutes = executions.values().stream().filter(e -> e.centre().equals(centre))
                    .sorted(Comparator.comparing(ExecutionDOptimisation::creeLe).reversed()).toList();
            int debut = (int) Math.min(pagination.decalage(), toutes.size());
            int fin = (int) Math.min(debut + (long) pagination.taille(), toutes.size());
            return new PageDeResultats<>(toutes.subList(debut, fin), toutes.size(), pagination.page(), pagination.taille());
        }

        @Override
        public void purger(CentreId centre, int aGarder) {
            purges++;
            List<ExecutionDOptimisation> anciennes = executions.values().stream().filter(e -> e.centre().equals(centre))
                    .sorted(Comparator.comparing(ExecutionDOptimisation::creeLe).reversed()).skip(aGarder).toList();
            anciennes.forEach(e -> executions.remove(e.id()));
        }

        @Override
        public boolean supprimer(CentreId centre, ExecutionId id) {
            return parId(centre, id).isPresent() && executions.remove(id) != null;
        }

        @Override
        public int interrompreLesEnCours(String motif) {
            int n = 0;
            for (ExecutionDOptimisation e : List.copyOf(executions.values())) {
                if (e.enCours()) {
                    executions.put(e.id(), e.echouer(motif, e.creeLe()));
                    n++;
                }
            }
            return n;
        }
    };

    public final DeplacementsTemporairesRepository deplacements = new DeplacementsTemporairesRepository() {
        @Override
        public void enregistrer(List<DeplacementTemporaire> nouveaux) {
            for (DeplacementTemporaire d : nouveaux) {
                temporaires.removeIf(t -> t.centre().equals(d.centre()) && t.patient().equals(d.patient()) && t.date().equals(d.date()));
                temporaires.add(d);
            }
        }

        @Override
        public List<DeplacementTemporaire> entre(CentreId centre, LocalDate du, LocalDate au) {
            return temporaires.stream().filter(t -> t.centre().equals(centre) && !t.date().isBefore(du) && !t.date().isAfter(au))
                    .sorted(Comparator.comparing(DeplacementTemporaire::date)
                            .thenComparing(t -> t.patient().valeur().toString())).toList();
        }
    };

    public final DonneesDeLOptimisation donnees = (centre, du, au) -> {
        lectures++;
        return donneesDuCentre;
    };

    public final Optimiseur optimiseur = new Optimiseur() {
        @Override
        public void demarrer(ExecutionId execution, DonneesDOptimisation donnees, ParametresDOptimisation parametres,
                             Ecouteur ecouteur) {
            if (demarrageImpossible) throw new IllegalStateException("solveur indisponible");
            demandes.add(new Demande(execution, donnees, parametres, ecouteur));
        }

        @Override
        public boolean arreter(ExecutionId execution) {
            arrets.add(execution);
            return true;
        }
    };
    private final Map<ExecutionId, CentreId> centresDesCalendriers = new LinkedHashMap<>();

    public final NotificationsDeLOptimisation notificationsDeLOptimisation = new NotificationsDeLOptimisation() {
        @Override
        public void seancesDeplacees(CentreId centre, Perimetre perimetre, int patients, int seancesTemporaires) {
            notifications.add("SEANCES_DEPLACEES|" + perimetre + "|" + patients + "|" + seancesTemporaires);
        }

        @Override
        public void propositionASignaler(CentreId centre, ExecutionId execution, Perimetre perimetre, String motif, int valeur) {
            notifications.add("PROPOSITION|" + perimetre + "|" + motif + "|" + valeur);
        }

        @Override
        public void bilanDeLaNuit(CentreId centre, String issue, int propositions, int echecs) {
            notifications.add("BILAN|" + issue + "|" + propositions + "|" + echecs);
        }

        @Override
        public boolean roulementModifie(CentreId centre, InfirmierId infirmier, ExecutionId execution) {
            return emettre("ROULEMENT|" + execution.valeur() + "|" + infirmier.valeur());
        }

        @Override
        public boolean remplacementAffecte(CentreId centre, InfirmierId infirmier, LocalDate date, SalleId salle, CreneauId creneau,
                                           ExecutionId execution) {
            return emettre("REMPLACEMENT|" + execution.valeur() + "|" + infirmier.valeur() + "|" + date);
        }

        private boolean emettre(String cle) {
            if (notifications.contains(cle)) return false;
            return notifications.add(cle);
        }
    };

    public final NouveauxIdentifiantsDeLOptimisation identifiants = new NouveauxIdentifiantsDeLOptimisation() {
        @Override
        public ExecutionId execution() {
            return new ExecutionId(suivant());
        }

        @Override
        public DeplacementTemporaireId deplacementTemporaire() {
            return new DeplacementTemporaireId(suivant());
        }

        @Override
        public AffectationId affectation() {
            return new AffectationId(suivant());
        }

        @Override
        public RemplacementId remplacement() {
            return new RemplacementId(suivant());
        }
    };

    /** Résultat vide valide pour terminer une exécution dans un test. */
    public static ResultatDOptimisation resultatVide() {
        return new ResultatDOptimisation(List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), null, null);
    }
}
