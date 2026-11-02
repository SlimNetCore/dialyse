---
slice: seance-se5-forfait-surcharge
status: planned
date: 2026-10-29
order: 5
context: seance
sources: [.ddd/tactical/seance.md, ADR 0062, 0074]
---

# SE5 — Forfait surchargé

> Statut vrai au 2026-11-02 : `planned`. Module gelé (GEL.md, ADR 0074) : forfait surchargé non persisté, prix absent accepté à 0, re-surcharge ignorée (le legacy remplace le forfait). Filet partiel : goldens Facturation `simulation/exclusion-forfait` (49). Reprise sur place.

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