---
slice: organisation-o1-societes-centres
status: in-progress
date: 2026-11-02
order: 1 (contexte organisation, voir roadmap §1 septies)
context: organisation (module cible backend/contexts/organisation)
sources: [.ddd/tactical/organisation.md, ADR 0010, 0024, 0063, docs/reference/regles-de-gestion]
---

# O1 — Sociétés et centres (OHS, CentreId)

> **Vérité au 2026-11-02** (remplace les états précédents de cette fiche). Code construit et branché (`SocietesControleur`,
> `CentresControleur`, `OrganisationService`). Le legacy (`SocieteRestController`) a été supprimé SANS filet (b365a63a) ; le filet a
> été reconstitué après coup sur `b365a63a^` (`.ddd/safety-net/organisation-retroactif.md`) : parité atteinte sur les 54 goldens
> `o1-societes` et les 4 goldens `o1-logo-documents` (logo et pied de page lus par les documents), sauf deux écarts DÉCIDÉS le
> 2026-11-03 : identifiant illisible 400 au lieu de 500 (KB-ORG-02) et ordre des centres non déterministe côté legacy (KB-ORG-04,
> tri par code dans le filet). Invariant « dernier centre actif » : verrou de la société + transaction + 409, règle dans le domaine,
> prouvé sur PostgreSQL 16. Droits : SUPERADMIN (`/api/v1/societes` et `/api/v1/organisation/societes`, nouvelles URLs publiques :
> écart avec « mêmes URLs » de l'ADR 0024). Reste : `SocieteJpaEntity`/`CenterJpaEntity` (auth, licence), validation métier des termes du
> glossaire (`proposed`).

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