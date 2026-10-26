---
status: proposed
date: 2026-10-26
decision-makers: [utilisateur (décisions 4 et 8 du 2026-10-26), architecte]
context-scope: planification, gmao, organisation (legacy), séance/absences/direction (lecteurs)
research: [.ddd/research/spring-modulith-events-outbox.md]
related: ADR 0008, 0013, 0024, 0034, 0041 ; `.ddd/tactical/planification.md` §7.3-§7.5 ; B8/B11
---
# 0053 — Publier les lectures de la Planification et consommer GMAO et Organisation par ports

## Contexte et problème
Séance, Absences, Patient et Direction lisent en SQL les colonnes de place et `planning_parametres` ; la Planification
lit en SQL `gmao_equipements`/`gmao_interventions` (B11 : son écouteur dépend des classes du domaine GMAO) et écrit
`salle.isolement` (Organisation, B8).

## Options considérées
1. Laisser les lectures SQL croisées — contre : dette de frontière, retrait du legacy bloqué.
2. **Langage publié de requête + ports consommés (ACL)** — pour : précédents Facturation (0034), Absences (0041), Stock.
3. Projections par événements — contre : exclu par ADR 0024.

## Décision
Option 2. `planification.publishedlanguage` (interfaces + records JDK, aucune dépendance) : `PlacesDesPatients`,
`PlacementDesPatients`, `LiberationDesPlaces`, `ReaffectationEnIsolement`, `CapaciteDesCentres`, `FichesDesInfirmiers`.
Ports consommés, adapters dans `infrastructure/acl/<source>` :
- GMAO (évolution acceptée) : `GenerateursDuCentre` existant enrichi de la désignation ; **nouveau** port publié
  `IndisponibilitesDesGenerateurs(centre, du, au)` ; événements **`gmao.v1`** en records JDK dans
  `gmao.publishedlanguage`, écoutés par `@ApplicationModuleListener` dans `infrastructure/acl/gmao` (idempotence :
  alerte rejouée tolérée, P26). Jalon : plus aucun SQL sur `gmao_*` après P5 et P8.
- Organisation (legacy) : port `SallesDuCentre` (lecture + `marquerIsolement`, **même SQL** qu'aujourd'hui), `Creneaux`,
  `CalendrierDuCentre` ; Patient, Dossier médical, Séance, Identité : ACL `infrastructure/acl/legacy*`.
- Absences : lecture publiée existante `absencespatients.publishedlanguage` (dépendance Maven ajoutée à P6 seulement).

## Conséquences
+ Lectures croisées fermées slice par slice (P5, P6, P8) ; `FrontieresDesContextesTest` les verrouille.
− Évolution de GMAO (versionnement de 2 événements, 1 port) à livrer avant P5/P8.
⚠ Écriture de `salle` par la Planification conservée : dette tracée jusqu'à un contexte Organisation.

## Révision
Création du contexte Organisation (propriétaire de `salle.isolement`).
