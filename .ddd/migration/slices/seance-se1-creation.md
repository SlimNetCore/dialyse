---
slice: seance-se1-creation
status: built
date: 2026-10-29
order: 1
context: seance
sources: [.ddd/tactical/seance.md, ADR 0002, 0006, 0062]
---

# SE1 — Création de séance (conditionnée par PEC valide)

Agrégat `Seance` (root). Cycle `CREE -> VALIDEE -> SIGNEE`. Création conditionnée par une PEC valide (port
`PECRepository` Patient & couverture).

## Cas d'usage

`CreerSeance`, `ConsulterSeance`, `ListerSeancesDuJour`.

## Entrées legacy

`bc-seance` (Seance), `SeanceApplicationService`, `SeanceRestController`.

## Prérequis

Patient & couverture doit exposer `PECRepository` (ADR 0062). Organisation disponible.

## Données

Table `seances` ; aucune migration. centerId obligatoire. Statut `ABSENT` supprimé (ADR 0002).