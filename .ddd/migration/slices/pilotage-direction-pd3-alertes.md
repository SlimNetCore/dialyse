---
slice: pilotage-direction-pd3-alertes
status: planned
date: 2026-10-29
order: 3
context: pilotage-direction
sources: [.ddd/tactical/pilotage-direction.md, ADR 0066]
---

# PD3 — Alertes direction

Agrégat `AlerteDirection` (root). Alerte unique par centre et jour (R2). Types : SOUS_EFFECTIF, SUR_CAPACITE,
RETARD_VALIDATION, GREFFE_EN_ATTENTE.

## Cas d'usage

`GenererAlerte`, `ConsulterAlertesDuCentre`, `AcquitterAlerte`.

## Entrées legacy

`DirectionAlertPolicy`.

## Données

Table alertes ; aucune migration. Consommation événements publiés (AbsenceDetectee, etc.).