# ADR-003 — Optimisation du planning avec Timefold Solver

- **Statut** : accepté
- **Contexte** : le planning existant (placement des patients, roulement et présence des infirmiers, générateurs, salles)
  est géré à la main avec des aides à la décision *locales* (une proposition de place pour un patient, un remplaçant
  pour une case). Aucune vue globale ne permet de minimiser les ressources consommées ni d'équilibrer la charge.
- **Décision** : un module d'optimisation global fondé sur **Timefold Solver** (`timefold-solver-core` 2.7, licence
  Apache 2.0), isolé derrière un port du domaine.

## Choix structurants

1. **Le domaine ignore Timefold.** Le port `OptimiseurPlanningPort` (domaine, `bc-seance`) reçoit des données de domaine
   (`DonneesOptimisation`) et renvoie une proposition (`ResultatOptimisation`). Les annotations Timefold vivent
   uniquement dans `platform/.../infrastructure/optimisation/timefold` (adaptateur sortant). Remplacer le moteur ne
   touche ni le domaine ni l'application.
2. **Pas de starter Spring Boot Timefold** : le moteur est piloté par code (`SolverFactory`), ce qui évite de dépendre
   d'une intégration Spring Boot 4 et garde la configuration (phases, terminaison, graine) lisible et testée.
3. **Deux phases** indépendantes — placement des patients, puis planification des infirmiers sur les nouveaux
   placements — plutôt qu'un modèle unique où la demande d'infirmiers dépendrait dynamiquement des placements :
   chaque phase est plus simple à raisonner, à tester et à expliquer, et peut être lancée seule.
4. **Une proposition, jamais une action.** Le calcul est asynchrone et ne modifie rien. L'application est une
   commande distincte (administrateur), tout ou rien, protégée par une **empreinte** des données lues
   (`OPTIMISATION_PERIMEE`) et par une revérification des règles de planification par le domaine
   (`VerificationDeplacementsService`) — la planification reste la seule source de cohérence.
5. **Les indicateurs sont calculés par le domaine**, pas par le solveur (`IndicateursOptimisationService`) : le « avant /
   après » affiché à l'utilisateur ne dépend pas du moteur.
6. **Recuit simulé** plutôt que l'acceptation tardive par défaut : déplacer un premier patient ne rapporte rien tant que
   le second n'a pas suivi (« vallée » du paysage) ; l'acceptation tardive reste bloquée dès que la stabilité a un coût.
7. **Fonctions commerciales de Timefold évitées** : l'analyse de score (`SolutionManager.analyze`) est réservée à
   l'édition Enterprise ; elle n'est pas utilisée. Les poids sont portés par `ConstraintWeightOverrides`.

## Conséquences

- Exécution en mémoire du centre (quelques centaines de patients, quelques dizaines d'infirmiers) : secondes à
  quelques dizaines de secondes. Un seul calcul à la fois par centre ; `PLANNING_OPTIMISATION_WORKERS` calculs en
  parallèle au total.
- L'historique (20 dernières exécutions par centre) est stocké en JSON dans `planification_optimisation` ; une
  exécution restée « en cours » après un redémarrage est passée en échec au démarrage.
