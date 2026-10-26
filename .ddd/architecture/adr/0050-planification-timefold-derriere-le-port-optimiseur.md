---
status: proposed
date: 2026-10-26
decision-makers: [architecte, utilisateur]
context-scope: planification (slice P8)
research: [.ddd/research/timefold-solver.md]
related: ADR 0012, 0015, 0049 ; `.ddd/tactical/planification.md` §6 (`Optimiseur`), P20-P24
---
# 0050 — Garder Timefold comme bibliothèque d'infrastructure derrière le port `Optimiseur`

## Contexte et problème
Le legacy résout le planning avec Timefold Solver 2.7.0 (`platform/.../optimisation/timefold`, 13 classes, 4 tests de
contraintes) derrière `OptimiseurPlanningPort`. Le solveur est non déterministe (temps), asynchrone, partage un pool de
calculs entre centres (`PLANNING_OPTIMISATION_WORKERS`).

## Options considérées
1. Annoter le domaine (`@PlanningEntity`, `@PlanningVariable`) — contre : domaine dépendant d'une bibliothèque, mutable.
2. **Port `Optimiseur` (application) ; modèle Timefold (`Plan*`, `*ConstraintProvider`, `TimefoldOptimiseurAdapter`)
   déplacé tel quel dans `infrastructure/optimisation/timefold`, traduction explicite `DonneesDOptimisation` ↔ plan ↔
   `ResultatDOptimisation`** — pour : ACL vers la bibliothèque, domaine pur, solveur remplaçable.
3. Réécrire le solveur — contre : hors objectif (« déplacer et resserrer »).

## Décision
Option 2. `SolverManager` créé en bibliothèque (pas de starter), `parallelSolverCount` = workers partagés, durée par
exécution via `withConfigOverride`, arrêt par `terminateEarly` (meilleure solution conservée). Indicateurs avant/après
recalculés par le domaine (P24), jamais repris du solveur. Calcul **hors transaction** ; chaque transition de
`ExecutionDOptimisation` = une transaction ; « une seule en cours par centre » sous le verrou du centre (ADR 0049).
Règles : `ai.timefold` interdit en `domain` **et** en `application` (`.ddd/arch-rules.json`, ArchUnit du module).
Version : 2.7.0 inchangée (2.7.1 GA disponible, 2.8.0 en RC : pas de montée liée à la slice).

## Conséquences
+ Domaine/application testables sans solveur ; filet P8 avec un `Optimiseur` factice déterministe.
− Une couche de traduction de plus (déjà présente dans le legacy, déplacée).
⚠ Reprise au démarrage (`EN_COURS` → échec) indépendante du solveur ; tests `*ConstraintProviderTest` déplacés et verts.

## Révision
Montée de version Timefold ou second solveur.
