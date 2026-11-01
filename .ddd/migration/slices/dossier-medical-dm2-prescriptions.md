---
slice: dossier-medical-dm2-prescriptions
status: built
date: 2026-10-29
order: 2
context: dossier-medical
sources: [.ddd/tactical/dossier-medical.md, ADR 0065]
---

# DM2 — Prescriptions (ordonnances)

Agrégat `Prescription`. Transition de statut d'ordonnance. Événement `PrescriptionSignee`.

## Cas d'usage

`CreerPrescription`, `SignerPrescription`, `ConsulterPrescriptions`.

## Entrées legacy

`bc-medical` (ordonnance).

## Données

Tables prescriptions ; aucune migration.