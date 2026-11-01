---
slice: pilotage-direction-pd1-tableau-bord
status: built
date: 2026-10-29
order: 1
context: pilotage-direction
sources: [.ddd/tactical/pilotage-direction.md, ADR 0066]
---

# PD1 â€” Tableau de bord direction (read models)

AgrÃ©gat `TableauDeBord` (entitÃ© dÃ©diÃ©e). Consomme les langages publiÃ©s des autres contextes (lecture uniquement,
aucune
Ã©criture cross-context R4).

## Cas d'usage

`ConsulterTableauDeBord`, `FiltrerParCentre`.

## EntrÃ©es legacy

`platform/application/direction` (`Direction*QueryService`).

## DonnÃ©es

Read models ; aucune migration. centerId + sociÃ©tÃ© anonymisÃ©e. Cache `ref.alertes.centre`.