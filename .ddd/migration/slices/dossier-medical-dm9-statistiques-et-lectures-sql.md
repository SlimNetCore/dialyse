---
slice: dossier-medical-dm9-statistiques-et-lectures-sql
status: planned
date: 2026-11-07
order: 9
context: dossier-medical
sources: [.ddd/migration/slices/dossier-medical-dm8-lectures-publiees.md, ADR 0087, ADR 0024, ADR 0072, known-bugs.md KB-MED-100, 103, 104, 105]
---

# DM9 : statistiques médicales, indicateurs Direction, lecteurs SQL Planification, `volet_medical`, retrait de `bc-medical`

Reliquat de DM8 (ADR 0087), séparé le 2026-11-07 (revue DM8, constat M2) pour que le périmètre non repris reste suivi. Refactoring SUR PLACE (ADR 0024/0072) : mêmes tables, mêmes URL, mêmes rôles, pas de façade, de flag, de projection ni d'événement inventé.

## Périmètre (rien n'est repris à ce jour)
| Élément | État actuel | À faire |
|---|---|---|
| Statistiques médicales du patient (`stats/medical`, `stats/export` CSV et PDF, POST `search`) : `PatientStatsQueryService`, `PatientStatsRestController` | plateforme ; seule la couche d'erreur est alignée (400, 403 avant 400) | reprise dans le module si une bibliothèque CSV/PDF est décidée (ADR), sinon garde via langage publié ; arbitrer KB-MED-100 (libellé `PATIENTS_CREATION` des POST de recherche) |
| Indicateurs Direction (`/api/v1/direction/indicators`) : `DirectionIndicatorsQueryService`, `AnonymityPolicy`, `DirectionBreakdownQueryService` | plateforme ; 400 et 403 avant 400 alignés | lectures publiées du contexte ; **KB-MED-104 (fuite par différence des totaux) : décision DPO requise** |
| Lecteurs SQL de la Planification : `RisquesInfectieuxJdbc`, `ContraintesDesPatientsJdbc` | SQL direct sur `serologies_patient` et `abords_vasculaires` | remplacer par des lectures publiées (`RisquesInfectieux`, `AbordsVasculaires`) ; garde « pas de SQL sur les tables du Dossier médical hors contexte » |
| Volet médical de la séance (`volet_medical`) | lu ici, écrit par la Séance | décision de propriété avec le contexte Séance |
| KDIGO et PDF de greffe (DM7) | plateforme, garde seulement | déplacer la garde de patient dans un cas d'usage ; reprise ou lecture publiée |
| Retrait de `bc-medical` | liste nominative et jalons : ADR 0087 | supprimer `Antecedent`, `Serologie`, greffe, anémie, KDIGO, `ResultatAnalyse*` (JPA, dépôt, `ResultatAnalyseSpecifications`) quand leurs lecteurs sont repris ; réécrire la garde de tables `TableDuDossierMedicalTest` (toujours `@Disabled`) ; remplacer les entités JPA qui créent les tables H2 par un schéma de test |

## Étapes (ordre du pipeline)
1. Filet : les 38 scénarios DM8 (stats, Direction) restent le filet ; ajouter les scénarios des lecteurs Planification s'ils manquent (`/ddd-safety-net`).
2. Décisions DPO et métiers : KB-MED-104, KB-MED-100, KB-MED-101 et KB-MED-102 (déjà conditions de mise en production de DM8).
3. Reprise puis retrait ; revue au plus tard le **2026-12-15** (verrou d'architecture `plus_aucun_appelant_des_ecritures_legacy_du_dossier_medical`).

Prochaine commande : `/ddd-slice dossier-medical-dm9-statistiques-et-lectures-sql` après décision sur les points de l'étape 2.
