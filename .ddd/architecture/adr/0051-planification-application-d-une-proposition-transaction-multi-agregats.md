---
status: proposed
date: 2026-10-26
decision-makers: [utilisateur (décision 7 du 2026-10-26 : exception assumée), architecte]
context-scope: planification (slice P8)
research: []
related: ADR 0049, 0050 ; `.ddd/tactical/planification.md` §6, P22, RG-PLN-091/094, RG-INF-070
---
# 0051 — Appliquer une proposition d'optimisation en une seule transaction sur plusieurs agrégats

## Contexte et problème
`AppliquerUneProposition` est tout ou rien (RG-PLN-091) et écrit `ExecutionDOptimisation` + `PlacesDuCentre` +
N `RoulementDeLInfirmier` + N `Remplacement` + N `DeplacementTemporaire`. Règle par défaut : une transaction = un
agrégat. Aujourd'hui : une transaction (`OptimisationApplicationService.java:91`), sans verrou.

## Options considérées
1. **Une transaction, verrous en ordre fixe, empreinte revérifiée sous verrou** — pour : comportement actuel, aucun
   état intermédiaire visible.
2. Saga avec compensation — contre : états intermédiaires inacceptables pour le métier (planning à moitié appliqué),
   compensation des roulements complexe.
3. Une transaction par agrégat sans compensation — contre : viole RG-PLN-091.

## Décision
Option 1, **exception explicite et limitée à ce seul cas d'usage**. Ordre des verrous : ligne `planning_parametres` du
centre, puis lignes `infirmier` par identifiant croissant. Sous verrou : empreinte recalculée (sinon
`OPTIMISATION_PERIMEE`), chaque nouvelle place revérifiée par `PlacesDuCentre` (P3-P5), roulement par
`RoulementDeLInfirmier` (P14) ; jours écrits avant la place. Un seul refus ⇒ rollback complet. Délai de verrou 5 s
(ADR 0043). Aucun autre cas d'usage ne peut écrire plusieurs agrégats (hors écritures croisées synchrones de l'ADR 0052).

## Conséquences
+ Parité avec le legacy, invariants d'ensemble préservés, pas d'interblocage par construction.
− Transaction longue possible (horizon 8 semaines) ; les écritures concurrentes du centre attendent ou reçoivent 409.
⚠ Test d'intégration PostgreSQL 16 : application concurrente d'un placement ⇒ un seul gagne, rien de partiel.

## Révision
Si une application dépasse le délai de verrou en production, ou si les roulements sortent dans un autre contexte.
