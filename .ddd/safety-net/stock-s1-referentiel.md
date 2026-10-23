---
slice: stock-s1-referentiel
status: built
date: 2026-10-23
sources: [.ddd/migration/slices/stock-s1-referentiel.md, docs/reference/regles-de-gestion/08-stock.md (RG-STK-001 à 008), ADR 0019, 0024]
---

# Filet de caractérisation — S1 Référentiel et catalogue (articles, fournisseurs, emplacements)

## Point d'observation

HTTP (MockMvc sur le contexte Spring complet, sécurité réelle + `RoleScopeFilter`, H2 du profil `test`) et état SQL des
tables `articles`,
`fournisseurs`, `emplacements`. Aucun mock du code caractérisé, aucun seam.

## Tests

`backend/platform/src/test/java/com/hemodialyse/backend/characterization/stock/referentiel/`

- `ReferentielStockCharacterizationSupport` (harnais, fixtures SQL avec alias nommés, normalisation UUID / horodatages,
  clés triées) ;
- `ArticlesCharacterizationIntegrationTest` — 16 scénarios ;
- `FournisseursEtEmplacementsCharacterizationIntegrationTest` — 7 scénarios.

Goldens : `backend/platform/src/test/resources/characterization/stock/referentiel/{articles,fournisseurs-emplacements}/`
(≈ 110 fichiers).

## Scénarios couverts (11 routes de `/api/v1/stock/referentiel`)

| Route                         | Comportements verrouillés                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                     |
|-------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `POST /articles`              | création complète (nettoyage code/libellé/comptes, dosage, conservation, anémie, stock et PMP à 0, actif), création minimale, stock/PMP/actif de la requête ignorés, champs obligatoires et longueurs (400 `VALIDATION_ERROR`), invariants de la fiche (422 `ARTICLE_DOSAGE_INCOMPLET`, `ARTICLE_DOSAGE_INVALIDE`, `ARTICLE_COEFFICIENT_ACHAT_INVALIDE`, `ARTICLE_VALEUR_NEGATIVE`, `ARTICLE_STOCK_MAX_INFERIEUR_SEUIL`, `ARTICLE_COMPTE_INVALIDE`), énumérations inconnues (400), unicité du code par centre sans casse ni espaces (422 `ARTICLE_CODE_EXISTANT`), même code dans un autre centre (200), fiche sans booléens (500, KB-STK-03) |
| `PUT /articles/{id}`          | remplacement de la fiche avec conservation du stock, du PMP et de l'activité, même code en casse différente (200), code d'un autre article (422), article inconnu / d'un autre centre (422 `ARTICLE_INTROUVABLE`), corps d'un autre centre que la session (403)                                                                                                                                                                                                                                                                                                                                                                               |
| `GET /articles/{id}`          | lecture (ADMIN, INFIRMIER, MEDECIN ; centre de la session par défaut), inconnu / autre centre (422), centre autre que la session (403)                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                        |
| `PATCH /articles/{id}/active` | désactivation, réactivation, paramètre absent (500, KB-STK-06), article inconnu (422)                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                         |
| `GET /articles`               | liste triée par code (actifs et inactifs), filtre EPO / FER_INJECTABLE, type vide ignoré, type inconnu (400), sans centre (500, KB-STK-05), **autre centre lisible** (KB-STK-04)                                                                                                                                                                                                                                                                                                                                                                                                                                                              |
| `GET /articles/page`          | recherche sur code, libellé, DCI, code-barres (casse et espaces ignorés), actifs / inactifs, pagination, bornes (`size` 0 → 1, 500 → 200, page négative → 0), centre autre que la session (403)                                                                                                                                                                                                                                                                                                                                                                                                                                               |
| `POST                         | GET /fournisseurs`                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                            | création (raison sociale non nettoyée, KB-STK-09), refus raison sociale absente / blanche / centre absent (400), liste des actifs triée par raison sociale, recherche insensible à la casse et nettoyée, inactif non trouvé, sans centre (500), **création et lecture dans un autre centre** (KB-STK-04) |
| `POST                         | GET /emplacements`                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                            | création (libellé non nettoyé), refus (400), liste des actifs triée par libellé, sans centre (500), **création et lecture dans un autre centre** (KB-STK-04) |
| Rôles                         | écriture `ADMIN`, `PHARMACIEN` ; lecture des articles + `INFIRMIER`, `MEDECIN` (pas la recherche paginée) ; fournisseurs/emplacements + `INFIRMIER` ; `SECRETAIRE` et `MEDECIN` refusés selon la route ; anonyme 403 sans corps                                                                                                                                                                                                                                                                                                                                                                                                               |

