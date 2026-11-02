---
slice: patient-couverture-pc2-assure-principal
status: characterized
date: 2026-10-29
order: 2
context: patient-couverture
sources: [.ddd/tactical/patient-couverture.md, ADR 0064]
---

# PC2 — Assuré principal

> Statut vrai au 2026-11-02 : `characterized` (7 goldens legacy `characterization/patientcouverture/assure`, URL legacy). Module gelé (`GEL.md`, ADR 0073) : le code du module n'est ni câblé ni en parité ; R1 vit dans le service applicatif. Reprise sur place après le filet PC1.

Agrégat `Assure` (root). Un seul assuré principal par patient (R1).

## Cas d'usage

`AffecterAssurePrincipal`, `ModifierAssure`, `ConsulterAssure`.

## Entrées legacy

`bc-assure`, `bc-insurance`.

## Données

Tables assure ; aucune migration.