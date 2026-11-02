---
slice: seance-se2-validation-signature
status: planned
date: 2026-10-29
order: 2
context: seance
sources: [.ddd/tactical/seance.md, ADR 0062, 0074]
---

# SE2 — Validation et signature

> Statut vrai au 2026-11-02 : `planned` (aucun golden Séance dédié ; `characterized` était faux). Module gelé (GEL.md, ADR 0074) : `valider`/`signer` sans horodatage ni auteur. Filet partiel existant : validation de séances facturées (Facturation F3), `SeanceDeverrouillageIntegrationTest`, `SeanceScanValidationControllerTest` (voir GEL.md). Reprise sur place.

Validation le jour même sinon déverrouillage admin (7j, RG-SEA-046). Signature médicale.

## Cas d'usage

`ValiderSeance`, `SignerSeance`, `DeverrouillerPourRegularisation`.

## Règles

R1, R2 (verrouillée pour facturation immuable), R3, R5. Invalidation cache `ref.seances.*` synchrone (@CacheEvict).

## Entrées legacy

`bc-seance` (validation, signature).

## Données

Table `seances` ; aucune migration.