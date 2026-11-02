---
slice: seance-se4-derogation-regularisation
status: planned
date: 2026-10-29
order: 4
context: seance
sources: [.ddd/tactical/seance.md, ADR 0062, 0074]
---

# SE4 — Dérégation de régularisation

> Statut vrai au 2026-11-02 : `planned` (`built` était faux). Module gelé (GEL.md, ADR 0074) ; le déverrouillage n'est pas persisté par le module. **Migration V50** (`derogations_regularisation`, renumérotée depuis V22 en doublon par `16d27b7b`) : table utilisée par le seul module gelé, donc par aucun code déployé ; **conservée** (additive, idempotente, vide, prouvé par `backend/scripts/seance-pg16-rejeu.sh`) et consignée dans `verifications-production.md` § 39. Le legacy déverrouille par `regularisation_deverrouillee_at/by` sur `seances` (`SeanceRegularisationService`). Reprise sur place : décider alors de conserver ou de retirer la table.

Agrégat satellite `DerogationRegularisation`. Déverrouillage > 7j, événement `SeanceDeverrouilleePourRegularisation`.

## Cas d'usage

`CreerDerogation`, `ConsulterDerogation`.

## Entrées legacy

`bc-seance` (DerogationRegularisation).

## Données

Table dérogations ; aucune migration.