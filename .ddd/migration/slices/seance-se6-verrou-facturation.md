---
slice: seance-se6-verrou-facturation
status: built
date: 2026-10-29
order: 6
context: seance
sources: [.ddd/tactical/seance.md, ADR 0062]
---

# SE6 — Verrou facturation (réception FacturationValidée)

`FACTUREE` devient un verrou interne posé sur réception de `FacturationValidée` (ADR 0002). Modulith events-api pour
l'écouteur.

## Cas d'usage

Réception événement `FacturationValidée` → verrouillage immuable de la séance.

## Entrées legacy

`bc-seance` (verrou facturation).

## Données

Colonne verrou ; aucune migration. Idempotence via `JournalDesMessagesTraites`.