## Stabilité

Suite exécutée **3 fois de suite** sans approbation
(`mvnw -o -pl platform -am test -Dtest=…/stock/referentiel/*IntegrationTest`) : 23/23 verts à chaque
passage, aucun test instable. Les horodatages et identifiants générés sont normalisés ; les données de test utilisent
deux centres dédiés (`99997500-…-a/b`), nettoyés avant et après chaque scénario.

## Comportements suspects consignés (ADR 0019)

`known-bugs.md` § « Stock S1 » : KB-STK-03 à 06, 08, 09 **corrigés par la slice S1 (2026-10-23)**, KB-STK-07
**reproduit** (422, décision
utilisateur du 2026-10-23), KB-STK-10 conforme. Les 18 goldens legacy en écart sont conservés dans
`_ecarts-legacy/{articles,fournisseurs-emplacements}/*.legacy.json` ; les goldens dont le nom décrivait le bug ont été
renommés (`creation_sans_booleens_200`, `*_sans_centre_utilise_la_session_200`, `refus_*_centre_B_par_admin_A`).

## Rejeu sur le nouveau code (2026-10-23)

Contexte `backend/contexts/stock` (`ReferentielStockControleur`) : 23/23 scénarios verts **3 fois de suite** sans
approbation
(`mvnw -pl platform -am test -Dtest=ArticlesCharacterizationIntegrationTest,FournisseursEtEmplacementsCharacterizationIntegrationTest`) ;
92 goldens inchangés, 18 écarts décidés (KB-STK-03 à 06, 08, 09).

## Fichiers protégés (`covered.json`)

Contrôleur `StockReferentialRestController`, requêtes `Create{Article,Fournisseur,Emplacement}Request`,
`StockReferentialDomainService`,
`StockReferentialUseCase`, ports `ArticleRepositoryPort`, `ArticleCatalogPort`, `FournisseurRepositoryPort`,
`EmplacementRepositoryPort`, modèles `Article`,
`ArticleFiche`, `ConditionConservation`, `TypeTraitementAnemie`, `Fournisseur`, `Emplacement`, adaptateurs, entités et
dépôts JPA correspondants (25 fichiers).

## Limites (ce qui n'est PAS verrouillé)

- **Consommateurs de l'article hors de ces routes** : `Article` et `ArticleRepositoryAdapter` sont aussi utilisés par
  les bons, l'inventaire, la Séance (raccourcis, consommables), le Dossier médical (`SuiviAnemieQueryService`,
  prescriptions) et la Direction. Ces usages ne sont pas couverts par ce filet ;
  modifier ces classes pour S1 exige de garder leur contrat (lecture d'article, `debiter`, `quantiteStockPourDose`)
  intact — la conversion de dose est
  verrouillée par `ArticleTest` (legacy) et le sera par le VO `Dosage`.
- `tvaTypeId` / `fournisseurId` d'un article : non vérifiés en existence par le legacy (aucun scénario avec identifiant
  inconnu : clé étrangère possible
  en base, comportement H2 vs PostgreSQL non observé).
- Contraintes et longueurs **PostgreSQL** (colonnes `VARCHAR`, `NUMERIC`) non rejouées : filet sous H2 seulement.
- Concurrence : deux créations simultanées du même code (contrôle applicatif sans contrainte en base) non déclenchées.
- Pas de mesure de couverture de branches ni de mutation (PIT non lancé sur cette zone legacy ; la mutation sera mesurée
  sur le nouveau domaine, comme
  pour les slices précédentes).






