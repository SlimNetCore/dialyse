---
slice: seance-se6-verrou-facturation
status: planned
date: 2026-10-29
order: 6
context: seance
sources: [.ddd/tactical/seance.md, ADR 0062, 0074]
---

# SE6 — Verrou facturation (réception FacturationValidée)

> Statut vrai au 2026-11-02 : `planned` (`built` était faux). Module gelé (GEL.md, ADR 0074) : UPDATE de masse d'une colonne `verrouille_facturation` inexistante. Le legacy verrouille par le statut `FACTUREE` + `facture_id` (filet : `SeanceFactureeImmuableCharacterizationIntegrationTest`, `SeancesFactureesCharacterizationIntegrationTest`). Reprise sur place avec les statuts du LEGACY.

`FACTUREE` devient un verrou interne posé sur réception de `FacturationValidée` (ADR 0002). Modulith events-api pour
l'écouteur.

## Cas d'usage

Réception événement `FacturationValidée` → verrouillage immuable de la séance.

## Entrées legacy

`bc-seance` (verrou facturation).

## Données

Colonne verrou ; aucune migration. Idempotence via `JournalDesMessagesTraites`.