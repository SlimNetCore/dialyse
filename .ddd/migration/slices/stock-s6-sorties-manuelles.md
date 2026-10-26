---
slice: stock-s6-sorties-manuelles
status: built
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

Dépend de S5 (`StockArticle`, `RejeuDuPmp`) ; port publié de la Facturation. Risque moyen.## Livraison (2026-10-23)

- **Module** `backend/contexts/stock` : domaine `BonDeSortie` / `LigneDeSortie` (immuables, correction = nouveau bon de
  même identité,
  référence et heure de saisie), ports `BonsDeSortie`, `SortiesDuStock` (lots FEFO, consommer / restituer, mouvement de
  sortie,
  retrait d'un mouvement) et `StatutDesSeances`, cas d'usage `GestionDesSorties` (sortir, corriger, consulter, lister,
  lots
  disponibles), adapters JDBC `BonsDeSortieJdbc` et `SortiesDuStockJdbc`, ACL `StatutDesSeancesLegacy` (lecture de
  `seances.statut`), contrôleur `SortiesControleur` (mêmes routes, rôles `ADMIN`/`PHARMACIEN`/`INFIRMIER` et JSON).
  Verrou de ligne sur `articles` par identifiant croissant, garde d'inventaire et rejeu du PMP réutilisés de S5 ;
  validation et correction
  dans une seule transaction ; notification temps réel `SORTIE` / `CORRECTION`.
- **Reproduit à l'identique** : refus (400 `BAD_REQUEST`, 422 `BUSINESS_RULE_VIOLATION` « Stock insuffisant sur le
  lot », codes
  d'inventaire, `SEANCE_FACTUREE_NON_MODIFIABLE`, `SEANCE_STOCK_EXIT_DATE_IMMUTABLE` avec son titre), valorisation au
  PMP courant,
  ordre des contrôles, lots FEFO (périmés inclus), article inactif accepté, **création sans contrôle de facturation de
  la séance**
  (reproduit ; **décision métier à prendre**, voir `known-bugs.md`, « Décisions à prendre » D1), lots, mouvements et stock des articles. 74 goldens inchangés sur 126.
- **Corrections (même ligne que S5), KB-STK-46 à 49, désormais consignées dans `known-bugs.md` (confirmation demandée : D2)** : 403 si le centre demandé ≠ session et centre
  facultatif (= session) ;
  auteur = utilisateur de la session (le champ `userId` du corps est ignoré) ; **la correction d'un bon manuel ne
  supprime plus les
  mouvements des autres bons** du même article (le legacy supprimait tous les mouvements « séance 0 / article »,
  laissant le stock
  de l'article décalé des lots : 48 au lieu de 46 dans le scénario des rôles) — le mouvement retiré est celui de la
  ligne (séance,
  article, lot, quantité, heure de saisie du bon) ; quantités relues en base (`1.00` au lieu de `1`). Legacy conservé
  dans
  `characterization/stock/referentiel/_ecarts-legacy/sorties/*.legacy.json`.
- **Port publié de la Facturation** : aucun langage publié n'expose encore « séance facturée » ; l'ACL
  `StatutDesSeancesLegacy` lit le
  même statut que `SeanceBillingStatusJdbcAdapter` (conservé pour les sorties de séance legacy, S6b). À remplacer par le
  langage publié
  de la Facturation quand il existera.
- **Tests** : filet `SortiesManuellesCharacterizationIntegrationTest` 9/9 sur le nouveau code (assertion du nombre de
  bons corrigée
  à 3, conforme au golden legacy) ; +13 tests de module (`BonDeSortieTest`, `GestionDesSortiesTest`), ArchUnit vert ;
  `BonSortieRestControllerIntegrationTest` adapté à un utilisateur rattaché à un centre. Concurrence
  (`SortiesConcurrentesIntegrationTest`) : deux sorties simultanées d'un même lot ne dépassent pas son stock, et une
  écriture legacy qui tient le verrou de l'article donne un 409 sans rien écrire. Rejeu PostgreSQL 16 jetable
  (`stock-pg16-rejeu.sh`) **vert** le 2026-10-23.
- **Retrait** : `BonSortieRestController` et ses DTO (`CreateBonSortieRequest`, `UpdateBonSortieRequest`,
  `SortieItemDto`). **Conservés** (utilisés par les sorties de séance, S6b) : `BonSortieService` (dont `create`,
  `update`, `get`, `list`, à
  retirer avec S6b), `BonSortieApplicationService`, `SeanceBillingStatusPort` et son adapter JDBC.
