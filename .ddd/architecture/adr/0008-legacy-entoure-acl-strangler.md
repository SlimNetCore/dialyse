---
status: approved
amended-by: 0024 (plus de Strangler progressif : refactoring sur place)
date: 2026-10-09
decision-makers: [administrateur, direction technique, architecte]
context-scope: global
research: []
---
# 0008 — Entourer le legacy d'ACL et migrer contexte par contexte (Strangler Fig), sans base partagée en cible

## Contexte et problème
Toutes les entités JPA/JDBC sont dans `platform` sur un schéma unique (124 tables) ; aucun contexte n'est extrait (`backend/contexts` absent). Le modèle legacy contient des termes trompeurs (`ABSENT`, `center`/`centre`, `session`).

## Options considérées
1. Réécriture big bang — contre : interdit (règle legacy-safety), risque financier.
2. Strangler : chaque contexte extrait possède son schéma ; il parle au legacy via une ACL (traduction de modèle) ; la façade de routage bascule les entrées une slice à la fois ; base partagée tolérée **uniquement en transition**, en lecture via ACL — pour : réversible ; contre : double écriture/synchronisation temporaire.
3. Conformiste au modèle legacy — contre : importerait les homonymes.

## Décision
Option 2. Ordre proposé : GMAO → Comptabilité → Facturation → Absences → Stock → Planification → Patient & couverture → Séance (à arbitrer au gate `plan`).

## Conséquences
+ chaque bascule est réversible (feature flag) ; le vocabulaire cible n'est pas contaminé.
− ACL et synchronisation à maintenir jusqu'au retrait.
⚠ chaque accès transitoire à une table legacy est listé dans le plan avec une date de retrait.

## Révision
À chaque slice ; retrait de chaque ACL après la période d'observation définie dans `.ddd/plan/`.
