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

## Livraison (2026-10-23)

- **Module** `backend/contexts/stock` : agrégat `GroupeDArticles` (clé de nom, nettoyage, 1 à 500 articles, horloge
  injectée), port
  `GroupesDArticles`, cas d'usage `GestionDesGroupesDArticles`, adapter `GroupesDArticlesJdbc` (mêmes tables,
  composition remplacée dans une
  transaction), contrôleur `GroupesDArticlesControleur` (même URL, `ADMIN`), langage publié `GroupesDArticlesDesCentres`
  (direction).
  Port `Articles` complété (`identifiants`, contrôle de la composition).
- **Filet** : 12 scénarios, 70 goldens, verts 3 fois sur le legacy puis sur le nouveau code **sans aucun golden
  modifié** (parité stricte) ;
  KB-STK-11 à 16 reproduits (`known-bugs.md`).
- **Direction** : `DirectionStockGroupesQueryService` lit les groupes par `GroupesDArticlesDesCentres` ; la valorisation
  (`ValorisationArticlesService`) reste legacy jusqu'à S4/S5.
- **Tests du module** : +13 (`GroupeDArticlesTest`, `GestionDesGroupesDArticlesTest`, langage publié), ArchUnit vert,
  `check-arch` 0 violation.
- **Retrait** : `GroupeArticleRestController`, `GroupeArticleDtos`, `GroupeArticleApplicationService`,
  `GroupeArticleRepositoryAdapter`,
  `GroupeArticleJpaEntity`, `GroupeArticleJpaRepository`, `GroupeArticle`, `GroupeArticleRepositoryPort` et leurs
  tests ; DDL H2 des deux tables
  ajoutée à `db/schema.sql` (production : Flyway V9, inchangé).
- RG-STK-070 à 073 et annexe de traçabilité mises à jour.


