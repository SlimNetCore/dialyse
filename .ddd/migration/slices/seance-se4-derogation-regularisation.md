---
slice: seance-se4-derogation-regularisation
status: built
date: 2026-10-29
order: 4
context: seance
sources: [.ddd/tactical/seance.md, ADR 0062]
---

# SE4 — Dérégation de régularisation

Agrégat satellite `DerogationRegularisation`. Déverrouillage > 7j, événement `SeanceDeverrouilleePourRegularisation`.

## Cas d'usage

`CreerDerogation`, `ConsulterDerogation`.

## Entrées legacy

`bc-seance` (DerogationRegularisation).

## Données

Table dérogations ; aucune migration.