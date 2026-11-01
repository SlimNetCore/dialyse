---
slice: seance-se5-forfait-surcharge
status: planned
date: 2026-10-29
order: 5
context: seance
sources: [.ddd/tactical/seance.md, ADR 0062]
---

# SE5 — Forfait surchargé

VO `ForfaitApplique`. Priorité surcharge > PEC effective > PEC demandée (RG-FAC-021). Événement
`ForfaitDeSeanceSurchargé`.

## Cas d'usage

`DeclarerForfaitSurchargé`.

## Entrées legacy

`bc-seance` (forfait).

## Consommateurs

Facturation (simulation, validation).

## Données

Colonne forfait ; aucune migration.