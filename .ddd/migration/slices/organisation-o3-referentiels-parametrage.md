---
slice: organisation-o3-referentiels-parametrage
status: built
date: 2026-10-29
order: 3
context: organisation
sources: [.ddd/tactical/organisation.md, ADR 0063]
---

# O3 — Référentiels de paramétrage (médecins, machines, protocoles)

Médecins, machines, protocoles rattachés au centre. Consommés par Seance, GMAO, Planification.

## Cas d'usage

`ReferencerMedecin`, `ReferencerMachine`, `ReferencerProtocole`, listes et recherches paginées.

## Entrées legacy

`bc-referential` (médecins, machines, protocoles), `ReferentialRepositoryAdapter`.

## Données

Tables référentielles existantes ; aucune migration.

## Filet

Goldens HTTP sur CRUD/référentiels ; multi-centre strict.