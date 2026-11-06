---
slice: dossier-medical-dm5-antecedents-allergies
status: planned
date: 2026-10-29
order: 5
context: dossier-medical
sources: [.ddd/tactical/dossier-medical.md, ADR 0065]
---

> Statut vrai au 2026-11-02 : `planned` (module gelé, `backend/contexts/dossier-medical/GEL.md`, ADR 0072). Les tables `antecedents` et `allergies` du module sont parallèles aux tables réelles `antecedents_medicaux` et `allergies_patient` ; aucun filet. Reprise : sur place, mêmes tables et URL.

> **Découpage réel (2026-11-06, ADR 0080)** : `planned`, aucun filet DM5. Périmètre élargi (trou d'inventaire ACCEPTÉ) : DM5 porte aussi les **abords vasculaires** (`abords-vasculaires` GET/POST/PUT/DELETE, table `abords_vasculaires`, exportés en FHIR `Procedure`). Tables réelles : `antecedents_medicaux`, `allergies_patient`, `abords_vasculaires`.

# DM5 — Antécédents et allergies

Agrégats `Antecedent`, `Allergie`.

## Cas d'usage

`AjouterAntecedent`, `AjouterAllergie`, `Consulter`.

## Entrées legacy

`bc-medical` (antécédents, allergies).

## Données

Tables ; aucune migration.