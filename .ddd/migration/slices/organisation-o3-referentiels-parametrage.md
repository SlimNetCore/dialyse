---
slice: organisation-o3-referentiels-parametrage
status: in-progress
date: 2026-11-02
order: 3
context: organisation
sources: [.ddd/tactical/organisation.md, ADR 0063]
---

# O3 — Référentiels de paramétrage (médecins, machines, protocoles)

> **Vérité au 2026-11-02**. Construit et branché (`ReferentielsControleur`, `ReferentielsLegacyControleur`,
> `ReferentielsCommunsJdbc`). Les routes CRUD des médecins, machines et protocoles sont NOUVELLES (aucun équivalent legacy direct ;
> l'équivalent était l'admin générique). Droits ADMIN avec garde de centre (validés par l'utilisateur ; SUPERADMIN : 403 `ROLE_SCOPE`) ; modifier et supprimer
> filtrés sur le centre. Les tables `machine` et `protocole` n'existaient que dans `schema.sql` : créées par la migration V51 ; `medecin.actif`
> par V52. Décisions de l'utilisateur (2026-11-02) : suppression LOGIQUE des médecins (aussi dans l'admin générique, seam fermé),
> garde de centre sur `GET /api/v1/referentials/*`, tri des médecins par nom (KB-ORG-03). Filet : 14 goldens `o3-referentiels`,
> 31 `o2-administration` (import CSV compris). Reste : référentiels orphelins (payeurs, agences, caisses, transporteurs, forfaits,
> catégories de transport) en lecture seule, propriétaire à décider.

Médecins, machines, protocoles rattachés au centre. Consommés par Seance, GMAO, Planification.

## Cas d'usage

`ReferencerMedecin`, `ReferencerMachine`, `ReferencerProtocole`, listes et recherches paginées.

## Entrées legacy

`bc-referential` (médecins, machines, protocoles), `ReferentialRepositoryAdapter`.

## Données

Tables référentielles existantes ; aucune migration.

## Filet

Goldens HTTP sur CRUD/référentiels ; multi-centre strict.