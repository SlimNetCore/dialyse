---
status: accepted (aucun changement de comportement ; KB-S6a-8 et KB-S6a-9 reproduits, A CONFIRMER métiers/DPO <= 2026-12-15)
date: 2026-11-13
decision-makers: [utilisateur, architecte]
context-scope: platform (outil de reprise de données), patient-couverture
related: ADR 0024, 0079, 0134, 0168 ; `.ddd/migration/reviews/rp1-realisation.md` ; `.ddd/safety-net/known-bugs.md` KB-S6a-8, KB-S6a-9
---

# 0169 - RP1 : l'annulation d'un lot de reprise passe par le langage publié de Patient et couverture

Refactoring sur place (ADR 0024) : mêmes URL, mêmes tables. Filet : goldens de la reprise (ADR 0134, 0168) complétés par trois goldens RP1.

## Décision
`patientcouverture.v1` publie `AnnulationDeLaReprise` : prises en charge et attestations d'un patient (ce qu'il a vécu depuis la reprise), `assureRetenuHorsDuLot` (règle `DonneesCreeesParLeLot`), suppression d'une affectation, d'un patient (avec ses affectations) et d'un assuré, toutes bornées au centre. Application `AnnulerLaReprise`, port sortant `DonneesDeLaReprise`, adapter `DonneesDeLaRepriseJdbc` (requêtes reprises de `MigrationRollbackJdbcAdapter`). La plateforme garde l'orchestration : ordre de suppression, compte rendu des blocages (`MIGRATION_CANCEL_BLOCKED`, texte inchangé), contrôles des autres contextes (séances, factures, dossier médical...).

## Garde de table
`TableDuPatientCouvertureTest` : l'entrée de `MigrationRollbackJdbcAdapter` et son butoir 2027-03-31 sont SUPPRIMÉS (soldés, aucun report) ; un test vérifie que l'adaptateur n'est plus dans la liste blanche ni lecteur des tables. `HistoricalRecordJdbcAdapter` (import et suppression des attestations et PEC du lot, SQL construit) reste sur la liste, inchangé.

## Comportement
Aucun changement : les 3 goldens RP1 et tous les goldens S6a sont inchangés (parité). Deux constats reproduits faute de décision (KB-S6a-8 : un patient modifié depuis l'import est supprimé sans avertissement ; KB-S6a-9 : lectures bloquantes sans borne de centre, refus nommant la première table) : **à confirmer métiers/DPO <= 2026-12-15**. Pas de fichier `_nouveau_code` ni d'archive `_avant_rp1` : rien n'a changé.

## Suite possible
RP2 : lecture publiée « volet complété » (séance) et écritures du dossier médical par ports publiés (ligne 10 de l'inventaire).
