---
slice: stock-s2-groupes-articles
status: built
date: 2026-10-23
sources: [ .ddd/migration/slices/stock-s2-groupes-articles.md, docs/reference/regles-de-gestion/08-stock.md (RG-STK-070 à 073), ADR 0019, 0024 ]
---

# Filet de caractérisation — S2 Groupes d'articles

## Point d'observation

HTTP (MockMvc sur le contexte Spring complet, sécurité réelle + `RoleScopeFilter`, H2 du profil `test`) et état SQL des
tables `groupes_articles`
et `groupes_articles_items`. Aucun mock du code caractérisé, aucun seam.

## Tests

`backend/platform/src/test/java/com/hemodialyse/backend/characterization/stock/referentiel/GroupesDArticlesCharacterizationIntegrationTest.java`
(12 scénarios, harnais commun `ReferentielStockCharacterizationSupport`). Articles à identifiants fixes (la réponse trie
les articles par
identifiant). Goldens : `backend/platform/src/test/resources/characterization/stock/referentiel/groupes-articles/` (70
fichiers).

## Comportements verrouillés

- Création : nom nettoyé (espaces de bord, espaces multiples), description blanche = absente, doublons ignorés, articles
  désactivés acceptés,
  201 ; 1 à 500 articles (501 : 400 `VALIDATION_ERROR`) ; nom absent, blanc, > 100, description > 500 (400) ; liste vide
  (400 `VALIDATION_ERROR`),
  liste de `null` (400 `BAD_REQUEST`) ; corps absent ou illisible (500).
- Unicité du nom par centre sans casse, accents ni espaces multiples (422 `GROUPE_ARTICLE_NOM_EXISTANT`) ; même nom dans
  un autre centre (201).
- Articles inconnus ou d'un autre centre (422 `GROUPE_ARTICLE_ARTICLE_INCONNU`).
- Lecture, modification (remplacement total, son propre nom en casse différente accepté), suppression (204, composition
  supprimée,
  articles conservés) ; groupe inconnu ou d'un autre centre : 422 `GROUPE_ARTICLE_INTROUVABLE` ; centre demandé autre
  que la session : 403.
- Liste paginée triée par clé de nom (page, taille, hors limites, taille non bornée ; page négative et taille nulle :
  400 ; taille illisible : 500).
- Rôles : `ADMIN` seul (`PHARMACIEN`, `INFIRMIER`, `MEDECIN`, `SECRETAIRE` : 403 `ACCESS_DENIED` ; `DIRECTION` : 403
  `ROLE_SCOPE` ; anonyme : 403).

## Stabilité

Legacy : 12/12 verts **3 fois** sans approbation (après l'enregistrement). Nouveau code (`GroupesDArticlesControleur`) :
12/12 verts, **aucun
golden modifié** (parité stricte, aucun écart décidé).

## Comportements suspects consignés

`known-bugs.md` § « Stock S2 » : KB-STK-11 à 16, tous **reproduits** par le nouveau code.

## Limites

- La lecture de la direction (`GET /api/v1/direction/stock-groupes`) n'a pas de golden HTTP (montage d'une société et de
  valorisations) : elle
  reste couverte par `DirectionStockGroupesQueryServiceTest` (consolidation par clé de nom, bornage aux centres de la
  société, période) et par
  le test du langage publié dans le module. La valorisation elle-même (`ValorisationArticlesService`) reste legacy
  jusqu'aux slices S4/S5.
- Concurrence (deux créations simultanées du même nom) non déclenchée (KB-STK-16).
- Contraintes PostgreSQL non rejouées (H2 seulement).


