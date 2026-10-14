---
status: approved
date: 2026-10-09
decision-makers: [secrétaire, administrateur, architecte]
context-scope: patient-couverture
research: []
---
# 0004 — Regrouper patient, assuré, PEC et attestation dans « Patient & couverture »

## Contexte et problème
Quatre micro-modules (`bc-patient`, `bc-assure`, `bc-insurance`, `bc-pec`, 122–755 LOC) partagent les clusters C5/C25 ; `bc-pec → bc-patient` (4 arêtes), `bc-pec → bc-insurance` (1). Le statut patient a trois modèles (état, type vacancier, mouvements — H5).

## Options considérées
1. Garder 4 modules — contre : frontières sans invariant propre, coordination artificielle.
2. Un contexte « Patient & couverture » (patient administratif + droits) — pour : même acteur (secrétaire), même cadence, invariants liés (assuré principal unique, PEC par patient) ; contre : contexte de taille moyenne.
3. Patient seul + Couverture séparée — pour : sépare identité et droits ; contre : aucun signal fort aujourd'hui.

## Décision
Option 2. La **place** et le mouvement « place libérée » sortent vers Planification (ADR 0005).

## Conséquences
+ un seul propriétaire du patient administratif ; OHS pour Séance, Facturation, Planification, Dossier médical.
− l'unification du statut patient (H5) reste à modéliser au tactique.

## Révision
Si la couverture (caisses, attestations) acquiert des échanges automatisés avec les caisses, réévaluer l'option 3.
