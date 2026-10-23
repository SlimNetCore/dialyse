---
slice: stock-s4-lectures-publiees
status: built
date: 2026-10-23
sources: [ .ddd/migration/slices/stock-s4-lectures-publiees.md, docs/reference/regles-de-gestion/08-stock.md (RG-STK-042 à 044, 060, 062), ADR 0024 ]
---

# Filet de caractérisation — S4 Lectures du Stock

## Point d'observation

HTTP (MockMvc sur le contexte Spring complet, sécurité réelle + `RoleScopeFilter`, H2 du profil `test`) sur les cinq
routes de lecture de `/api/v1/stock/dashboard` : `analytics`, `stock-valorise`, `alertes`, `tracabilite/{lotId}`,
`pmp-explain/{articleId}`. Aucune écriture : les données sont posées en SQL (articles, mouvements, lots, bons de
réception et de sortie). Les dates de la réponse sont relatives au jour d'exécution et normalisées en `<J-n>` /
`<J+n>` ;
les mouvements sont datés de midi UTC (hors zone de bascule de date).

## Tests

`backend/platform/src/test/java/com/hemodialyse/backend/characterization/stock/referentiel/LecturesDuStockCharacterizationIntegrationTest.java`
(10 scénarios, harnais commun `ReferentielStockCharacterizationSupport`). Goldens :
`backend/platform/src/test/resources/characterization/stock/referentiel/lectures-publiees/` (73 fichiers). Deux
mouvements d'un même article ne portent jamais le même horodatage (l'ordre de rejeu en dépend).

## Comportements verrouillés

Voir `known-bugs.md` § « Stock S4 » (KB-STK-23 à 33) : isolation de centre absente, erreurs de protocole en 500,
normalisation des paramètres, valorisation au PMP courant, tendance signée, stock valorisé, alertes (30 jours, rupture,
seuil), traçabilité, explication du PMP (pièces, remise à zéro, inventaire), rôles.

## Stabilité

Legacy : 10/10 verts deux fois sans approbation après l'enregistrement (après avoir séparé deux sorties simultanées,
dont l'ordre dépendait d'un identifiant aléatoire).

## Limites

- **Non couvert ici** : le calcul de valorisation de période (`ValorisationStockCalculator`,
  `ValorisationArticlesService`)
  n'est pas une route HTTP du Stock ; il est exercé par la Comptabilité
  (`ComptabilisationDuStockCharacterizationIntegrationTest`,
  inchangé) et par `ValorisationStockCalculatorTest`, `ValorisationArticlesCacheIntegrationTest`.
- Les routes `pmp-recalc/*` du même contrôleur relèvent de la slice S5.
- `GET /direction/stock-groupes` n'est pas relue ici (slice S2).
- Concurrence, cache de valorisation et contraintes PostgreSQL non rejoués (H2 seulement).

