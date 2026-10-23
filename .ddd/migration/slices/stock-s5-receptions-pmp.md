---
slice: stock-s5-receptions-pmp
status: built
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
PostgreSQL 16 jetable (script `stock-pg16-rejeu.sh`, créé).

## Bascule / retour arrière / retrait

Sans façade ; retour arrière = version précédente (aucune donnée nouvelle). Retrait : contrôleur de réception, routes
`pmp-recalc/*`, `BonReceptionService`,
`PmpRecalculationCoordinator`, `PmpEngine` ; `PmpCalculator` legacy retiré quand S6/S7 utilisent `RejeuDuPmp`.

## Dépendances et risques

Dépend de S1 (articles) et S3 (`RECU`). **Risque élevé** : argent (PMP, valorisation comptable C6), concurrence avec le
legacy restant.

## Livraison (2026-10-23)

- **Module** `backend/contexts/stock` : domaine `BonDeReception` / `LigneDeReception` (lignes validées dès la saisie,
  lot rattaché
  à la ligne), ports `BonsDeReception` et `EcrituresDuStock` (lots, mouvements d'entrée, rejeu, verrou des articles,
  gardes
  d'inventaire), cas d'usage `GestionDesReceptions` (création, depuis un bon de commande, brouillon, validation,
  correction),
  `RejeuDuPmp` et `RecalculMassifDuPmp`, adapters JDBC `BonsDeReceptionJdbc` et `EcrituresDuStockJdbc`, contrôleurs
  `ReceptionsControleur` et `RecalculDuPmpControleur` (mêmes routes, rôles et JSON), langage publié
  `MouvementsDeStockModifies` /
  `VerrousDeRecalculModifies` relayé à la plateforme (`StockEvenementsListener` : temps réel du centre et éviction du
  cache de
  valorisation, après la validation de la transaction).
- **Corrections décidées (« oui », 2026-10-23), KB-STK-34 à 45** : 403 si le centre demandé ≠ session ; auteur =
  utilisateur de la
  session ; lignes contrôlées dès la saisie (article, quantité > 0, prix ≥ 0, article, fournisseur et bon de commande du
  centre) ;
  refus en 422 avec codes stables (`RECEPTION_*`, `BON_COMMANDE_*`, `STOCK_INVENTORY_IN_PROGRESS`, `STOCK_PERIOD_CLOSED`
  inchangés) ;
  `bons_reception_lignes.lot_id` renseigné à la validation ; un bon de commande ne passe à `RECU` que s'il est validé
  (ou déjà reçu) ; **verrou de ligne sur `articles` par identifiant croissant** (délai 5 s, puis 409
  `STOCK_ECRITURE_CONCURRENTE`) à la place du verrou
  mémoire ; validation et correction dans une seule transaction ; recalcul massif : une transaction par article.
- **Argent reproduit à l'identique** : sur les 11 scénarios (validation, rejeu du jour d'une sortie, entrée antérieure,
  correction,
  garde d'inventaire, recalcul massif), `lots`, `stock_movements` (`pmp_apres`, valorisation des sorties) et `articles`
  (stock, PMP)
  sont **identiques au legacy** (hors auteur), vérifié fichier par fichier. Écart bénin : le rejeu suit la correction de
  la date seule (le legacy ne rejouait pas), sans effet sur les chiffres des scénarios.
- **Legacy restant verrouillé comme le nouveau code** : `StockMovementRepositoryAdapter.findByArticleOrdered` prend le
  même verrou de
  ligne sur l'article avant de rejouer (sorties et inventaire legacy), donc un rejeu legacy attend un rejeu du contexte
  et inversement.
- **Filet** : 11 scénarios, 162 goldens rejoués sur le nouveau code ; 95 goldens changés (auteur, codes de refus, lot
  rattaché,
  isolation), legacy conservé dans `characterization/stock/referentiel/_ecarts-legacy/receptions/*.legacy.json`.
- **Concurrence** (`ReceptionsConcurrentesIntegrationTest`, trois exécutions vertes) : deux validations simultanées du
  même article (stock et PMP cohérents), deux réceptions croisées (A,B)/ (B,A) sans interblocage, 409 quand une autre
  transaction tient le verrou (rien d'écrit, le bon reste brouillon puis se valide). Rejeu sur PostgreSQL 16 jetable
  (`backend/scripts/stock-pg16-rejeu.sh`, `StockPostgresContractIT`) : **vert** le 2026-10-23, verrou `FOR UPDATE` avec
  délai de requête (409 après 5 s), verrouillages croisés sans interblocage, FEFO et séquence lus par SQL.
- **Tests du module** : +9 (`BonDeReceptionTest`, `RejeuDuPmpTest`), ArchUnit vert.
- **Retrait** : `BonReceptionRestController` et ses DTO (`CreateBonReceptionRequest`, `BonReceptionLigneDto`,
  `FromBonCommandeRequest`), `StockDashboardRestController` (routes `pmp-recalc/*`). **Conservés** :
  `BonReceptionService`,
  `BonReceptionApplicationService`, `PmpRecalculationCoordinator`, `PmpEngine`, `PmpCalculator` et les modèles legacy,
  encore utilisés
  par le jeu de démonstration `StockDemoDataSeeder` et par les sorties et l'inventaire legacy (S6, S7) ; à retirer avec
  elles.
- **Limites** : le suivi des tâches de recalcul et la liste des articles en cours restent **en mémoire de l'instance**
  (consultation
  seulement, aucun verrou n'en dépend) ; la correction d'un bon validé **créé avant S5** (lignes sans `lot_id`) apparie
  lignes et lots par
  article puis par numéro de lot (voir `verifications-production.md` § 29).
