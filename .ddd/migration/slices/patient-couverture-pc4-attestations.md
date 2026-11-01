---
slice: patient-couverture-pc4-attestations
status: characterized
date: 2026-10-29
order: 4
context: patient-couverture
sources: [.ddd/tactical/patient-couverture.md, ADR 0064]
---

# PC4 — Attestations de droits

Entité satellite de Patient. Attestation liée à une PEC active (R5).

## Cas d'usage

`EnregistrerAttestation`, `ConsulterAttestation`, `ListerAttestations`.

## Entrées legacy

`bc-pec` (attestations).

## Données

Tables attestations ; aucune migration.