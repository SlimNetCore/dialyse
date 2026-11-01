---
slice: pilotage-direction-pd2-instantanes-mensuels
status: planned
date: 2026-10-29
order: 2
context: pilotage-direction
sources: [.ddd/tactical/pilotage-direction.md, ADR 0009, 0066]
---

# PD2 — Instantanés mensuels figés

Agrégat `InstantaneMensuel` (root). Un instantané par centre et mois (R1). Consomme le verrou de clôture Facturation
(OHS).

## Cas d'usage

`CapturerInstantaneMensuel`, `ConsulterInstantane`.

## Entrées legacy

`DirectionSnapshotService`.

## Données

Table instantanés ; aucune migration. Cache `ref.instants.mois` (PT3M).