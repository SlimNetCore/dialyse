---
slice: stock-s1-referentiel
status: built
date: 2026-10-23
order: 1 (S1 à S8, voir `.ddd/migration/roadmap.md` § 1 quinquies)
context: stock (module cible `backend/contexts/stock`)
sources: [.ddd/tactical/stock.md (Article, catalogue publié), docs/reference/regles-de-gestion/08-stock.md (RG-STK-001 à 008), ADR 0006, 0024, 0045]
---

# S1 — Référentiel et catalogue publié (articles, fournisseurs, emplacements)

**Pourquoi d'abord** : petite, réelle, représentative (création du module `contexts/stock`, ArchUnit, composition root,
ACL), sans argent ni concurrence ;
elle publie le **catalogue d'articles** dont dépendent la Séance (raccourcis, consommables) et le Dossier médical (types
de traitement de l'anémie).

## Cas d'usage (tactique § 8)

`ReferencerUnArticle`, `ModifierLaFiche`, `ChangerLActiviteDeLArticle`, `ConsulterUnArticle`, `ChercherDesArticles`
(pages), `ListerLesArticles`,
`ListerParTypeDeTraitement` ; `CreerUnFournisseur`, `ListerLesFournisseurs`, `ChercherDesFournisseurs` ;
`CreerUnEmplacement`, `ListerLesEmplacements`.

## Entrées legacy (mêmes URLs, refaites sur place)

`StockReferentialRestController` (116 l.) `/api/v1/stock/referentiel` : `POST /articles`, `PUT /articles/{id}`,
`GET /articles/{id}`, `PATCH /articles/{id}/active`,
`GET /articles/page`, `GET /articles`, `POST|GET /fournisseurs`, `POST|GET /emplacements`. Domaine : `bc-article`
(`Article`, `ArticleFiche`,
`TypeTraitementAnemie`, `ConditionConservation`), `StockReferentialDomainService` (138 l.), `StockReferentialUseCase`,
`ArticleCatalogPort`.

## Consommateurs implicites (à passer par le langage publié `CatalogueDArticles`)

Séance (`SeanceRaccourciJdbcAdapter` sur `seance_raccourcis_articles`, consommables), Dossier médical
(`listArticlesByTypeTraitementAnemie`), Stock lui-même (bons, inventaire : lecture d'article), `StockDemoDataSeeder`,
Direction (groupes). Recensement exact par `FrontieresDesContextesTest` avant retrait.

## Données

Tables `articles` (fiche, `stock`/`pmp` courants : **lus seulement** dans cette slice, écrits par S5/S6),
`fournisseurs`, `emplacements` ; propriétaire : Stock.
Aucune migration. Unicité du code par centre : vérifier la contrainte existante (sinon contrôle applicatif, comme
aujourd'hui).

## Façade, bascule, retour arrière

Aucune façade ni flag (ADR 0024) : livraison en une fois. Retour arrière = version précédente (aucune donnée nouvelle).

## Filet de caractérisation (avant code)

Goldens HTTP sur les 11 routes : création complète (dosage, comptes, conservation), refus (`ARTICLE_CODE_REQUIS`,
`ARTICLE_CODE_EXISTANT` casse ignorée,
`ARTICLE_COEFFICIENT_ACHAT_INVALIDE`, `ARTICLE_VALEUR_NEGATIVE`, `ARTICLE_STOCK_MAX_INFERIEUR_SEUIL`,
`ARTICLE_DOSAGE_INCOMPLET`, `ARTICLE_COMPTE_INVALIDE`),
pagination et recherche (code, libellé, DCI, code-barres ; actif/désactivé), (dés)activation, rôles (RG-STK-004), autre
centre (404/403), fournisseur sans raison
sociale, emplacement sans libellé. Reprendre `ArticleTest`, `ArticleFicheRestControllerIntegrationTest`.

## Critères de « built »

Suite verte sur le legacy puis sur le nouveau code ; ArchUnit du module ; `check-arch` 0 violation ; RG-STK-001 à 008 à
jour (sources) ; port publié
`CatalogueDArticles` utilisé par Séance et Dossier médical ; contrôleur legacy retiré.

## Retrait

`StockReferentialRestController` et `StockReferentialDomainService` à la fin de S1 ; `bc-article` quand plus aucun
consommateur ne le référence (au plus tard S8).

## Dépendances et risques

Aucune dépendance. Risques : consommateurs implicites de `Article` (`bc-article` est importé par `bc-seance` : 3
arêtes) ; dosage (conversion RG-STK-006 utilisée
par la prescription RG-MED-071) : garder la conversion dans le VO `Dosage`, exposée par le catalogue publié.

## Livraison (2026-10-23)

- **Module** `backend/contexts/stock` : domaine (`Article`, `FicheArticle`, `Dosage`, `Fournisseur`, `Emplacement`,
  `Pagination`), cas d'usage
  `ReferentielDesArticles`, `ReferentielDesFournisseursEtEmplacements`, adapters JDBC (mêmes tables), ACL
  `SessionLegacy`, contrôleur
  `ReferentielStockControleur` (mêmes URLs), langage publié `CatalogueDArticles` / `ArticleDuCatalogue`, composition
  root `StockContexteConfiguration`.
- **Filet** : 23/23 scénarios verts 3 fois sur le nouveau code ; 92 goldens inchangés, 18 écarts décidés (KB-STK-03 à
  06, 08, 09, versions
  legacy dans `_ecarts-legacy`). KB-STK-07 **reproduit** (introuvable en 422, décision utilisateur du 2026-10-23).
  Séance : bascule sur le
  catalogue publié reportée à S5/S6/S6b (accord utilisateur du 2026-10-23).
- **Tests du module** : 33 (ArchUnit 10 règles, `FicheArticleTest`, `DosageTest`, `ReferentielSimpleTest`,
  `ReferentielDesArticlesTest`,
  `CatalogueDArticlesDuReferentielTest`) ; `check-arch` : 0 violation.
- **Catalogue publié branché** : le Dossier médical passe par `CatalogueDArticles` — administration de l'anémie
  (`AdministrationTraitementRestController`, conversion de dose RG-STK-006), prescriptions
  (`PrescriptionMedicaleRestController` : code, libellé,
  contrôle de conversion de la dose prescrite, article inconnu toujours ignoré) et suivi de l'anémie
  (`SuiviAnemieQueryService`) ; refus traduits en
  422 avec le même code. La Séance (`SeanceRaccourciDomainService`, `SeanceDomainService` dans `bc-seance`,
  consommables) lit toujours l'article
  legacy : bascule avec les slices S5/S6/S6b (ces lectures portent aussi stock et lots).
- **Retrait** : `StockReferentialRestController`, `Create{Article,Fournisseur,Emplacement}Request`,
  `StockReferentialDomainService`,
  `StockReferentialUseCase`, `ArticleCatalogPort` (et son implémentation dans `ArticleRepositoryAdapter`). `bc-article`
  (`Article` legacy, utilisé
  par bons, inventaire, dashboard) reste jusqu'à S8.
- RG-STK-001, 003, 004, 006, 007, 008 mis à jour (sources et corrections) ; annexe de traçabilité mise à jour.





