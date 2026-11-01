---
slice: organisation-o1-societes-centres
status: characterized
date: 2026-10-29
order: 1 (contexte organisation, voir roadmap §1 septies)
context: organisation (module cible backend/contexts/organisation)
sources: [.ddd/tactical/organisation.md, ADR 0010, 0024, 0063, docs/reference/regles-de-gestion]
---

# O1 — Sociétés et centres (OHS, CentreId)

**Pourquoi d'abord** : fondation multi-centre ; Centre est la racine de l'OHS et seul l'identifiant de centre est publié
(Published Language). Crée le module réel et publie organisation.v1.

## Cas d'usage

`CreerSociete`, `ModifierSociete`, `CreerCentre`, `ModifierCentre`, `TransfererCentre`, `ListerCentresActifs`,
`LireParametresCentre`.

## Entrées legacy

`bc-referential` (sociétés, centres), `ReferentialRepositoryAdapter`, `ReferentialAdminTables`.

## Données

Tables `societes`, `centers` ; propriétaire : Organisation. Aucune migration. centerId obligatoire (ADR 0010).

## Cache

`ref.centres.actifs` (PT3M, clé societeId), `ref.parametres.centre` (PT15M, clé centerId).

## Filet de caractérisation

Goldens HTTP sur CRUD société/centre, transfert, centres actifs, paramètres ; isolation par centre.

## Critères de built

Module `contexts/organisation` réel ; organisation.v1 publié ; garde `FrontieresDesContextesTest` étendue ; tests
unitaires + intégration verts.