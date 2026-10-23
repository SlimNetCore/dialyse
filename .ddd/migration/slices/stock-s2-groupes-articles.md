---
slice: stock-s2-groupes-articles
status: planned
date: 2026-10-23
order: 2
context: stock
sources: [.ddd/tactical/stock.md (GroupeArticle), RG-STK-070 à 073, ADR 0024]
---

# S2 — Groupes d'articles (kits)

## Cas d'usage

`CreerUnGroupe`, `ModifierUnGroupe` (nom, description, composition), `SupprimerUnGroupe`, `ConsulterUnGroupe`,
`ListerLesGroupes` ; lecture publiée
`ValorisationDesGroupes` (Direction, RG-STK-073).

## Entrées legacy

`GroupeArticleRestController` (81 l.) `/api/v1/stock/groupes-articles` : `GET`, `GET /{id}`, `POST`, `PUT /{id}`,
`DELETE /{id}` ; `GroupeArticle` (117 l.),
`GroupeArticleApplicationService`, `GroupeArticleRepositoryPort` ; Direction : `DirectionStockGroupesQueryService`.

## Données

Tables `groupes_articles`, `groupes_articles_items` (V9). Aucune migration.

## Filet

Goldens : création (1 et 500 articles), 501 refusé, nom normalisé (espaces), unicité « Kit CNAS » = « KIT CNAS » (casse,
accents, espaces multiples),
article d'un autre centre (`GROUPE_ARTICLE_ARTICLE_INCONNU`), groupe introuvable, suppression, rôle `ADMIN` seul ;
valorisation direction sans donnée nominative.
Reprendre `GroupeArticleTest`, `GroupeArticleApplicationServiceTest`, `GroupeArticleIntegrationTest`,
`DirectionStockGroupesQueryServiceTest`.

## Bascule / retour arrière / retrait

Sans façade ; retour arrière = version précédente ; retrait du contrôleur, du service et du modèle legacy à la fin de la
slice.

## Dépendances et risques

Dépend de S1 (articles du centre). Risque faible ; la valorisation de groupe dépend du PMP courant (lu, non recalculé).

