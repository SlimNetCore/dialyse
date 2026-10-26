---
topic: Timefold Solver - SolverManager, arrêt anticipé, parallélisme, intégration bibliothèque (Planification P8)
date: 2026-10-26
source: web (docs.timefold.ai/timefold-solver/latest/running-timefold-solver/library/library-integration, doc 2.7.1 ; repo1.maven.org maven-metadata timefold-solver-core) - non vérifié par context7 (clé API invalide, re-testé le 2026-10-26)
libraryId: ai.timefold.solver:timefold-solver-core (projet : 2.7.0 via `timefold-solver.version`, backend/pom.xml:20)
---
## Réponses clés
- Versions Maven Central (lastUpdated 2026-10-15) : 2.7.0, **2.7.1** (GA), 2.8.0-rc-1 (RC, à ne pas prendre). Le projet est en 2.7.0 : **pas de montée** imposée par la Planification (patch 2.7.1 possible, hors périmètre).
- Usage bibliothèque (sans starter Spring) : `SolverManager.create(solverConfig, new SolverManagerConfig())` ; l'intégration Spring Boot/Quarkus ne fait qu'injecter le manager. => le core jar suffit, aucune auto-configuration requise.
- `solverManager.solveBuilder().withProblemId(id).withProblem(p)` (seuls requis) + optionnels `withFirstInitializedSolutionEventConsumer`, `withBestSolutionEventConsumer`, `withFinalBestSolutionEventConsumer`, `withExceptionHandler`, `withConfigOverride` ; `.run()` rend un `SolverJob` sans bloquer.
- `getSolverStatus(problemId)`, `terminateEarly(problemId)` : arrêt anticipé, la meilleure solution reste disponible (`SolverJob.getFinalBestSolution()`). Id immuable (UUID OK).
- `SolverManagerConfig.parallelSolverCount` : nombre de problèmes résolus simultanément, au-delà file d'attente ; défaut `AUTO` = moitié des cœurs. Un pool de threads solveur + un pool consommateurs.
- Le solveur **clone** le problème ; ne pas modifier les solutions reçues par les consommateurs.
- Termination : dans la config du solveur, surchargeable par job (`withConfigOverride`) => durée 2-300 s par exécution (RG-PLN-082).
- JPMS : module `ai.timefold.solver.core` (sans objet : projet en classpath).

## Conséquences pour le projet
- Timefold reste une **bibliothèque d'infrastructure** : `contexts/planification/infrastructure/optimisation/timefold` (déplacement de `platform/.../optimisation/timefold`, 13 classes + 4 tests `ConstraintProvider`). Domaine et application l'ignorent (règles `ai.timefold` interdites).
- Port `Optimiseur` (application) : lancer(problème, réglages) / arrêter(id) / progression ; workers partagés = `parallelSolverCount` (propriété `PLANNING_OPTIMISATION_WORKERS` existante).
- Non-déterminisme (temps) : le filet de caractérisation utilise un `Optimiseur` factice déterministe (tactique §9 P20).

## Limites
- Page lue = intégration bibliothèque ; `ConstraintVerifier` (tests des contraintes) non relu : API déjà utilisée par les tests legacy, à confirmer au déplacement (P8).
- Compatibilité Java 21 non affichée sur la page (en service aujourd'hui).
