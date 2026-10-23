---
slice: stock-s4-lectures-publiees
status: planned
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

