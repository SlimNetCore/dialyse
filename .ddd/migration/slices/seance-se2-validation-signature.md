---
slice: seance-se2-validation-signature
status: characterized
date: 2026-10-29
order: 2
context: seance
sources: [.ddd/tactical/seance.md, ADR 0062]
---

# SE2 — Validation et signature

Validation le jour même sinon déverrouillage admin (7j, RG-SEA-046). Signature médicale.

## Cas d'usage

`ValiderSeance`, `SignerSeance`, `DeverrouillerPourRegularisation`.

## Règles

R1, R2 (verrouillée pour facturation immuable), R3, R5. Invalidation cache `ref.seances.*` synchrone (@CacheEvict).

## Entrées legacy

`bc-seance` (validation, signature).

## Données

Table `seances` ; aucune migration.