---
status: proposed
date: 2026-10-26
decision-makers: [utilisateur (décisions 1 et 2 du 2026-10-26), architecte]
context-scope: planification, patient-couverture (legacy), dossier-medical (legacy)
research: []
related: ADR 0005 (migration des colonnes reportée), 0024, 0031 (précédent : port publié dans la transaction de l'appelant), 0049 ; B2/B7/B10
---
# 0052 — Écrire la place dans les colonnes de `patients` sans migration ; la fiche patient appelle la Planification

## Contexte et problème
La place (salle, créneau, générateur, jours) vit dans `patients` ; la fiche patient (JPA, ligne entière) et la
Planification écrivent les mêmes colonnes (B2). ADR 0005 prévoyait de les sortir de `patients` ; ADR 0024 interdit une
migration pour une slice qui réutilise les tables.

## Options considérées
1. Migrer les colonnes vers une table de la Planification — contre : migration + réécriture des lecteurs ; reporté.
2. **Garder les colonnes ; seul `PlacesDuCentreRepository` les écrit (UPDATE ciblé) ; la fiche patient cesse de les
   écrire et appelle le port publié `PlacementDesPatients.placer` dans sa transaction** — pour : un seul écrivain,
   aucune migration.

## Décision
Option 2. **Petite modification du legacy Patient, autorisée par l'utilisateur** (seam à inscrire dans `seamAllowed`
à P5, filet de caractérisation d'abord) : colonnes de place `insertable/updatable = false` côté `PatientJpaEntity`,
appel de `PlacementDesPatients` depuis `PatientApplicationService`. Écritures croisées **synchrones dans la transaction
de l'appelant** (ADR 0031) : `PlacementDesPatients` (fiche), `LiberationDesPlaces.siEchue` (enregistrement, J-1),
`ReaffectationEnIsolement.siRisque` (sérologie : passe dans la transaction, corrige B10).
Libération : mouvement `PLACE_LIBEREE` écrit **dans la même transaction** par le port `MouvementsDesPatients` (ACL sur
`mouvement_patient`) ; une transaction **par patient** (B7). L'événement `PlaceLiberee` (`planification.v1`) est
**différé** jusqu'à l'extraction de Patient & couverture : aucune dépendance Modulith ajoutée d'ici là.

## Conséquences
+ B2 corrigé, aucune migration, contrats HTTP inchangés.
− Couplage synchrone legacy → contexte (langage publié uniquement, `FrontieresDesContextesTest`).
⚠ Ordre de verrous centre → patient (ADR 0049) ; goldens `PUT /patients` (placement, partiel, occupé) avant le seam.

## Révision
Extraction du contexte Patient & couverture : migration des colonnes (ADR 0005) et publication de `PlaceLiberee` v1.
