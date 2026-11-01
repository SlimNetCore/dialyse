---
slice: patient-couverture-pc2-assure-principal
status: built
date: 2026-10-29
order: 2
context: patient-couverture
sources: [.ddd/tactical/patient-couverture.md, ADR 0064]
---

# PC2 — Assuré principal

Agrégat `Assure` (root). Un seul assuré principal par patient (R1).

## Cas d'usage

`AffecterAssurePrincipal`, `ModifierAssure`, `ConsulterAssure`.

## Entrées legacy

`bc-assure`, `bc-insurance`.

## Données

Tables assure ; aucune migration.