---
slice: patient-couverture-pc1-identite-mouvements
status: characterized
date: 2026-10-29
order: 1
context: patient-couverture
sources: [.ddd/tactical/patient-couverture.md, ADR 0004, 0024, 0064]
---

# PC1 — Identité administrative et mouvements du patient

Agrégat `Patient` (root) : admission, transfert, sortie, décès, greffe, guérison. Invariant R2 (date de sortie requise),
R1 (un seul assuré principal).

## Cas d'usage

`AdmettrePatient`, `TransfererPatient`, `SortirPatient`, `DeclarerDeces`, `DeclarerGreffe`, `DeclarerGuerison`,
`ConsulterPatient`, `ListerPatients`.

## Entrées legacy

`bc-patient` (Patient, mouvements), `PatientApplicationService`, `MouvementPatientJdbcAdapter`.

## Prérequis

Bloquer la sortie sans date d'événement (décision utilisateur) ; lister cas existants (verifications-production §25.2).

## Données

Tables patient/mouvements ; aucune migration. centerId obligatoire.