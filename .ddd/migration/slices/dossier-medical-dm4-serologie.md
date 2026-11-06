---
slice: dossier-medical-dm4-serologie
status: planned
date: 2026-10-29
order: 4
context: dossier-medical
sources: [.ddd/tactical/dossier-medical.md, ADR 0065]
---

> **Statut vrai au 2026-11-06 (ADR 0080)** : `planned`, aucun filet DM4. Table réelle : `serologies_patient`. Les lecteurs SQL hors module (`RisquesInfectieuxJdbc` et `ContraintesDesPatientsJdbc` de la Planification, `DirectionIndicatorsQueryService`) restent en place : jalon DM4/DM8, non touchés par DM1. Audit `SEROLOGIE_*` existant à préserver.

# DM4 — Sérologie (détection positive)

Agrégat `Serologie`. Invariant : détection sérologique positive. Événement `SerologiePositiveDetectee`. Port publié
`RisquesInfectieux` pour Planification.

## Cas d'usage

`EnregistrerSerologie`, `ConsulterSerologies`.

## Entrées legacy

`bc-medical` (sérologie).

## Consommateurs

Planification (risques infectieux).