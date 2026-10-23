---
slice: stock-s4-lectures-publiees
status: built
date: 2026-10-23
order: 4
context: stock
sources: [.ddd/tactical/stock.md (§ 6 requêtes, § 7.3), RG-STK-042 à 044, 060, 062, comptabilite-c6-stock.md, ADR 0024, 0034, 0041]
---

# S4 — Lectures publiées (tableau de bord, alertes, valorisation, traçabilité, explication du PMP, opérations pour la Comptabilité)

## Cas d'usage

`ConsulterLeTableauDeBord` (7/30/90 j, top articles), `ConsulterLeStockValorise`, `ConsulterLesAlertes` (péremption ≤ 30
j, rupture, seuil), `TracerUnLot`,
`ExpliquerLePmp` ; **langage publié** `OperationsDeStock` (réceptions, sorties par jour, inventaires, articles sans
compte) fourni par le Stock à la Comptabilité,
qui retire son adaptateur JDBC `OperationsStockJdbcAdapter` (comme ADR 0041 pour les Absences).

## Entrées legacy

`StockDashboardRestController` (91 l.) `/api/v1/stock/dashboard` : `GET /analytics`, `GET /stock-valorise`,
`GET /tracabilite/{lotId}`, `GET /alertes`,
`GET /pmp-explain/{articleId}` (les routes `pmp-recalc/*` vont en S5) ; `StockDashboardService` (240 l.),
`ValorisationStockCalculator` (127 l.),
`ValorisationArticlesService` (cache `CACHE_TTL_STOCK_VALORISATION`), `StockDashboardAdapter` ; Comptabilité :
`OperationsStockJdbcAdapter` (port `OperationsDeStock`).

## Données

Lecture seule de `stock_movements`, `lots`, `articles`, `bons_*`, `inventaires` ; aucune écriture, aucune migration ;
cache invalidé à chaque écriture de mouvement (le legacy écrit encore jusqu'à S5-S7 : garder l'éviction déclenchée par
le legacy).

## Filet (points d'argent § 11.4)

Goldens chiffrés : valorisation de période (début, entrées, sorties, autres variations, fin) sur un jeu couvrant entrée,
sortie, ajustement avec/sans prix,
inventaire ; tableau de bord (jours sans mouvement comblés, top 10/50 par valeur et volume) ; alertes (30 j, ≤ 0,
seuil) ; traçabilité d'un lot jusqu'au patient ;
explication du PMP mouvement par mouvement ; **opérations publiées identiques** aux lectures de C6
(`ComptabilisationDuStockCharacterizationIntegrationTest` vert
sans changement de golden). Reprendre `ValorisationStockCalculatorTest`, `ValorisationArticlesCacheIntegrationTest`.

## Bascule / retour arrière / retrait

Sans façade ; retour arrière = version précédente ; retrait du contrôleur (routes de lecture), `StockDashboardService`,
`ValorisationStockCalculator` et de
`OperationsStockJdbcAdapter` (Comptabilité) en fin de slice.

## Dépendances et risques

Dépend de S1. Risque moyen : chiffres (cache, arrondis) ; la Comptabilité doit rester identique (différentiel 35 j,
RG-CPT-015).

## Livraison (2026-10-23)

- **Module** `backend/contexts/stock` : `ConsulterLeStock` (tableau de bord, stock valorisé, alertes, traçabilité,
  explication du PMP), domaine `CalculDuPmp` + `MouvementDeStock` (rejeu et ordre de la journée), port `LecturesDuStock`
  et adapter `LecturesDuStockJdbc`, contrôleur `LecturesDuStockControleur` (mêmes routes, rôles et JSON), langage publié
  `OperationsDeStock` fourni par `OperationsDeStockJdbc` (requêtes déplacées de la Comptabilité, SQL inchangé).
- **Comptabilité** : l'adaptateur `OperationsDeStockJdbc` est retiré ; l'ACL `OperationsDeStockDuStock` lit le langage
  publié (le module dépend de `contexts-stock`, seul point d'entrée).
  `ComptabilisationDuStockCharacterizationIntegrationTest`
  et `ComptabiliteStockIntegrationTest` restent verts **sans aucun golden modifié**.
- **Corrections décidées (« oui », 2026-10-23)** : 403 si le centre demandé ≠ session, centre facultatif (KB-STK-23,
  24) ;
  paramètres illisibles en 400 (KB-STK-24) ; mouvement sans prix valorisé au PMP du jour du mouvement (`pmp_apres`),
  puis au
  PMP courant (KB-STK-26) ; stock valorisé trié par libellé sans casse ni accents (KB-STK-28) ; article inconnu à
  l'explication du PMP : 422 `ARTICLE_INTROUVABLE` (KB-STK-31, comme le référentiel).
- **Écart avec la décision annoncée sur l'inventaire (KB-STK-32)** : le rejeu de l'explication ignore déjà les
  mouvements
  d'un inventaire clôturé (`inventaire_id` non nul) ; seuls des mouvements `INVENTAIRE` saisis hors inventaire y
  apparaissent. La règle « remise à zéro au premier comptage » n'a donc pas de consommateur dans cette lecture : elle
  est **reportée à la slice S7 (inventaire)**, qui possède ces mouvements ; le comportement actuel est reproduit.
- **Filet** : 10 scénarios, 73 goldens rejoués sur le nouveau code ; 21 goldens changés par les corrections (legacy
  conservé
  dans `characterization/stock/referentiel/_ecarts-legacy/lectures-publiees/*.legacy.json`) ; 52 goldens identiques.
- **Tests du module** : +9 (`CalculDuPmpTest`, `ConsulterLeStockTest`), ArchUnit vert.
- **Retrait** : `StockDashboardService`, `StockDashboardPort`, `StockDashboardUseCase`, `StockDashboardAnalytics`,
  `StockTopSort`, `StockDashboardAdapter`, les routes de lecture de `StockDashboardRestController` (il ne garde que
  `pmp-recalc/*`, slice S5) et `OperationsDeStockJdbc` (Comptabilité). **Conservés** : `ValorisationStockCalculator` et
  `ValorisationArticlesService` (cache), encore lus par la lecture direction des groupes
  (`DirectionStockGroupesQueryService`),
  et les modèles de lecture legacy (`StockTrendPoint`…) à retirer avec S5.
