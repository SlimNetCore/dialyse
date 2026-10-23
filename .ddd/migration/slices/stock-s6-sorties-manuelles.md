---
slice: stock-s6-sorties-manuelles
status: planned
date: 2026-10-23
order: 6
context: stock
sources: [.ddd/tactical/stock.md (BonSortie, StockArticle.sortirDuLot/restituer), RG-STK-002, 030 à 033, RG-SEA-002, ADR 0024, 0035 (ports publiés de la Facturation), 0045]
---

# S6 — Sorties manuelles (bons de sortie, lots choisis) et lecture des sorties de séance historiques

## Cas d'usage

`SortirDesLots`, `CorrigerUnBonDeSortie`, `ConsulterUnBonDeSortie`, `ListerLesBonsDeSortie`, `ListerLesLotsDisponibles`
(FEFO) ; garde « séance facturée »
pour les bons historiques rattachés à une séance (RG-STK-032).

## Entrées legacy

`BonSortieRestController` (92 l.) `/api/v1/stock/bons-sortie` : `POST`, `PUT /{id}`, `GET /lots-disponibles`,
`GET /{id}`, `GET` ; `BonSortie` (122 l.),
`LigneSortie`, `BonSortieService` (432 l.) méthodes `create`, `update`, `get`, `list` ; `BonSortieApplicationService` ;
**ACL** `SeanceBillingStatusPort` →
`SeanceBillingStatusJdbcAdapter` (lecture SQL de `seances`) remplacée par le langage publié de la Facturation (« séance
facturée »). **Hors périmètre** (S6b) : `addSeanceConsommation`, `setSeanceConsommation`, `annulerSortiesSeance`,
`createViaFefo` et leurs 4 appelants Séance (restent legacy
jusqu'à S6b).

## Données

Tables `bons_sortie`, `bons_sortie_lignes`, `lots`, `stock_movements`, `articles`. Aucune migration. Cohabitation avec
le legacy de séance (S6b) sur les mêmes lots :
même verrou de ligne d'article (voir S5).

## Filet (points d'argent § 11.2)

Goldens : sortie d'un lot (disponible, lot d'un autre article refusé, « Stock insuffisant sur le lot », article
inactif), valorisation au PMP courant, correction (restitution puis nouvelles sorties à la date et à l'heure de création
du bon), bon de séance facturée immuable (`SEANCE_FACTUREE_NON_MODIFIABLE`,
`SEANCE_STOCK_EXIT_DATE_IMMUTABLE`), lots disponibles triés FEFO, rôles (`ADMIN`, `PHARMACIEN`, `INFIRMIER`), autre
centre. Reprendre `BonSortieServiceTest`,
`BonSortieApplicationServiceIntegrationTest`, `BonSortieRestControllerIntegrationTest`.

## Bascule / retour arrière / retrait

Sans façade ; retour arrière = version précédente. Retrait : contrôleur, méthodes manuelles de `BonSortieService`,
`SeanceBillingStatusJdbcAdapter`.

## Dépendances et risques

Dépend de S5 (`StockArticle`, `RejeuDuPmp`) ; port publié de la Facturation. Risque moyen.

