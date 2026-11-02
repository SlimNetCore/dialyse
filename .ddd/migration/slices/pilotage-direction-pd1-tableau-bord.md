---
slice: pilotage-direction-pd1-tableau-bord
status: planned
date: 2026-11-02
order: 1
context: pilotage-direction
sources: [.ddd/tactical/pilotage-direction.md, ADR 0066, ADR 0075, backend/contexts/pilotage-direction/GEL.md]
---

# PD1 — Tableau de bord direction (lectures)

> **GEL (2026-11-02, ADR 0075)** : le module `contexts/pilotage-direction` est gelé (non branché, hors réacteur). Statut réel : `planned`, aucune parité. Reprise **sur place** : mêmes URL `/api/v1/direction/*`, filet de caractérisation du legacy d'abord.

## Cas d'usage

`ConsulterTableauDeBord` (vue d'ensemble, indicateurs, ventilation), filtrage par centre.

## Entrées legacy

`platform/application/direction` : `DirectionDashboardQueryService`, `DirectionIndicatorsQueryService`, `DirectionBreakdownQueryService`, `AnonymityPolicy` ; `DirectionDashboardRestController` (`/overview`, `/indicators`, `/breakdown`, `/societe`).

## Filet existant et manques

Voir l'inventaire de `backend/contexts/pilotage-direction/GEL.md`. Manque : caractérisation HTTP de la vue d'ensemble, des indicateurs et de la ventilation, avec masquage par centre.

## Données

Aucune table nouvelle ni migration : lectures des langages publiés des autres contextes. Rôle DIRECTION, périmètre société et garde de centre issus de la session.
