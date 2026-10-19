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

## 1 bis. Slices Comptabilité (contexte `comptabilite`, module `backend/contexts/comptabilite`, ADR 0025)

| Ordre | Slice                                                                | État                 | Remarque                                                                                                                                                                                                                                                                                                                                                                                     |
|-------|----------------------------------------------------------------------|----------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 1     | C1 Référentiel comptable (plan, journaux, compte client des payeurs) | `built` (2026-10-15) | fiche : `slices/comptabilite-c1-referentiel.md` ; écarts décidés KB-CPT-01, 04, 13a                                                                                                                                                                                                                                                                                                          |
| 2     | C2 Mapping et TVA                                                    | `built` (2026-10-15) | fiche : `slices/comptabilite-c2-mapping-tva.md` ; `/mapping`, `/regles-tva` refaites sur place ; tables `mapping_comptable`, `tva_types` (partagée avec la Facturation) ; écarts décidés KB-CPT-21, 16 (400 sur dates inversées et chevauchement), 20 (cache évincé)                                                                                                                         |
| 3     | C3 Écritures et périodes                                             | `built` (2026-10-15) | implémentation dans `contexts/comptabilite`; 102 scénarios passent ; écarts KB-CPT-25 approuvés par l'utilisateur et référencés par six goldens `*_nouveau_code.approved.json` (fiche `slices/comptabilite-c3-ecritures.md`)                                                                                                                                                                 |
| 4     | C4 Modèles et pièces                                                 | `built` (2026-10-15) | routes dans `contexts/comptabilite`, parité 10/10 goldens ; verrous B1/B2 partagés avec le legacy C5/C6 ; nouveau contrôleur activé par `comptabilite.c4.nouveau-code=true` (flag prod laissé à l'équipe) ; fiches : `slices/comptabilite-c4-modeles-pieces.md`, `safety-net/comptabilite-c4-modeles-pieces.md`                                                                              |
| 5     | C5 Ventes et règlements                                              | `built` (2026-10-19) | ADR 0027 : écouteurs après commit, une source par transaction, refus rapportés ; `/rebuild` (continue et rapporte `echecs`) et `/ventes/non-comptabilisees` dans `contexts/comptabilite` ; générateur legacy retiré, ACL transitoire `ComptabiliteApplicationService` jusqu'à C7 ; fiches : `slices/comptabilite-c5-ventes-reglements.md`, `safety-net/comptabilite-c5-ventes-reglements.md` |
| 6     | C6 Comptabilisation du stock                                         | `built` (2026-10-19) | `/stock/synchroniser` et tâche de 03:15 dans `contexts/comptabilite` ; `echecs` (pièces des mois clos, réceptions et sorties sans valeur : KB-CPT-42/44) ; legacy du stock retiré (dont `ComptabiliteRestController`) ; fiches : `slices/comptabilite-c6-stock.md`, `safety-net/comptabilite-c6-stock.md`                                                                                    |
| 7     | C7 Retrait de `bc-comptabilite`                                      | `built` (2026-10-19) | module supprimé (21 classes) ; Absences lisent la TVA par le langage publié `ParametrageFiscal` ; ACL C1/C2/C5, adaptateurs et entités legacy retirés (schéma H2 des écritures et périodes décrit par le contexte) ; suites C1 à C6 sur le contexte via `GenerationDEcritures` ; fiche : `slices/comptabilite-c7-retrait-bc-comptabilite.md`                                                 |

## 1 ter. Slices Facturation (contexte `facturation`, module `backend/contexts/facturation`) — **plan approuvé le

2026-10-19 (utilisateur)** ; Q1 tranchée : la Comptabilité reste propriétaire de `tva_types` (ADR 0030)

Modèle tactique : `.ddd/tactical/facturation.md` (approuvé). Même méthode que GMAO et Comptabilité (ADR 0024) : refonte
sur place, mêmes tables (`tva_types`, `facturation_settings`, `factures`, `facture_lignes`, `facture_sequence`,
`facture_reglements`, colonnes de facturation de `seances`) et mêmes URLs (`/api/v1/tva-types`, `/api/v1/facturation`,
`/api/v1/reglements`), filet de caractérisation vert sur le legacy d'abord. Le module `bc-reglement` rejoint le
contexte `facturation` (agrégat `Règlement` distinct de `Facture`).

| Ordre | Slice                                                                                                                           | Périmètre                                                                                                                                                                                                                                                 | Risque                                                 | Décisions à prendre avant la slice                                                                        |
|-------|---------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|--------------------------------------------------------|-----------------------------------------------------------------------------------------------------------|
| 1     | `built` (2026-10-19, fiche `slices/facturation-f1-referentiel.md`) **F1 Référentiel de facturation** (types de TVA, paramètres) | `GET/POST/PUT/DELETE /tva-types`, `GET/PUT /facturation/settings` ; RG-FAC-001 à 003, 010, 011 ; langage publié `TauxDeTvaApplicable` (lu par Absences, Comptabilité, simulation)                                                                         | faible (paramétrage, pas d'argent émis)                | **Q1** propriétaire de `tva_types` (aujourd'hui écrit par `/tva-types` **et** `/comptabilite/regles-tva`) |
| 2     | **F2 Simulation** (aperçu, exclusion, changement de forfait)                                                                    | `POST /facturation/preview`, `/preview/seances/{id}/exclude`, `/preview/seances/{id}/forfait` ; F1-F4, F11 ; ACL Séance (`SeanceFacturationJdbcAdapter`)                                                                                                  | moyen (écrit l'état et le forfait des séances)         | **Q3** exclusion : motif, réinclusion, part de `ABSENT`                                                   |
| 3     | **F3 Validation et émission**                                                                                                   | `POST /facturation/validate` ; F5-F9, F14-F15 ; numérotation, snapshots, liaison séance → facture, événement `FacturationValidée` (consommé par la Comptabilité via son langage publié)                                                                   | **fort** (argent, preuve, concurrence de numérotation) | **Q6** verrou/contrainte de numérotation et doublons existants ; **Q7** contrat de l'événement            |
| 4     | **F4 Règlements**                                                                                                               | `/reglements` (enregistrement, paiements d'une facture, recherche, tableau de bord, export) ; F12-F13 ; retrait de `bc-reglement`                                                                                                                         | moyen                                                  | **Q4** agrégat Règlement, total signé, montant négatif                                                    |
| 5     | **F5 Consultation et pilotage**                                                                                                 | `GET /facturation/dashboard`, `POST /facturation/synthese/print` ; lectures publiées pour la Direction (`DirectionDashboard/Breakdown/AbsencesQueryService`) et le reporting                                                                              | faible                                                 | —                                                                                                         |
| 6     | **F6 Retrait de `bc-facturation`**                                                                                              | consommateurs passés par le langage publié : Comptabilité (`FacturesAComptabiliserJdbc`, `ReglementsAComptabiliserJdbc`, écouteurs P1/P2), Direction, Stock (`SeanceBillingStatusPort`), Séance, Absences ; garde-fou `FrontieresDesContextesTest` étendu | moyen                                                  | —                                                                                                         |
| 7     | **F7 Clôture mensuelle de facturation** *(nouvelle fonctionnalité, ADR 0009)*                                                   | `PériodeDeFacturation` (F16) : plus d'émission ni d'exclusion après clôture                                                                                                                                                                               | à évaluer                                              | **Q2** préconditions, ordre avec Absences/Stock/Comptabilité, réouverture                                 |

Ordre : F1 avant F2/F3 (taux de TVA figé à l'émission), F2 avant F3 (même calcul), F3 avant F4 (règlement d'une facture
émise), F6 en dernier (comme C7 et G6 temps 2). F7 n'est engagée qu'après décision Q2. Chaque slice applique la
recette du § 3, **y compris la mise à jour du référentiel des règles et la régénération de la documentation
fonctionnelle**.

## 2. Autres contextes (haut niveau, à détailler après `/ddd-tactical <ctx>`)

| Contexte                      | Remarque                                                                                                                                                                                                                           |
|-------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Comptabilité                  | **C1 à C7 livrées : `bc-comptabilite` retiré** (voir 1 bis) ; écrans Angular de régénération (C5) et des échecs du stock (C6) livrés le 2026-10-19 ; reste `/ddd-verify comptabilite` et les décisions ouvertes de `known-bugs.md` |
| Facturation + règlement       | **plan proposé le 2026-10-19 (§ 1 ter, F1 à F7)** ; dépend de la lecture des séances ; TVA, clôture mensuelle (ADR 0009)                                                                                                           |
| Absences patients             | dépend des jours prévus (Planification) et des séances                                                                                                                                                                             |
| Stock (+ article)             | PMP, FEFO ; la recette C6 a révélé KB-STK-01/02 (rejeu du PMP à date égale, dates du jeu de démo), corrigés hors slice : à reprendre dans le filet de la slice Stock                                                               |
| Planification                 | consommera les événements GMAO ; débloquera le retrait des lectures SQL de `gmao_equipements`                                                                                                                                      |
| Patient et couverture, Séance | les plus couplés : en dernier                                                                                                                                                                                                      |

## 3. Recette d'une slice

1. `/ddd-safety-net` : filet de caractérisation vert sur le legacy.
2. Domaine pur (TDD), cas d'usage avec fakes, adaptateurs JPA/JDBC directs sur les tables existantes (nommage de colonnes identique, mapping explicite), contrôleurs dans le module.
3. Suite de caractérisation contre le nouveau code ; chaque écart est soit corrigé (défaut), soit décidé et consigné (ADR 0019) avec un golden `*_nouveau_code`.
4. Retrait du contrôleur/DTO legacy de la slice ; le reste du legacy (domaine, ports, adaptateurs) reste tant qu'un écran legacy le référence ; la liste est dans la fiche de la slice.
5. **Documentation (obligatoire, décision utilisateur du 2026-10-19)** : toute règle de gestion nouvelle ou modifiée
   (écart décidé, correction, nouvelle fonctionnalité, nouvel écran) est inscrite dans
   `docs/reference/regles-de-gestion` (identifiant `RG-XXX-nnn` stable, sources à jour, annexe 16 pour les contrôleurs
   et tâches), puis la documentation fonctionnelle est régénérée
   (`docs/client/tools/build-documentation-fonctionnelle.cjs`). `ReglesDeGestionDocumentationTest` vérifie codes,
   contrôleurs (legacy **et** `backend/contexts`), tâches planifiées et entrées périmées de l'annexe.
6. `state.ts set slices.<slice> built`, puis `/ddd-verify`.

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
