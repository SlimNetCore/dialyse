---
slice: dossier-medical-dm3-examens
status: planned
date: 2026-10-29
order: 3
context: dossier-medical
sources: [.ddd/tactical/dossier-medical.md, ADR 0065]
---

> **Statut vrai au 2026-11-06 (ADR 0080)** : `planned`, aucun filet DM3. Tables réelles : `demandes_examen`, `lignes_demande_examen` (pas de `center_id` sur les lignes). Reprise SUR PLACE : mêmes tables, mêmes URL `/patients/{id}/demandes-examen/*`. Le contenu gelé du module est dans l'historique git (542c7571).

# DM3 — Demandes d'examen et résultats

Agrégat `DemandeExamen`. Événement `ResultatExamenRecu`.

## Cas d'usage

`DemanderExamen`, `RecevoirResultat`, `ConsulterExamens`.

## Entrées legacy

`bc-medical` (examen).

## Données

Tables examens ; aucune migration.