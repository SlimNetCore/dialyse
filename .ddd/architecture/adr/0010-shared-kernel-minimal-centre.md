---
status: approved
date: 2026-10-09
decision-makers: [architecte, équipes de tous les contextes]
context-scope: global
research: []
---
# 0010 — Limiter le shared kernel aux identifiants de centre et de société et au mois

## Contexte et problème
`CenterId` est importé par 321 fichiers et `PagedResult` par 203 ; l'isolement par centre (RG-TRV-002) est transverse. `shared-kernel` existe déjà (106 tests).

## Options considérées
1. Shared kernel large (Patient, Séance, Money, DTO paginés…) — contre : modèle canonique, couplage fort.
2. Shared kernel minimal : `CentreId`, `SocieteId`, `Mois` (période mensuelle) et la garde d'isolement par centre ; `PagedResult` relégué à l'infrastructure ; autres identifiants (`PatientId`, `SeanceId`) publiés comme Published Language des contextes propriétaires.

## Décision
Option 2. Identifiant cible francisé `CentreId` (ACL : `center` legacy → `centre`).

## Conséquences
+ changement du kernel = gouvernance commune rare.
− renommage `CenterId` → `CentreId` à faire progressivement via ACL.

## Révision
Toute addition au kernel exige un nouvel ADR.
