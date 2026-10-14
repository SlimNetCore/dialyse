---
status: approved
date: 2026-10-11
sources: [.ddd/architecture/adr/0024-refactoring-sur-place-de-gmao-abandon-du-strangler-progressif.md, .ddd/tactical/gmao.md, .ddd/scan/summary.md]
---
# Roadmap — refactoring sur place par slice (ADR 0024)

Approche : chaque slice est refaite **sur place** dans `backend/contexts/<contexte>`, avec le **même stockage** (mêmes tables, mêmes colonnes) et les **mêmes URLs** que le legacy. Le filet de caractérisation (golden masters HTTP) est écrit et vert sur le legacy d'abord ; la même suite doit rester verte sur le nouveau code. Livraison en une fois : pas de façade, pas de routage par centre, pas de projection, pas de double propriétaire des données. Retour arrière = redéploiement de la version précédente.

Règles : aucun `DROP`/`DELETE`/`TRUNCATE` sur le schéma legacy ; pas de nouvelle migration pour une slice qui réutilise les tables ; bugs legacy reproduits (ADR 0019) sauf écart décidé et tracé ; code legacy retiré seulement quand plus aucun consommateur ne le référence.

## 1. Slices GMAO

| Ordre | Slice | État | Remarque |
|---|---|---|---|
| 1 | G1 Plans de maintenance préventive | `built` | fiche : `slices/gmao-g1-plans-maintenance.md` |
| 2 | G2 Intervenants (référentiel + tarif) | `built` | fiche : `slices/gmao-g2-intervenants.md` |
| 3 | G3 Cycle de vie de l'équipement | `built` | fiche : `slices/gmao-g3-cycle-vie-equipement.md` ; publie les événements du parc, consommés par un écouteur de platform (alerte Planification ; le cache `ref.generateurs` est évincé de façon synchrone par le contrôleur) |
| 4 | G4 Interventions (cycle, coûts, tâches) | `built` (temps 1 et 2) | fiche : `slices/gmao-g4-interventions.md` ; temps 1 (2026-10-12) : les neuf routes existantes refaites sur place, événement `InterventionTerminee` par l'outbox. Temps 2 (2026-10-12, nouvelles fonctionnalités demandées par l'utilisateur, à valider par le métier) : tâches d'intervention (KB-GMAO-55, migration additive V36) et notification de proposition de réforme (KB-GMAO-57). À venir : corrections des écarts reproduits (KB-GMAO-44, 46, 42), dates à fixer par l'utilisateur. Les lecteurs d'interventions (suivi, bon, fiche équipement, statistiques, direction) restent sur le legacy jusqu'à G6 |
| 5 | G5 Pièces jointes d'intervention | `built` | fiche : `slices/gmao-g5-pieces-jointes.md` ; les quatre routes `/documents` refaites sur place (2026-10-12), octets toujours en base ; **écarts décidés** : structure du fichier et extension vérifiées (KB-GMAO-59), nom assaini (KB-GMAO-60), nom et type re-contrôlés à la lecture (KB-GMAO-61), ajout et retrait refusés sur une intervention annulée, terminée ou supprimée (KB-GMAO-64, 422), quota de 50 tenu sous concurrence (KB-GMAO-58) ; événements nouveaux `PieceJointeAjoutee` et `PieceJointeRetiree` ; tout le code legacy des documents est retiré |
| 6 | G6 Consultation et pilotage GMAO | `built` (temps 1 et 2, 2026-10-14) | fiche : `slices/gmao-g6-consultation-pilotage.md` ; fiche équipement, disponibilité pour un patient, indicateurs, chronologie, bon (rendu par un port, moteur de documents dans la plateforme), statistiques, source GMAO de la direction (`SyntheseGmaoDuCentre`, `GenerateursInstalles`) refaits sur place le 2026-10-14, 59/59 scénarios verts ; **écarts décidés** : KB-GMAO-69 (intervention supprimée non lisible), KB-GMAO-72 (seuil sur ratio exact), KB-GMAO-73 (désactivé non disponible) ; temps 2 : `bc-gmao` retiré, port publié `GenerateursDuCentre`, KB-GMAO-80 à 83 reproduits (correction à planifier) |

L'ordre suit les dépendances : G2 avant G4 (tarif), G3 avant G4 (état de l'équipement), G4 avant G5 et G6.

## 2. Autres contextes (haut niveau, à détailler après `/ddd-tactical <ctx>`)

| Contexte | Remarque |
|---|---|
| Comptabilité | argent, aucun test sur les services : filet de caractérisation long ; écritures exportées immuables |
| Facturation + règlement | dépend de la lecture des séances ; TVA, clôture mensuelle (ADR 0009) |
| Absences patients | dépend des jours prévus (Planification) et des séances |
| Stock (+ article) | PMP, FEFO |
| Planification | consommera les événements GMAO ; débloquera le retrait des lectures SQL de `gmao_equipements` |
| Patient et couverture, Séance | les plus couplés : en dernier |

## 3. Recette d'une slice

1. `/ddd-safety-net` : filet de caractérisation vert sur le legacy.
2. Domaine pur (TDD), cas d'usage avec fakes, adaptateurs JPA/JDBC directs sur les tables existantes (nommage de colonnes identique, mapping explicite), contrôleurs dans le module.
3. Suite de caractérisation contre le nouveau code ; chaque écart est soit corrigé (défaut), soit décidé et consigné (ADR 0019) avec un golden `*_nouveau_code`.
4. Retrait du contrôleur/DTO legacy de la slice ; le reste du legacy (domaine, ports, adaptateurs) reste tant qu'un écran legacy le référence ; la liste est dans la fiche de la slice.
5. `state.ts set slices.<slice> built`, puis `/ddd-verify`.

## 4. Indicateurs

| Indicateur | Source | Cible |
|---|---|---|
| Scénarios de caractérisation verts | suites `characterization/gmao/*` | 100 % |
| Violations d'architecture | `check-arch.ts`, ArchUnit | 0 |
| Fichiers legacy GMAO restant à retirer | listes des fiches | 0 (atteint, G6 temps 2) |
| Bugs legacy ouverts / corrigés | `.ddd/safety-net/known-bugs.md` | tous datés |

## Notes pour plus tard
- **Verrou optimiste (décision utilisateur 2026-10-11)** : G1 et G2 n'en ont pas (les tables legacy n'ont pas de colonne `version`, la dernière sauvegarde gagne, comme avant). À prévoir pour les modules sensibles où la concurrence risque d'écraser une donnée déjà écrite (interventions G4 et valorisation, stock, comptabilité, facturation) : colonne `version` par migration additive + `@Version` + réponse 409. Le legacy restant ne l'incrémenterait pas : à traiter module par module.

## Questions ouvertes
1. Y a-t-il des consommateurs externes de `/api/v1/gmao/**` hors frontend (BI, intégrations) ?
2. Les experts préfèrent-ils sécuriser l'argent (Comptabilité, Facturation) avant G3-G6 ?
