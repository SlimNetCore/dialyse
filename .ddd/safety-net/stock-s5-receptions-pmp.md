---
slice: stock-s5-receptions-pmp
status: built
date: 2026-10-23
sources: [ .ddd/migration/slices/stock-s5-receptions-pmp.md, docs/reference/regles-de-gestion/08-stock.md (RG-STK-020 à 025, 040, 041), ADR 0024, 0029, 0043, 0045 ]
---

# Filet de caractérisation — S5 Réceptions et PMP

## Point d'observation

HTTP (MockMvc sur le contexte Spring complet, sécurité réelle + `RoleScopeFilter`, H2 du profil `test`) sur
`/api/v1/stock/bons-reception` (`POST`, `POST /from-bon-commande`, `PUT /{id}`, `POST /{id}/valider`, `GET /{id}`,
`GET`) et
`/api/v1/stock/dashboard/pmp-recalc/{start, jobs/{id}, locks}`, et état SQL de `bons_reception(_lignes)`, `lots`,
`stock_movements`, `articles` (stock, PMP), `bons_commande` (statut) et du compteur `SEQ_BR`. Les dates relatives au
jour
d'exécution sont normalisées en `<J-n>`, les horodatages en `<HORODATAGE>`.

## Tests

`backend/platform/src/test/java/com/hemodialyse/backend/characterization/stock/referentiel/ReceptionsEtPmpCharacterizationIntegrationTest.java`
(11 scénarios, harnais commun `ReferentielStockCharacterizationSupport`). Goldens :
`backend/platform/src/test/resources/characterization/stock/referentiel/receptions/` (162 fichiers). Deux mouvements
d'un
même article ne portent jamais le même horodatage (l'ordre de rejeu en dépend) ; les sorties sont posées en SQL.

## Comportements verrouillés

Voir `known-bugs.md` § « Stock S5 » (KB-STK-34 à 45) : isolation de centre absente, formes de refus, brouillon sans
contrôle, lignes non rattachées aux lots, rejeu du PMP (entrées avant sorties, sorties revalorisées, PMP à 4 décimales
arrondi à 2 en base), date des mouvements, correction (quantité consommée du lot), bon de commande `RECU`, gardes
d'inventaire, recalcul massif en mémoire, rôles.

## Stabilité

Legacy : 11/11 verts deux fois sans approbation après l'enregistrement (après normalisation des horodatages et
séparation de
deux mouvements simultanés).

## Limites

- **Non couvert** : saisie pendant un recalcul (« Recalcul en cours… », KB-STK-44) et concurrence entre deux réceptions
  croisées
  ou avec une sortie legacy : courses non déterministes sans seam, à écrire avec la construction (test de concurrence
  PG16 et H2).
- **Non couvert** : notification temps réel du centre (`stockMovementChanged`), mouvements d'inventaire clôturé,
  validation
  d'un bon de commande non validé (KB-STK-41 : à vérifier à la construction), emplacement des lignes.
- Les sorties de stock réelles (bons de sortie, consommation de séance) relèvent de S6/S6b : ici elles sont posées en
  SQL.
- Contraintes PostgreSQL non rejouées (H2 seulement).

