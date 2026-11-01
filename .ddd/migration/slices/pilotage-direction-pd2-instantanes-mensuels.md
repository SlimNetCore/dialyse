---
slice: pilotage-direction-pd2-instantanes-mensuels
status: built
date: 2026-10-29
order: 2
context: pilotage-direction
sources: [.ddd/tactical/pilotage-direction.md, ADR 0009, 0066]
---

# PD2 â€” InstantanÃ©s mensuels figÃ©s

AgrÃ©gat `InstantaneMensuel` (root). Un instantanÃ© par centre et mois (R1). Consomme le verrou de clÃ´ture Facturation
(OHS).

## Cas d'usage

`CapturerInstantaneMensuel`, `ConsulterInstantane`.

## EntrÃ©es legacy

`DirectionSnapshotService`.

## DonnÃ©es

Table instantanÃ©s ; aucune migration. Cache `ref.instants.mois` (PT3M).