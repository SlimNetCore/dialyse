---
slice: stock-s3-bons-commande
status: built
date: 2026-10-23
order: 3
context: stock
sources: [.ddd/tactical/stock.md (BonCommande), RG-STK-005, 010 à 012, ADR 0024, 0045 (ANNULE retiré)]
---

# S3 — Bons de commande

## Cas d'usage

`CreerUnBonDeCommande`, `RemplacerLesLignes`, `ValiderLeBonDeCommande`, `ConsulterUnBon`, `ListerLesBons` ; politique
`BonCommandeRecuALaReception`
(déclenchée par S5 ; jusque-là le legacy de réception continue de passer le bon à `RECU`).

## Entrées legacy

`BonCommandeRestController` (72 l.) `/api/v1/stock/bons-commande` : `POST`, `PUT /{id}`, `POST /{id}/valider`,
`GET /{id}`, `GET` ; `BonCommande` (127 l.),
`LigneBonCommande`, `BonCommandeService` (64 l.), `StockSequencePort` (référence `BC`).

## Données

Tables `bons_commande`, `bons_commande_lignes` ; séquence de pièces du centre. Aucune migration. **Donnée partagée**
avec la réception legacy (passage à
`RECU`) jusqu'à S5 : le nouveau dépôt doit lire et écrire les mêmes colonnes ; aucune double écriture concurrente (le
legacy n'écrit que `RECU`).

## Filet

Goldens : création, remplacement des lignes en brouillon, refus hors brouillon, validation sans ligne refusée, total = Σ
(montants ≥ 0), référence `BC` séquentielle,
rôles (écriture `ADMIN`/`PHARMACIEN`, lecture + `INFIRMIER`), autre centre introuvable. Reprendre
`BonCommandeTotalTest`.

## Bascule / retour arrière / retrait

Sans façade ; retour arrière = version précédente ; retrait du contrôleur et du service legacy en fin de slice, du
modèle `BonCommande` legacy quand S5 a repris la réception.

## Dépendances et risques

Dépend de S1. Risque faible.

## Livraison (2026-10-23)

- **Module** `backend/contexts/stock` : agrégat `BonDeCommande` (immuable ; `BROUILLON → VALIDE`, `RECU` posé par la
  réception), `LigneDeBon`, port `BonsDeCommande`, cas d'usage `GestionDesBonsDeCommande`, adapter `BonsDeCommandeJdbc`
  (mêmes tables, référence `BC` dans la transaction du bon, modification sous `FOR UPDATE`), contrôleur
  `BonsDeCommandeControleur` (mêmes routes, rôles et JSON). Port `Fournisseurs` complété (`existe`), `SessionCourante`
  complété (`utilisateur`).
- **Corrections décidées (« corrige », 2026-10-23), KB-STK-17 à 22** : 403 si le centre demandé ≠ session (centre
  facultatif, session par défaut) ; remplacement des lignes refusé hors brouillon (422 `BON_COMMANDE_NON_MODIFIABLE`) ;
  lignes : article obligatoire, quantité > 0, prix ≥ 0 (400 `BAD_REQUEST`), articles et fournisseur du centre (422
  `BON_COMMANDE_ARTICLE_INCONNU` / `_FOURNISSEUR_INCONNU`) ; un refus ne consomme aucune référence `BC` ; bon inconnu ou
  d'un autre centre : 422 `BON_COMMANDE_INTROUVABLE` (comme articles et groupes) ; codes stables
  `BON_COMMANDE_NON_VALIDABLE` et `BON_COMMANDE_SANS_LIGNE` à la validation ; auteur = utilisateur de la session (le
  `userId` du corps est ignoré). Les montants relus sont ceux de la base (quantité et prix à 2 décimales : `1.333`
  devient `1.33`).
- **Filet** : 13 scénarios, 100 goldens rejoués sur le nouveau code ; 68 goldens changés par ces corrections, leurs
  versions legacy conservées dans `characterization/stock/referentiel/_ecarts-legacy/bons-commande/*.legacy.json`.
- **Tests du module** : +8 (`BonDeCommandeTest`, `GestionDesBonsDeCommandeTest`), ArchUnit vert.
- **Retrait** : `BonCommandeRestController` et ses DTO de requête (`CreateBonCommandeRequest`,
  `UpdateBonCommandeLignesRequest`, `BonCommandeLigneDto`). **Conservés jusqu'à S5** : `BonCommandeService`,
  `BonCommandeUseCase` (jeu de démonstration `StockDemoDataSeeder`), le modèle, le port et l'adaptateur `BonCommande`
  (réception legacy, passage à `RECU`).
- **Hors correction** : JSON illisible, identifiant illisible et `centerId` illisible restent en 500 (transverse, comme
  S2).
