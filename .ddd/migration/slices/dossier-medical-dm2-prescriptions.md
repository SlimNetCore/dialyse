---
slice: dossier-medical-dm2-prescriptions
status: planned
date: 2026-10-29
order: 2
context: dossier-medical
sources: [.ddd/tactical/dossier-medical.md, ADR 0065]
---

> Statut vrai au 2026-11-02 : `planned` (module gelé, `backend/contexts/dossier-medical/GEL.md`, ADR 0072). Aucun filet (ordonnances et prescriptions legacy non caractérisées), `OrdonnanceRestController` et `ordonnance.jrxml` non modélisés. Reprise : filet d'abord, puis refactoring sur place.

# DM2 — Prescriptions (ordonnances)

Agrégat `Prescription`. Transition de statut d'ordonnance. Événement `PrescriptionSignee`.

## Cas d'usage

`CreerPrescription`, `SignerPrescription`, `ConsulterPrescriptions`.

## Entrées legacy

`bc-medical` (ordonnance).

## Données

Tables prescriptions ; aucune migration.