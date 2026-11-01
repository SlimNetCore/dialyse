---
slice: dossier-medical-dm6-biologie
status: planned
date: 2026-10-29
order: 6
context: dossier-medical
sources: [.ddd/tactical/dossier-medical.md, ADR 0065]
---

# DM6 — Observations biologiques

Agrégat `ObservationBiologique`.

## Cas d'usage

`EnregistrerObservation`, `ConsulterObservations`.

## Entrées legacy

`bc-medical` (biologie).

## Données

Tables ; aucune migration. Cache `ref.dossier.constant`/prescriptions.