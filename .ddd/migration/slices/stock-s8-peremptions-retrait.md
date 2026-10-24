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

## État de `bc-stock` au 2026-10-24 (après le retrait des reliquats S5 / S6 / S7)

61 fichiers au départ ; il en reste **11** (+ 3 tests), tous encore utilisés hors du module :

| Reste dans `bc-stock`                                                        | Utilisateurs                                                                                               | Remplacement prévu                                                                                                   |
|-------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------|
| `Lot`, `LotRepositoryPort`                                                     | `SeanceDomainService` / `SeanceApplicationService` (bc-seance, plateforme), `ExpirationAlertScheduler`, `LotRepositoryAdapter` | Séance : lecture publiée des lots (`CatalogueDArticles` / `ConsommationsDeSeance`) ; péremptions : `ControlerLesPeremptions` (S8) |
| `StockMovement`, `StockMovementType`, `StockMovementHistoryPort`, `StockMovementRepositoryPort`, `ValorisationStockCalculator`, `PmpCalculator` | `ValorisationArticlesService`, `DirectionStockGroupesQueryService`, `StockMovementRepositoryAdapter` (valorisation historique RG-STK-043, Direction) | Lecture publiée de la valorisation par le contexte (`LecturesDuStock` / `CalculDuPmp`)                                     |
| `StockEventPublisher`, `InventaireEventPublisher`                              | `WebSocketStockEventPublisher`, `StockEvenementsListener`, `WebSocketInventaireEventPublisher`, `RelaiDesInventairesVersWebSocket` | Relais des évènements publiés du contexte (`MouvementsDeStockModifies`, `InventaireModifie`) directement vers le WebSocket   |
| `SeanceBillingStatusPort`                                                      | `SeanceBillingStatusJdbcAdapter` + test de caractérisation Facturation                                    | `StatutDesSeancesLegacy` (contexte) existe déjà : retirer l'adaptateur et adapter le test                            |

`bc-article` (5 fichiers) : `ArticleRepositoryPort` reste utilisé par la Séance (`SeanceDomainService`,
`SeanceRaccourciDomainService`) et `ArticleRepositoryAdapter` ; à remplacer par le catalogue publié du Stock.

**Estimation** : 3 à 4 incréments (événements temps réel ; valorisation ; lots et articles côté Séance ; péremptions
notifiées + suppression des modules et du verrou ArchUnit). Le retrait effectif de `bc-stock` / `bc-article` est la
dernière étape de S8, juste après la bascule de la Séance sur le langage publié du Stock.



