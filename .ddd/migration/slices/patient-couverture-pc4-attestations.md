---
slice: patient-couverture-pc4-attestations
status: planned
date: 2026-10-29
order: 4
context: patient-couverture
sources: [.ddd/tactical/patient-couverture.md, ADR 0064]
---

# PC4 — Attestations de droits

> Statut vrai au 2026-11-02 : `planned` (module gelé, `GEL.md`, ADR 0073). Le filet se limite à 2 tests unitaires legacy (`bc-insurance`), aucune caractérisation HTTP. Règle R5 (attestation liée à une PEC active) non implémentée et non observée dans le legacy : à confirmer au filet avant de la retenir.

Entité satellite de Patient. Attestation liée à une PEC active (R5).

## Cas d'usage

`EnregistrerAttestation`, `ConsulterAttestation`, `ListerAttestations`.

## Entrées legacy

`bc-pec` (attestations).

## Données

Tables attestations ; aucune migration.