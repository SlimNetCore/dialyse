---
slice: pilotage-direction-pd3-alertes
status: built
date: 2026-10-29
order: 3
context: pilotage-direction
sources: [.ddd/tactical/pilotage-direction.md, ADR 0066]
---

# PD3 â€” Alertes direction

AgrÃ©gat `AlerteDirection` (root). Alerte unique par centre et jour (R2). Types : SOUS_EFFECTIF, SUR_CAPACITE,
RETARD_VALIDATION, GREFFE_EN_ATTENTE.

## Cas d'usage

`GenererAlerte`, `ConsulterAlertesDuCentre`, `AcquitterAlerte`.

## EntrÃ©es legacy

`DirectionAlertPolicy`.

## DonnÃ©es

Table alertes ; aucune migration. Consommation Ã©vÃ©nements publiÃ©s (AbsenceDetectee, etc.).