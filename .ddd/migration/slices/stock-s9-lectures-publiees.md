---
slice: stock-s9-lectures-publiees
status: verified
date: 2026-10-29
order: 10
context: stock
sources: [.ddd/migration/roadmap.md (§ 1 quater, dette « lecteurs SQL »), .ddd/tactical/stock.md, ADR 0024, ADR 0045, ADR 0010]
---

# S9 — Lectures publiées hors contexte : plus aucun SQL sur les tables du Stock

Refonte sur place (ADR 0024) : aucune migration de données, aucune URL modifiée. Objectif : ramener à zéro l'indicateur
« lectures SQL des tables du stock par d'autres contextes » (cible 0, 7 lecteurs au départ).

## Fait

| Contexte lecteur | Fichier (avant)                                       | Tables lues                                                                  | Remplacement                                                    |
|------------------|-------------------------------------------------------|------------------------------------------------------------------------------|-----------------------------------------------------------------|
| Séance           | `SeanceRestController` (consommables d'une séance)    | `stock_movements`, `articles`, `sorties_pour_seances_lignes`                 | `ConsommationsDeSeance.historique(centre, seance)`              |
| Séance           | `SeanceRestController` (résumé de séance)             | `consommations_de_seance`, `consommations_de_seance_imputations`, `articles` | `ConsommationsDeSeance.resume(...)` / `sortiesHistoriques(...)` |
| Séance           | `SeanceRestController` (sorties par date)             | `bons_sortie`, `bons_sortie_lignes`, `articles`                              | `ConsommationsDeSeance.sortiesParDate(centre, date)`            |
| Direction        | `DirectionIndicatorsQueryService` (articles actifs)   | `articles`                                                                   | `IndicateursDuStock.articlesActifs(centre)`                     |
| Direction        | `DirectionIndicatorsQueryService` (lots, péremptions) | `lots`                                                                       | `IndicateursDuStock.lotsEnStock(centre)`                        |
| Référentiel      | `ReferentialRepositoryAdapter.findArticles`           | `articles`                                                                   | `CatalogueDArticles` (déjà publié en S1)                        |
| Comptabilité     | `UsagesDuParametrageJdbc.compteRattacheAUnArticle`    | `articles`                                                                   | `CatalogueDArticles.utiliseLeCompte(...)`                       |

| Couche                                                 | Livré                                                                                                                                                                                                                   |
|--------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Langage publié (`contexts/stock/publishedlanguage`)    | `ConsommationsDeSeance` étendu des lectures `historique`, `sortiesHistoriques`, `sortiesParDate` (+ records `LigneHistorique`, `SortieParDate`) ; `IndicateursDuStock` (nouveau, indicateurs agrégés pour la Direction) |
| Adapters (`contexts/stock/infrastructure/persistence`) | `ConsommationsDeSeanceDuStock` : lectures JDBC bornées au centre ; `IndicateursDuStockJdbc` : deux requêtes agrégées, aucun accès ligne à ligne                                                                         |
| Câblage                                                | `StockContexteConfiguration` : beans `stockConsommationsDeSeance`, `stockIndicateursDuStock`, `stockCatalogueDArticles`                                                                                                 |
| Consommateurs                                          | `SeanceRestController`, `DirectionIndicatorsQueryService`, `ReferentialRepositoryAdapter`, `UsagesDuParametrageJdbc` n'importent plus ni `JdbcTemplate` ni les tables du Stock                                          |
| Frontend                                               | aucun changement (mêmes contrats HTTP, mêmes DTO)                                                                                                                                                                       |

## Garde de non-régression (`TableDuStockTest`)

Garde ArchUnit-like dans `backend/platform/src/test/java/com/hemodialyse/backend/architecture/TableDuStockTest.java` :
tout source de `platform`, du `shared-kernel` legacy, des modules `bc-*` et des contextes autres que `stock` qui lit ou
écrit une table du Stock fait échouer le build.

- **20 tables gardées** (liste issue du schéma et des adapters du contexte) : `articles`, `lots`, `fournisseurs`,
  `emplacements`, `groupes_articles{,_items}`, `bons_commande{,_lignes}`, `bons_reception{,_lignes}`,
  `bons_sortie{,_lignes}`,
  `stock_movements`, `stock_parametres`, `inventaires`, `inventaire_lignes`, `consommations_de_seance{,_imputations}`,
  `sorties_pour_seances{,_lignes}`.
- **Détection** : mots-clés SQL `FROM|JOIN|INTO|UPDATE|TABLE` en majuscules (le code de production écrit ses requêtes en
  majuscules ; la prose française « la table des séances » ne doit pas déclencher la garde), puis nom de table comparé
  sans
  tenir compte de la casse. Entités JPA (`/entity/`) et migrations Flyway (`/migration/`) hors périmètre.
- **Trois tests** : (1) aucun source hors contexte ne mentionne une table du Stock ; (2) **preuve que la garde
  échoue** —
  un répertoire temporaire contenant `SELECT … FROM articles` est bien signalé, un fichier ne lisant que `seances` ne
  l'est
  pas ; (3) **preuve de non-dérive** — toute table lue en SQL par le contexte Stock doit figurer dans la liste gardée ou
  dans `TABLES_LUES_AILLEURS` (dette connue : `app_settings`, `centers`, `position_creneau`, `salle`, `seances`).
- Chemins normalisés (`\` → `/`) : le filtre d'exclusion est identique sous Windows et sous Linux.

**Correction du 2026-10-29 (revue `/ddd-verify`)** : la première version de cette garde était **inerte** — le motif
`"\\\\s+"` produisait la regex `\\s+` (antislash littéral suivi de `s`) et ne pouvait jamais reconnaître un SQL ; elle
ne
gardait que 6 tables, dont aucune des tables `articles`/`lots` réellement en cause. La slice n'a donc été portée à
`verified` qu'après correction et preuve par test négatif.

## Preuves

- `TableDuStockTest` : 3 tests verts (périmètre, détection prouvée, non-dérive) —
  `mvn -o -pl platform test -Dtest=TableDuStockTest`.
- `SeanceRestControllerTest` : 16 tests verts après migration des lecteurs.
- `DirectionIndicatorsQueryService` et `ReferentialRepositoryAdapter` : inchangés fonctionnellement (mêmes DTO, mêmes
  libellés), couverts par `DirectionIndicatorsIntegrationTest` et `ReferentialAdminIntegrationTest`.
- `UsagesDuParametrageJdbc` : `compteRattacheAUnArticle` couvert par les tests de comptabilité (310 verts).
- Contrôle indépendant : recherche exhaustive `FROM|JOIN|INTO|UPDATE|TABLE + <table du stock>` sur `src/main` hors
  `contexts/stock` → 0 occurrence.

## Reste / dettes

- Le contexte Stock lit encore **cinq tables d'autres contextes** (`centers`, `salle`, `position_creneau`, `seances`,
  `app_settings`) via ses ACL : c'est le sens inverse du partage, à traiter avec l'extraction d'Organisation/Séance
  (inscrit dans `TABLES_LUES_AILLEURS`).
- `ConsommationsDeSeance` mélange lectures et une commande (`enregistrerALaValidation`) : séparation lecture/écriture à
  envisager lors de la modélisation de Séance (nit de revue).
