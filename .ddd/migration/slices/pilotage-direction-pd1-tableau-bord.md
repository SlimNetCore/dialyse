---
slice: pilotage-direction-pd1-tableau-bord
status: planned
date: 2026-10-29
order: 1
context: pilotage-direction
sources: [.ddd/tactical/pilotage-direction.md, ADR 0066]
---

# PD1 — Tableau de bord direction (read models)

Agrégat `TableauDeBord` (entité dédiée). Consomme les langages publiés des autres contextes (lecture uniquement, aucune
écriture cross-context R4).

## Cas d'usage

`ConsulterTableauDeBord`, `FiltrerParCentre`.

## Entrées legacy

`platform/application/direction` (`Direction*QueryService`).

## Données

Read models ; aucune migration. centerId + société anonymisée. Cache `ref.alertes.centre`.