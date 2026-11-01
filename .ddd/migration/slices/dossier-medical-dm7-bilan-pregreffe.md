---
slice: dossier-medical-dm7-bilan-pregreffe
status: built
date: 2026-10-29
order: 7
context: dossier-medical
sources: [.ddd/tactical/dossier-medical.md, ADR 0007, 0065]
---

# DM7 — Bilan pré-greffe (isolé, extractible)

Agrégats `BilanPreGreffe`, `EtapeBilanPreGreffe`. Isolé et extractible (ADR 0007). Événement `BilanPreGreffeComplete`.

## Cas d'usage

`DemarrerBilan`, `ValiderEtape`, `CompleterBilan`.

## Entrées legacy

`bc-medical` (bilan greffe).

## Données

Tables bilan ; aucune migration. Port publié pour Patient & couverture (greffe).