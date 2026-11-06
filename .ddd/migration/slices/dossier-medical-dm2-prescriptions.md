---
slice: dossier-medical-dm2-prescriptions
status: planned
date: 2026-10-29
order: 2
context: dossier-medical
sources: [.ddd/tactical/dossier-medical.md, ADR 0065]
---

> Statut vrai au 2026-11-02 : `planned` (module gelé, `backend/contexts/dossier-medical/GEL.md`, ADR 0072). Aucun filet (ordonnances et prescriptions legacy non caractérisées), `OrdonnanceRestController` et `ordonnance.jrxml` non modélisés. Reprise : filet d'abord, puis refactoring sur place.

> **Découpage réel (2026-11-06, `.ddd/safety-net/dossier-medical-inventaire.md`, ADR 0080)** : statut `planned`, aucun filet DM2. Périmètre élargi (trou d'inventaire ACCEPTÉ) : en plus des ordonnances et prescriptions, DM2 porte l'**anémie et l'observance** (`suivi-anemie`, `observance-anemie`, `alertes-observance` + `resoudre`, `administrations-anemie` GET/POST, tables `administrations_anemie`, `alertes_observance`) et le **planificateur `ObservancePrescriptionScheduler` (cron 06:30). Tables réelles : `ordonnances`, `lignes_ordonnance`, `prescriptions_medicales`. Le module `contexts/dossier-medical` ne porte aujourd'hui que DM1 ; le contenu gelé d'avant est dans l'historique git (542c7571).

# DM2 — Prescriptions (ordonnances)

Agrégat `Prescription`. Transition de statut d'ordonnance. Événement `PrescriptionSignee`.

## Cas d'usage

`CreerPrescription`, `SignerPrescription`, `ConsulterPrescriptions`.

## Entrées legacy

`bc-medical` (ordonnance).

## Données

Tables prescriptions ; aucune migration.