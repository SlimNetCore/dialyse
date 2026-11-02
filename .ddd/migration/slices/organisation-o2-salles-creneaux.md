---
slice: organisation-o2-salles-creneaux
status: in-progress
date: 2026-11-02
order: 2
context: organisation
sources: [.ddd/tactical/organisation.md, ADR 0063]
---

# O2 — Salles et créneaux (positions de créneau)

> **Vérité au 2026-11-02** (la mention `planned` précédente était fausse). Construit et branché : `SallesControleur` (ouvrir/fermer),
> `CreneauxControleur`, `SallesJdbc`, `CreneauxJdbc`, droits ADMIN avec garde de centre (le SUPERADMIN reçoit 403 `ROLE_SCOPE`, comme le legacy), DTO. Schéma de production :
> `salle.ouverte` ajouté par la migration V51 (elle manquait au chemin Flyway). Filet : lectures `/api/v1/referentials/salles|positions`
> (14 goldens `o3-referentiels`) et administration `/api/v1/admin/referentials` (salles, créneaux ; 31 goldens `o2-administration`).
> Écart décidé : tri par code des salles et positions (le legacy ne triait pas). Les événements `SalleOuverte`, `SalleFermee`,
> `CreneauCree` du langage publié ne sont JAMAIS émis (aucun consommateur : voir O4). Reste : l'ouverture/fermeture d'une salle n'a
> aucun équivalent legacy, donc aucun golden de parité ; garde de non-régression seulement
> (`AutorisationsDeLOrganisationIntegrationTest`, contrat JDBC).

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