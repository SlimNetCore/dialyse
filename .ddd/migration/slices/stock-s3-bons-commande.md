---
slice: stock-s3-bons-commande
status: planned
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

