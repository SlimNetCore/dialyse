---
slice: seance-se3-consommables
status: planned
date: 2026-10-29
order: 3
context: seance
sources: [.ddd/tactical/seance.md, ADR 0062, 0074]
---

# SE3 — Consommables déclarés (motif de sortie stock)

> Statut vrai au 2026-11-02 : `planned` (zéro ligne écrite dans le module ; `built` était faux). Module gelé (GEL.md, ADR 0074). Jalon posé par Stock : `ConsommationsDeSeance` déjà intégré à `SeanceRestController` ; filet : `SortiesPourSeancesIntegrationTest` (44 goldens) et `ArticlesDeLaSeanceCharacterizationIntegrationTest` (15 goldens). Reprise sur place.

Agrégat `ConsommableDeclare`. Motif de sortie stock (Q2) via ACL Stock (`ConsommationsDeSeance`).

## Cas d'usage

`DeclarerConsommables`, `ListerConsommablesDeSeance`.

## Entrées legacy

`bc-seance` (ConsommableDeclare), `SeanceRaccourciJdbcAdapter`.

## Consommateurs

Stock (sortie pour séances, S6b), Facturation (forfait).

## Données

Tables consommations ; aucune migration. Coordination avec Stock via langage publié.