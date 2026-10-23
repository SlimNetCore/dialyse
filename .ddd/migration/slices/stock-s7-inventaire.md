---
slice: stock-s7-inventaire
status: planned
date: 2026-10-23
order: 8
context: stock
sources: [.ddd/tactical/stock.md (Inventaire, PeriodeDeStock), RG-STK-050 à 057, ADR 0009, 0024, 0045 (décisions 6 et 7)]
---

# S7 — Inventaire et période de stock (clôture)

## Cas d'usage

`OuvrirUnInventaire`, `ConsulterLEtat`, `ConsulterUnInventaire`, `ListerLesInventaires`, `Compter`, `AjouterUneLigne`,
`RetirerUneLigne`, `TelechargerLaFeuille`,
`ImporterUneFeuille`, `ReporterLeTheorique`, `CloturerLInventaire`, `AnnulerLInventaire`, `ImprimerLeRapport` (PV) ;
`PeriodeDeStock` devient le propriétaire des
règles centre-entier (verrou partagé pour chaque mouvement, exclusif à l'ouverture/clôture/annulation).

## Entrées legacy

`InventaireRestController` (220 l.) `/api/v1/stock/inventaires` : `GET /{id}/rapport`, `GET`, `GET /etat`, `POST`,
`GET /{id}`, `PUT /{id}/lignes/{ligneId}`,
`POST /{id}/lignes`, `DELETE /{id}/lignes/{ligneId}`, `POST /{id}/reporter-theorique`, `POST /{id}/cloturer`,
`POST /{id}/annuler`, `GET|POST /{id}/feuille-comptage` ;
`Inventaire` (228 l.), `LigneInventaire` (212 l.), `InventaireService` (362 l.), `InventaireApplicationService`,
`InventoryGuardedMovementRepository` (garde **sans
verrou**, l. 63-72), `WebSocketInventaireEventPublisher`, modèle de document `INVENTAIRE_STOCK`.

## Données

Tables `inventaires`, `inventaire_lignes` (V4), `lots`, `stock_movements` (clôture, mouvement `INVENTAIRE`), `articles`.
`PeriodeDeStock` : déduite de `inventaires`
(en cours / dernière clôture) avec verrou sur une ligne par centre — décider entre une petite table additive
(`periodes_de_stock`, comme `mois_absences`) et un verrou
consultatif PostgreSQL ; **recommandation : table additive** (portable H2/PG, testable).

## Écart décidé (ADR 0045 / modèle)

Garde d'inventaire **sous verrou** : une réception commencée avant l'ouverture se termine avant le gel (l'ouverture
attend), aucune écriture après le gel ;
inventaire refusé tant qu'une sortie pour séances n'est pas rapprochée (si S6b livrée).

## Filet (points d'argent § 11.3)

Goldens : ouverture (date requise, non future, > dernière clôture, ≥ dernier mouvement, recalcul en cours refusé, un
seul en cours), théorique figé (lot non épuisé
d'article actif, FEFO, ligne sans lot), comptage (≥ 0, 3 décimales, lot trouvé, doublon, ligne non retirable), import de
feuille (anomalies, feuille d'un autre
inventaire), report du théorique, clôture (incomplète, écart sans motif, lot disparu ; stock des lots = compté,
mouvements figés, `INVENTAIRE` au PMP, PMP recalculé),
annulation sans effet, mouvements refusés pendant l'inventaire et dans la période close, PV « PROVISOIRE ». Reprendre
`InventaireServiceTest`,
`InventaireIntegrationTest`, `InventaireStockReportTemplateTest`. Concurrence : ouverture pendant une réception ; deux
ouvertures simultanées.

## Bascule / retour arrière / retrait

Sans façade ; retour arrière = version précédente (la table additive reste, sans effet). Retrait : contrôleur,
`InventaireService`, `InventoryGuardedMovementRepository`.

## Dépendances et risques

Dépend de S5 (`RejeuDuPmp`, `StockArticle.poserStockInventorie`) et de S6. **Risque élevé** : transaction centre-entier,
argent (écarts valorisés), Comptabilité.

