---
slice: organisation-o2-salles-creneaux
status: planned
date: 2026-10-29
order: 2
context: organisation
sources: [.ddd/tactical/organisation.md, ADR 0063]
---

# O2 — Salles et créneaux (positions de créneau)

Agrégats `Salle`, `Creneau` (+ position de créneau). Publie `SalleOuverte`, `SalleFermee`, `CreneauCree`.

## Cas d'usage

`OuvrirSalle`, `FermerSalle`, `CreerCreneau`, `ModifierCreneau`, `ListerSallesDuCentre`, `ListerCreneaux`.

## Entrées legacy

`bc-referential` (salles, créneaux, positions), `SalleVueJdbcAdapter`, `ReferentialAdminTables`.

## Consommateurs

Planification (salles/créneaux), Seance, GMAO (capacité salle via port publié).

## Données

Tables `salle`, `position_creneau` ; aucune migration.

## Filet

Goldens HTTP salles/créneaux ; isolation centre ; retrait différé des adapters legacy (P9).