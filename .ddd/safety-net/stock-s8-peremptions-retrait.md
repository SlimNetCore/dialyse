---
slice: stock-s8-peremptions-retrait
status: built
date: 2026-10-24
sources: [ .ddd/migration/slices/stock-s8-peremptions-retrait.md, RG-STK-043, 060, 061, ADR 0013, 0024, 0045 (décision 8), known-bugs.md KB-STK-53 à 56 ]
---

# Filet de caractérisation — S8 Péremptions, événements publiés et reliquats avant retrait

## Constat préalable (la fiche de slice est en retard sur le code)

`bc-stock` n'existe plus (retiré par les commits ee74b403 / cb422da9) : `Lot`, `StockMovement`, `PmpCalculator`, `StockEventPublisher`,
etc. ont disparu ; la Séance ne lit plus les lots (langage publié `ConsommationsDeSeance`, filet S6b). Restent à retirer :
**`bc-article`** (5 fichiers, utilisé par `SeanceDomainService`, `SeanceRaccourciDomainService`, `SeanceApplicationService`,
`SeanceRaccourciApplicationService`, `ArticleRepositoryAdapter`, `PrescriptionMedicaleResponse`) et le verrou ArchUnit
`FrontieresDesContextesTest`. Le scheduler lit déjà `PeremptionsDuStock` (langage publié) ; `StockEvenementsListener` et
`RelaiDesInventairesVersWebSocket` relaient les événements du contexte ; `StockDemoDataSeeder` écrit par les cas d'usage du Stock.

## Points d'observation et tests (23 scénarios, 4 classes, harnais `ReferentielStockCharacterizationSupport`)

| Cible | Classe de test (`...characterization.stock.referentiel.`) | Observation | Goldens (`src/test/resources/characterization/stock/referentiel/`) |
|---|---|---|---|
| `ExpirationAlertScheduler.scanExpirations` | `ControleDesPeremptionsCharacterizationIntegrationTest` (7) | appel direct ; appender Logback ; SQL `lots`, `notification_*`, `event_publication` | `peremptions/` |
| `StockEvenementsListener`, `WebSocketStockEventPublisher`, `WebSocketInventaireEventPublisher`, `RelaiDesInventairesVersWebSocket` | `EvenementsPublieDuStockCharacterizationIntegrationTest` (8) | HTTP (réception, sortie, correction, inventaire ouvert/clôturé/annulé, recalcul) + événements Spring + messages STOMP (`brokerChannel`) + cache + `event_publication` ; publication directe pour le relais (commit/annulation, valeurs nulles) | `evenements/` |
| `DirectionStockGroupesQueryService`, `ValorisationArticlesService` (RG-STK-043) | `ValorisationDeLaDirectionCharacterizationIntegrationTest` (6) | HTTP `GET /api/v1/direction/stock-groupes`, mouvements SQL à dates absolues (janvier 2026) | `valorisation-direction/` |
| `SeanceRaccourciDomainService`, `SeanceDomainService.addConsommableSeance` (lecture d'`Article`) | `ArticlesDeLaSeanceCharacterizationIntegrationTest` (2) | HTTP `PUT /api/v1/seances/raccourcis-consommables`, `POST /api/v1/seances/{id}/consommables` | `articles-de-la-seance/` |

Déjà verrouillés, non refaits : valorisation (`ValorisationHistorique`) par le filet S4 et la Comptabilité ; cache de valorisation par
`ValorisationArticlesCacheIntegrationTest` ; `StockDemoDataSeeder` par `StockDemoDataSeederIntegrationTest` ; `DirectionRealtimeService`
par `DirectionRealtimeIntegrationTest` ; sorties pour séances (événement `SORTIE`) par `SortiesPourSeancesIntegrationTest`.

## Non-déterminisme

Dates relatives au jour UTC nommées `<J+n>` / `<J-n>` ; le jour est relu autour de chaque exécution du scheduler et le contrôle est rejoué
si minuit UTC passe ; valorisation Direction à dates absolues ; horodatages STOMP `<HORODATAGE>` ; ordre des centres du journal non
garanti (regroupement par centre) ; UUID par alias nommés. Aucun seam.

## Comportements verrouillés

`known-bugs.md` § « Stock S8 » (KB-STK-53 à 56).

## Stabilité

Legacy : 23/23 verts trois fois (un ordre normal, deux ordres aléatoires des classes et des méthodes), puis dans la suite complète
`characterization.stock.referentiel.*` en ordre aléatoire des classes (aucune interférence).

## Couverture et mutation

Branches de `ExpirationAlertScheduler` toutes exercées (centre sans lot / avec lots, borne, doublon, inactif). PIT non exécuté (zone de
20 lignes ; les mutants de bornes sont tués par `bornes_et_exclusions`).

## Limites (non verrouillé)

- Le planificateur n'est pas déclenché par le temps (annotation `@Scheduled` lue par réflexion) ; la date du contrôle (UTC) n'est pas
  injectable : pas de test aux alentours de minuit UTC.
- Pas d'observation d'un déploiement multi-instance (chaque instance journalise).
- Événements : les sorties pour séances (`SORTIE` via `GestionDesSortiesPourSeances`) et `markDirtyForCentre` (temps réel Direction)
  ne sont pas assertés ici ; l'ordre `STARTED` puis `COMPLETED` d'un recalcul dépend d'un fil (attente bornée à 15 s).
- Valorisation : seul le rejeu publié est observé en HTTP ; H2 seulement (pas PostgreSQL).
- Séance : `SeanceApplicationService` n'est exercé que par la route `consommables` (cas d'erreur) ; la validation d'une séance avec
  consommations relève du filet S6b.

## Pour la construction

- `LotProcheExpiration` : ni contrat ni consommateur n'existe ; le golden `journal_par_centre` sert de référence de parité (mêmes lots,
  mêmes bornes) pour `ControlerLesPeremptions`, dont la notification est nouvelle.
- Retrait de `bc-article` : remplacer `ArticleRepositoryPort` par le catalogue publié (`CatalogueDArticles`) ; les goldens
  `articles-de-la-seance/` imposent les codes d'erreur (KB-STK-56) sauf décision contraire.
