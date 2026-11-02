---
slice: dossier-medical-dm7-bilan-pregreffe
status: planned
date: 2026-10-29
order: 7
context: dossier-medical
sources: [.ddd/tactical/dossier-medical.md, ADR 0007, 0065]
---

> Statut vrai au 2026-11-02 : `planned` (module gelé, `backend/contexts/dossier-medical/GEL.md`, ADR 0072). Tables parallèles `bilans_pregreffe` / `etapes_bilan_pregreffe` face aux tables réelles `bilans_pre_greffe` / `etapes_bilan_pre_greffe` ; sans transaction ni filet. Reprise : sur place.

# DM7 — Bilan pré-greffe (isolé, extractible)

Agrégats `BilanPreGreffe`, `EtapeBilanPreGreffe`. Isolé et extractible (ADR 0007). Événement `BilanPreGreffeComplete`.

## Cas d'usage

`DemarrerBilan`, `ValiderEtape`, `CompleterBilan`.

## Entrées legacy

`bc-medical` (bilan greffe).

## Données

Tables bilan ; aucune migration. Port publié pour Patient & couverture (greffe).