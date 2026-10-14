---
status: approved
date: 2026-10-09
slice: gmao-g6-consultation-pilotage
order: 6 (score 19/30, ordonnée après G4/G5 par dépendance)
sources: [.ddd/tactical/gmao.md (§6 read models, R27, R29, R30, Q5), ADR 0018-0021, query.ts entrypoints, grep ciblé]
---

> **État (2026-10-14)** : **slice livrée (`built`)**. Temps 1 : lectures dans `contexts/gmao`, 59/59 scénarios verts ; temps 2 : module `bc-gmao` retiré (voir « Temps 2 »). Le détail du temps 1 est dans « Refonte sur place : état final du temps 1 ».

> **Note ADR 0024 (2026-10-11)** : le Strangler progressif est abandonné. Les mentions de façade, routage par centre, projection, rattrapage, shadow, paliers et rollback à chaud de cette fiche sont caduques : la slice sera refaite sur place (mêmes tables, mêmes URLs, filet de caractérisation puis livraison en une fois). Voir `.ddd/architecture/adr/0024-*.md` et `.ddd/migration/roadmap.md`.
# G6 — Consultation et pilotage de la maintenance (fiche équipement, indicateurs, tableau de bord)

**Comportement observable** : l'administrateur consulte la fiche d'un équipement (statut, indisponibilité, coûts, aide à la décision de réforme), le bon et la chronologie d'une intervention, les statistiques GMAO et les interventions/plans en retard.

| Élément | Valeur |
|---|---|
| Contexte cible | `gmao` (read models, sans agrégat) |
| Use cases | Q: FicheÉquipement (RG-GMA-061) · IndicateursDIntervention (RG-GMA-031) · Chronologie · BonDIntervention · InterventionsEnRetard · PlansEnRetard · TableauDeBordGmao · DisponibilitéPatient (équipement) |
| Services de domaine | `CalculDIndisponibilité` (R29), `AnalyseDeDécision` + `SeuilDeRéforme` (R30, Q5 : seuil global conservé) |

