---
slice: patient-couverture-pc3-pec
status: characterized
date: 2026-10-29
order: 3
context: patient-couverture
sources: [.ddd/tactical/patient-couverture.md, ADR 0064]
---

# PC3 — Prise en charge (PEC)

> Statut vrai au 2026-11-02 : `characterized` (10 goldens legacy `characterization/patientcouverture/pec`, URL legacy `/api/v1/pec`). Module gelé (`GEL.md`, ADR 0073) : aucune parité, pas de garde de centre ; suppression d'une PEC inconnue silencieuse (le legacy la rejette). Reprise sur place.

Agrégat `PEC` (root). Cycle `CREE -> VALIDEE -> CLOTUREE` (R3). Port publié `PECRepository` consommé par Seance
(création conditionnée).

## Cas d'usage

`CreerPEC`, `ValiderPEC`, `CloturerPEC`, `ConsulterPEC`, `ListerPECActives`.

## Entrées legacy

`bc-pec`.

## Données

Tables PEC ; aucune migration. Port publié exposé pour Seance.