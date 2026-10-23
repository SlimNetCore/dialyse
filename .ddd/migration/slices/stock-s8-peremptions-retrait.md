---
slice: stock-s8-peremptions-retrait
status: planned
date: 2026-10-23
order: 9
context: stock
sources: [.ddd/tactical/stock.md (§ 7.2, ControleDesPeremptions), RG-STK-060, 061, ADR 0013, 0024, 0045 (décision 8)]
---

# S8 — Péremptions notifiées, événements publiés et retrait de `bc-stock` / `bc-article`

## Cas d'usage

`ControlerLesPeremptions` (07:00, **centres actifs seulement**) avec **notification** (pharmacien, admin) ; publication
par l'Outbox des événements d'intégration
`ReceptionValidee`, `SortiePourSeancesEnregistree`, `RapprochementValide`, `PmpRecalcule`, `InventaireCloture`,
`LotProcheExpiration` (contrats v1, consommateurs
idempotents) ; retrait des modules legacy.

## Entrées legacy

`ExpirationAlertScheduler.scanExpirations` (journal technique, parcourt aussi les centres inactifs) ;
`StockEventPublisher` / `WebSocketStockEventPublisher`
(temps réel, conservé) ; `bc-stock` (61 fichiers), `bc-article` (6 fichiers), `StockDemoDataSeeder` ; Direction
(`DirectionRealtimeService`).

## Données

Aucune migration (registre Outbox existant, ADR 0013). Notifications : service de la plateforme.

## Filet

Goldens : contrôle des péremptions (30 j, stock > 0, centres actifs, notification une fois par lot et par jour) ;
contrats des événements (payload, version) ;
consommateurs actuels inchangés (Comptabilité continue de lire `OperationsDeStock` tant qu'elle n'est pas passée aux
événements).

## Retrait (liste de décommissionnement, validée par l'utilisateur)

Modules `bc-stock`, `bc-article` ; tables inchangées ; `FrontieresDesContextesTest` étendu (plus aucun import
`domain.stock` / `domain.article` hors du contexte).

## Dépendances et risques

Dépend de S1 à S7. Risque moyen (notification : volumétrie et doublons).