## Entrypoints legacy
`EquipementFicheRestController` : GET `/{id}/fiche` `:61`, `/{id}/disponibilite-patient` `:96` · `InterventionSuiviRestController` : `/bon` `:58`, `/indicateurs` `:79`, `/chronologie` `:85` · `GmaoStatsRestController` GET `/api/v1/gmao/stats` (`:25-60`) · listes en retard (G1 `/plans-maintenance/en-retard` reste porté par G1 ; interventions en retard dans `InterventionRestController` liste) · `DirectionDashboardRestController.java:116` GET `/gmao` (périmètre **Pilotage direction** : seulement l'adaptation de sa source ; le endpoint reste legacy jusqu'à l'extraction de Direction). Domaine legacy : `IndisponibiliteCalculator.java:42` et `AideDecisionMaintenance.java` (tous deux testés), `IndicateursIntervention.java`.

## Seams
`gmao↔stock` vide. Les contrôleurs legacy utilisent les ports `PlanMaintenanceRepositoryPort` (`EquipementFicheRestController.java:76`, `GmaoStatsRestController.java:55-56`) : après G6 plus aucun lecteur legacy des plans ne subsiste → fin de la projection plans.

## Consommateurs implicites
Dashboard direction `DirectionGmaoQueryService` (SQL `gmao_equipements` `:152` + tables d'interventions) : reste lecteur SQL de la projection jusqu'à l'extraction de Pilotage direction ; PDF `DirectionReportPdfService` (417 l., non testé) ; front `gmao-api.service.ts`. À l'extraction de Direction, ces lectures passent à `gmao.v1` (`InterventionTerminée`, `ÉquipementRéformé`).

## ACL
Aucune nouvelle ACL de données : les read models lisent le schéma `gmao` (propriétaire). ACL sortante déjà posée : projection vers legacy (équipements, interventions, plans, documents) **maintenue** pour `DirectionGmaoQueryService`.

## Données
Pas de nouvelle propriété ; vérification : jeux de comparaison fiche/indicateurs sur tous les équipements d'un échantillon de centres (indisponibilité sur 12 mois, ratio de maintenance à 4 décimales, coût/heure). Arrondis : parité stricte.

## Façade, paliers, shadow
Lecture seule → shadow complet (comparaison JSON) et paliers par **% de requêtes en plus des centres** (la lecture ne change aucune propriété) ; ADR 0021, observation 30 j.

## Filet et bugs
Caractérisation : fiche (équipement actif/réformé/sans prix), indicateurs plafonnés à 100 %, chronologie, bon (PDF : gabarit `BonInterventionReportTemplateTest` existe). KB-GMAO-05 : seuil global reproduit (par centre = évolution après retrait). Mutation ≥ 80 % sur les services de domaine.

## Critères de bascule / rollback
ADR 0021 + 0 écart sur ratios/durées (tolérance 0 ; arrondi HALF_UP) sur 100 % des fiches échantillonnées ; p95 fiche ≤ 1,2 × legacy (agrégations plus lourdes à surveiller, index `V31__index_performance`). Rollback : routage `off` (lecture, sans gel).

## Retrait
Observation 30 j (2027-12-13 → 2028-01-12) → code legacy G6 + `bc-gmao` restant (hors lecteurs de projection) **2028-02-14** ; **projections plans, interventions, documents, intervenants retirées 2028-02-14** ; projection équipements + ACL `legacyplanification` + dernier code `bc-gmao` : **2028-06-30**.

## Dépendances
Après : G1, G3, G4, G5 (lit tous les agrégats). Avant : retrait de `bc-gmao`. Bloquée par rien d'autre ; DirectionGmao lit encore la projection.

## Risques
Perfs des agrégations ; périmètre « dashboard direction » qui déborde sur un autre contexte (limité à la source) ; PDF non testé (comparaison visuelle/texte).

---

# Refonte sur place : état final du temps 1 (2026-10-14)

> Les sections ci-dessus datent du Strangler abandonné (ADR 0024) : façade, routage, projection, rattrapage, shadow, paliers, rollback, métriques et dates de retrait y sont **caduques**. Seul ce qui suit fait foi.

## État final
État du temps 1 (`characterized` alors) ; la slice est `built` depuis le retrait de `bc-gmao` (temps 2). Backend complet vert (`./mvnw -o test` : module `contexts/gmao` 740 tests, platform 1184 dont 4 ignorés hors G6), `check-arch.ts` : 0 violation, G1 (36), G2 (39), G3 (54), G4 (67), G5 (48) inchangés et verts, **G6 : 59 scénarios de caractérisation sur 59 verts contre le nouveau code** (BonPdf 16, DirectionGmao 11, FicheEquipement 13, IndicateursChronologie 14, StatsGmao 5), dont **5 corrigés** (goldens `*_nouveau_code`). Frontend non modifié. Aucune migration, aucun flag, aucune façade : mêmes tables, mêmes URLs, mêmes DTO JSON.

## Réalisation (`backend/contexts/gmao`)
| Couche | Fichiers |
|---|---|
| domaine | `domain/equipement/CalculDIndisponibilite` (RG-GMA-060), `AnalyseDeDecision` (seuil, RG-GMA-062/063), `domain/intervention/IndicateursDIntervention` (RG-GMA-031), `EvenementDeChronologie` ; `StatutEquipement.disponiblePourUnPatient` (RG-GMA-066) |
| application | `equipement/` : `ConsulterLaFiche`, `FicheDEquipement`, `VerifierLaDisponibilite`, `DisponibilitePourUnPatient`, port `HistoriqueDesStatuts`, `LectureEquipements.parcDuCentre` ; `intervention/` : `ConsulterLesIndicateurs`, `ConsulterLaChronologie`, `EvenementVu`, `ImprimerLeBon`, `DonneesDuBon`, `DocumentImprime`, ports `ImpressionDuBon` (rendu) et `NomsDesUtilisateurs` ; `pilotage/` : `ConsulterLesStatistiques`, `StatistiquesGmao`, `SynthetiserLeCentre`, `ComptageDesGenerateurs`, ports `LectureDesComptages`, `LectureDesCouts` |
| langage publié | `publishedlanguage/SyntheseGmaoDuCentre` (synthèse de la période et analyse de chaque équipement), `GenerateursInstalles` (comptage pour la capacité) |
| infrastructure | `persistence/HistoriqueDesStatutsJpa`, `LectureDesComptagesJpa`, `LectureDesCoutsJpa` (sommes calculées par la base), requêtes ajoutées à `EquipementSpringData`, `InterventionSpringData`, `LigneDeCoutSpringData`, `EquipementStatutHistoriqueSpringData` ; `acl/legacyidentite/NomsLegacy` (nom complet ou identifiant de connexion) |
| interfaces | `interfaces/http/SuiviControleur`, `SuiviReponses` (mêmes URLs, mêmes corps et codes que le legacy) |
| configuration | `GmaoConfiguration` : 13 beans ; `gmao-defaults.properties` : `hemodialyse.gmao.reforme-seuil-ratio=0.60` |
| plateforme (câblage et seams) | `BonInterventionReportService` devient l'adaptateur du port `ImpressionDuBon` (moteur de documents, modèle du centre, fuseau) ; `DirectionGmaoQueryService` lit `SyntheseGmaoDuCentre` (modification minimale : la garde de société, les alertes, la requête des patients sur équipement indisponible et l'endpoint restent en place) ; `DirectionCapaciteQueryService` lit `GenerateursInstalles` |
| tests (module) | domaine : `AnalyseDeDecisionTest`, `CalculDIndisponibiliteTest`, `IndicateursDInterventionTest`, `ChronologieDInterventionTest` ; application : `CasDUsagePilotageTest` (19, doubles `HistoriqueEnMemoire`, `PilotageEnMemoire`, `NomsEnMemoire`, `ImpressionEnMemoire`) ; contrats : `PilotageContrat` (11) contre les doubles et les adapters JPA/H2, et `PilotagePostgresContractIT` |
| tests (platform) | `BonInterventionReportTemplateTest` adapté aux données du module ; 59 scénarios de caractérisation `pilotage/` |

Choix : aucun agrégat (lectures seules, ADR 0024) ; les trois calculs sont des fonctions pures sans état ; le bon ne contient pas de mise en page (celle-ci reste dans la plateforme, qui possède le moteur de documents partagé).

## Écarts de comportement décidés (utilisateur, 2026-10-12)
Détail, ancien comportement et impacts : `.ddd/safety-net/known-bugs.md`, « Décisions G6 ». Goldens `*_nouveau_code` : `indicateurs_erreurs`, `chronologie_erreurs`, `fiche_ratio_et_seuil_de_reforme`, `disponibilite_patient_par_statut` (+ l'assertion du bon d'une intervention supprimée).
- **KB-GMAO-69** : une intervention supprimée logiquement n'est plus lisible par identifiant (bon, indicateurs, chronologie : 400).
- **KB-GMAO-72** : le seuil de réforme (0,60) est comparé au ratio exact ; l'arrondi à 4 décimales ne sert qu'à l'affichage.
- **KB-GMAO-73** : un équipement « Désactivé » n'est plus disponible pour un patient.
- Reproduits : KB-GMAO-70 (GET /bon écrit en base), 71 (fiche sans filtre de centre), 74, 75, 76 (échelle JSON), 77, 78, 79 (événement MODIFIEE parasite), codes 400/403/500, seuil global (KB-GMAO-05).

## Mutation et PostgreSQL 16
`backend/scripts/pit-g6-calculs.sh` : **44 mutants, 38 tués (86 %), force des tests 86 %, aucun mutant sans couverture** (ligne : 51/55) sur `AnalyseDeDecision`, `CalculDIndisponibilite`, `IndicateursDIntervention`, `EvenementDeChronologie`, par les tests unitaires de domaine et de cas d'usage (cible ≥ 70 %).
`backend/scripts/gmao-pg16-rejeu.sh <port>` rejoue aussi `PilotagePostgresContractIT` (sommes de coûts, comptages, historique, générateurs installés) :
**PostgreSQL 16 réel** : `backend/scripts/gmao-pg16-rejeu.sh 55437` (cluster jetable, port 55437, jamais 5432, détruit en sortie, log en arrière-plan) : **code de sortie 0**. `PilotagePostgresContractIT` : 12 tests verts (les 11 du contrat partagé, dont les sommes de coûts quantité fois prix, les comptages et les générateurs installés, plus la version du serveur) ; contrats PG du module : 105 verts ; suites HTTP G1, G2, G3 rejouées sur PG16 : 129 verts. La suite HTTP de G6 reste sur H2 (ses fixtures sèment en SQL sans clés étrangères).

## Temps 2 : `bc-gmao` retiré (2026-10-14)
Le module `backend/bc-gmao` est **supprimé** (25 classes, 5 tests ; poms `backend`, `platform`, `bc-migration`, `Dockerfile`). Ce qui l'a rendu possible :
| Consommateur | Avant | Après |
|---|---|---|
| Reprise des patients (`PatientMigrator`) | `EquipementRepositoryPort` de `bc-gmao` | port propre `GenerateursDeReprise` dans `bc-migration`, adapté par l'ACL `GenerateursDeRepriseGmao` (`platform`) sur le nouveau port publié `GenerateursDuCentre` |
| Alerte « générateur indisponible » | `Equipement` reconstruit à 18 arguments | `GenerateurIndisponibleService.signaler(type, centre, id, code, statut, précédent)` ; l'écouteur lit les valeurs dans l'événement du module |
| Fixtures G1/G2/G3 | ports et modèles `bc-gmao` | `INSERT` SQL ; l'indisponibilité du test G3 est recalculée par `CalculDIndisponibilite` du module |
| Alignement des modèles | entités legacy vs modèle du module | modèle du module vs description figée de la table |

Supprimés dans `platform` : 5 adaptateurs, 7 entités, 7 dépôts JPA legacy, `InterventionResponse`, `LigneCoutResponse`, `TerminerInterventionRequest`, 2 tests devenus morts (`InterventionCostQueriesIntegrationTest`, `GmaoUtcSerializationIntegrationTest`) et `scripts/pit-g3-equipement.sh`. Conservé : le littéral `domain.gmao.model…` du détail de trois erreurs 400 (parité).

Nouveau port publié `GenerateursDuCentre` (id, code, salle, statut, centre ; tous statuts, supprimés exclus, par code), implémenté sur `gmao_equipements` comme `GenerateursInstalles`, qui n'est pas élargi. Contrat partagé `PilotageContrat` (12 scénarios) : double, JPA/H2, PG16.

Parité : filet temps 2 (`.ddd/safety-net/gmao-g6-temps2-bc-gmao.md`) 10 + 8 + 4 verts sans changer de golden. **KB-GMAO-80, 81, 82, 83 reproduits** à l'identique (décision utilisateur) ; **correction à planifier** après retrait.
Vérifs finales (temps 2) : `./mvnw -o clean test` complet vert (contexts/gmao 742, platform 1194 dont 4 ignorés, bc-migration verts), `check-arch.ts` 0 violation, rejeu PostgreSQL 16 jetable (`gmao-pg16-rejeu.sh 55439`, code 0 : contrats PG du module 106 verts dont `PilotagePostgresContractIT` avec `GenerateursDuCentre`, suites HTTP G1 à G3 sur PG16 129 verts avec fixtures SQL).

## Données et plan de bascule
Mêmes tables, mêmes URLs, aucune migration, aucune copie : pas de plan de bascule (ADR 0024). Retour arrière = revenir au commit précédent. Le GET du bon écrit toujours la ligne `modele_document` du centre à la première impression (KB-GMAO-70).
