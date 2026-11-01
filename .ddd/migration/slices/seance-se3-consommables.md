---
slice: seance-se3-consommables
status: built
date: 2026-10-29
order: 3
context: seance
sources: [.ddd/tactical/seance.md, ADR 0062]
---

# SE3 — Consommables déclarés (motif de sortie stock)

Agrégat `ConsommableDeclare`. Motif de sortie stock (Q2) via ACL Stock (`ConsommationsDeSeance`).

## Cas d'usage

`DeclarerConsommables`, `ListerConsommablesDeSeance`.

## Entrées legacy

`bc-seance` (ConsommableDeclare), `SeanceRaccourciJdbcAdapter`.

## Consommateurs

Stock (sortie pour séances, S6b), Facturation (forfait).

## Données

Tables consommations ; aucune migration. Coordination avec Stock via langage publié.