---
slice: dossier-medical-dm6-biologie
status: planned
date: 2026-10-29
order: 6
context: dossier-medical
sources: [.ddd/tactical/dossier-medical.md, ADR 0065]
---

> Statut vrai au 2026-11-02 : `planned` (module gelé, `backend/contexts/dossier-medical/GEL.md`, ADR 0072). Le nom `observations_biologiques` est une collision avec la table réelle sans garantie de colonnes ; aucun filet. Reprise : sur place.

# DM6 — Observations biologiques

Agrégat `ObservationBiologique`.

## Cas d'usage

`EnregistrerObservation`, `ConsulterObservations`.

## Entrées legacy

`bc-medical` (biologie).

## Données

Tables ; aucune migration. Cache `ref.dossier.constant`/prescriptions.