---
slice: dossier-medical-dm1-constantes
status: characterized
date: 2026-10-29
order: 1
context: dossier-medical
sources: [.ddd/tactical/dossier-medical.md, ADR 0007, 0065]
---

> Statut vrai au 2026-11-02 (module gelé, `backend/contexts/dossier-medical/GEL.md`, ADR 0072) : 4 goldens legacy (`characterization/dossiermedical/constantes`), aucun code nouveau branché ; la constante legacy est lue du volet médical de la séance (le `Constante` du module gelé est un autre modèle). Reprise : sur place, mêmes tables et URL.

# DM1 — Constantes

Agrégat `Constante`. Événement `ConstanteEnregistree`.

## Cas d'usage

`EnregistrerConstante`, `ConsulterConstantesDuPatient`.

## Entrées legacy

`bc-medical` (constantes).

## Données

Tables constantes ; aucune migration. centerId obligatoire. Cloisonnement données de santé.