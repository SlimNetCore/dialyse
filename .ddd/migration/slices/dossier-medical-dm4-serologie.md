---
slice: dossier-medical-dm4-serologie
status: planned
date: 2026-10-29
order: 4
context: dossier-medical
sources: [.ddd/tactical/dossier-medical.md, ADR 0065]
---

# DM4 — Sérologie (détection positive)

Agrégat `Serologie`. Invariant : détection sérologique positive. Événement `SerologiePositiveDetectee`. Port publié
`RisquesInfectieux` pour Planification.

## Cas d'usage

`EnregistrerSerologie`, `ConsulterSerologies`.

## Entrées legacy

`bc-medical` (sérologie).

## Consommateurs

Planification (risques infectieux).