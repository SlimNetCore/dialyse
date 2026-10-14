---
status: superseded by 0024
date: 2026-10-09
decision-makers: [utilisateur (accord seamAllowed), architecte, experts métier GMAO]
context-scope: slice:gmao-g3-cycle-vie-equipement
research: []
---
# 0022 — Introduire un seam minimal sur l'application de l'état constaté pour que l'équipement n'ait qu'un propriétaire par centre

## Contexte et problème
Quand G3 bascule, le nouveau contexte possède l'équipement du centre. Or tant que G4 n'est pas migrée, le legacy mute encore le statut de l'équipement lors du démarrage/clôture d'une intervention (`InterventionEquipementStatutService.java:49-58`, appelé depuis `InterventionRestController.java:122-161` dans la transaction de l'intervention) et alerte la Planification (`GenerateurIndisponibleService.java:38-41`). Sans traitement, deux écrivains sur le statut d'équipement d'un même centre.

## Options considérées
1. Ne rien faire — contre : viole « un propriétaire par donnée » ; états divergents.
2. Dual write (le legacy écrit aussi dans le nouveau) — interdit.
3. **Seam minimal : extraire un port `EtatEquipementPort` dans `InterventionEquipementStatutService` ; implémentation par défaut = comportement actuel ; implémentation « nouveau » (ACL `legacy-gmao`) appelle `constaterÉtat` du nouveau contexte quand le centre est routé `on` pour G3** — pour : un seul écrivain, rollback par flag ; contre : édition du legacy (hors gel).
4. Faire migrer G4 avant G3 — contre : G4 a besoin de l'équipement (existence, statut) et le risque argent arrive avant l'outillage rodé.

## Décision
Option 3, sous conditions : (a) accord explicite de l'utilisateur (`seamAllowed`) ; (b) filet de caractérisation vert sur `InterventionEquipementStatutService` et les deux appels du contrôleur **avant** l'édition ; (c) le seam ne change aucun comportement quand le flag est `off` (prouvé par les mêmes fichiers approuvés) ; (d) retrait du seam avec G4 (le contrôleur legacy disparaît).
Conséquence acceptée : pour un centre en `on`, l'état est appliqué dans une transaction distincte (cohérence éventuelle, Q3 tactique) ; le refus « équipement réformé » du legacy est reproduit par pré-vérification synchrone (ADR 0019).

## Conséquences
+ propriétaire unique ; rollback par flag. − une modification de legacy tracée, un adaptateur de plus ; ⚠ risque de divergence de transaction : surveillé par le test de parité « démarrer/terminer sur équipement réformé ».

## Révision
Retrait au plus tard à la fin de G4 (2027-12-01) ; si G4 est reportée > 2 mois, réexamen de l'ordre G3/G4.
