---
slice: dossier-medical-dm5-antecedents-allergies
status: planned
date: 2026-10-29
order: 5
context: dossier-medical
sources: [.ddd/tactical/dossier-medical.md, ADR 0065]
---

> Statut vrai au 2026-11-02 : `planned` (module gelé, `backend/contexts/dossier-medical/GEL.md`, ADR 0072). Les tables `antecedents` et `allergies` du module sont parallèles aux tables réelles `antecedents_medicaux` et `allergies_patient` ; aucun filet. Reprise : sur place, mêmes tables et URL.

# DM5 — Antécédents et allergies

Agrégats `Antecedent`, `Allergie`.

## Cas d'usage

`AjouterAntecedent`, `AjouterAllergie`, `Consulter`.

## Entrées legacy

`bc-medical` (antécédents, allergies).

## Données

Tables ; aucune migration.