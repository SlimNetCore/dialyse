---
slice: stock-s5-receptions-pmp
status: planned
date: 2026-10-23
order: 5
context: stock
sources: [.ddd/tactical/stock.md (StockArticle, BonReception, RejeuDuPmp, PeriodeDeStock en lecture), RG-STK-020 à 025, 040, 041, KB-STK-01/02, ADR 0024, 0029, 0043, 0045 (décisions 5 et 6)]
---

# S5 — Réceptions et PMP (argent)

## Cas d'usage

`CreerUnBonDeReception`, `ReceptionnerUnBonDeCommande`, `ModifierLeBrouillon`, `ValiderLaReception`,
`CorrigerLaReception`, `ConsulterUneReception`,
`ListerLesReceptions` ; `RecalculerLePmp` (massif), `SuivreUneTache`, `ConsulterLesVerrous` ; politique
`BonCommandeRecuALaReception`.
Agrégats : `BonReception`, `StockArticle` (entrée, correction d'entrée, rejeu), garde `PeriodeDeStock` (lecture sous
verrou partagé).

## Entrées legacy

`BonReceptionRestController` (76 l.) `/api/v1/stock/bons-reception` : `POST`, `POST /from-bon-commande`, `PUT /{id}`,
`POST /{id}/valider`, `GET /{id}`, `GET` ;
`StockDashboardRestController` : `POST /pmp-recalc/start`, `GET /pmp-recalc/jobs/{jobId}`, `GET /pmp-recalc/locks` ;
`BonReception` (135 l.),
`BonReceptionService` (291 l.), `BonReceptionApplicationService`, `PmpCalculator` (108 l.), `PmpEngine` (82 l.),
`PmpRecalculationCoordinator` (172 l.,
verrou **mémoire** `ConcurrentHashMap` l. 25-26), `Lot` (172 l.), `StockMovement` (280 l., `CHRONOLOGIE`, `saisi_le`
V38), `InventoryGuardedMovementRepository` (115 l.).

## Données

Tables `bons_reception`, `bons_reception_lignes`, `lots`, `stock_movements`, `articles` (`stock`, `pmp`),
`bons_commande` (statut). Aucune migration attendue (verrou de ligne sur `articles` ; `PeriodeDeStock` lue depuis
`inventaires`). Le legacy des sorties (S6) et de l'inventaire (S7) écrit encore les mêmes tables :
**même verrou de ligne pris par le legacy restant** (adapter `LotRepositoryPort`/mouvements legacy pour verrouiller
`articles` FOR UPDATE) sinon le rejeu peut se
croiser avec une sortie legacy — à vérifier dans le filet (test de concurrence) avant la bascule.

## Écarts décidés (ADR 0045)

- Verrou de recalcul : **verrou de ligne** au lieu du verrou mémoire ; une saisie concurrente **attend** (409
  `STOCK_ECRITURE_CONCURRENTE` après 5 s, ADR 0043)
  au lieu de « Recalcul en cours … Saisie temporairement bloquée » ; goldens `*_nouveau_code` pour ce cas.
- Rejeu **synchrone** par article dans la transaction de validation / correction ; recalcul massif : une transaction par
  article.
- Bon entier ou rien, verrous par `ArticleId` croissant.

## Filet (points d'argent § 11.1)

Goldens : validation (lots créés, numéro `AUTO-<réf>-<rang>`, lot géré sans numéro/péremption refusé, quantité ≤ 0),
mouvements `ENTREE` datés du bon,
PMP après entrée (4 déc., demi-supérieur), BC lié `RECU`, correction (même nombre de lignes, même article, quantité ≥
consommé, rejeu complet), rejeu KB-STK-01 (sortie saisie avant une entrée du même jour), sortie jamais revalorisée à 0,
inventaire en cours / période close refusés (`STOCK_INVENTORY_IN_PROGRESS`,
`STOCK_PERIOD_CLOSED`), recalcul massif (tâche, verrous, état), notification temps réel du centre. Reprendre
`PmpCalculatorTest`, `BonReceptionServiceTest`,
`BonReceptionApplicationServiceIntegrationTest`, `LotTest`. Concurrence : deux réceptions croisées (A,B)/ (B,A) sans
interblocage ; réception pendant une sortie legacy.
PostgreSQL 16 jetable (script `stock-pg16-rejeu.sh` à créer sur le modèle de Facturation).

## Bascule / retour arrière / retrait

Sans façade ; retour arrière = version précédente (aucune donnée nouvelle). Retrait : contrôleur de réception, routes
`pmp-recalc/*`, `BonReceptionService`,
`PmpRecalculationCoordinator`, `PmpEngine` ; `PmpCalculator` legacy retiré quand S6/S7 utilisent `RejeuDuPmp`.

## Dépendances et risques

Dépend de S1 (articles) et S3 (`RECU`). **Risque élevé** : argent (PMP, valorisation comptable C6), concurrence avec le
legacy restant.

