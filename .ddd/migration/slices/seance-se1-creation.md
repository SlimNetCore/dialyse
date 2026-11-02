---
slice: seance-se1-creation
status: planned
date: 2026-10-29
order: 1
context: seance
sources: [.ddd/tactical/seance.md, ADR 0002, 0006, 0062, 0074]
---

# SE1 — Création de séance (conditionnée par PEC valide)

> Statut vrai au 2026-11-02 : `planned` (la mention `built` posée par 2978e639 était fausse). Module gelé (`backend/contexts/seance/GEL.md`, ADR 0074) : persistance sur colonnes inexistantes (`pec_id`, `type_seance`), statuts sans `FACTUREE`/`ABSENT`, aucune parité. Reprise sur place : mêmes tables, mêmes URL, statuts du LEGACY ; filet de création et d'unicité à écrire d'abord. Ordre de reprise de la Séance : dernier.

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