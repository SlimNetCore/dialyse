---
slice: comptabilite-c4-modeles-pieces
status: built
date: 2026-10-15
sources: [.ddd/tactical/comptabilite.md §2 §4 §8 §9 §10 §11, ADR 0024, .ddd/safety-net/comptabilite-c3-ecritures.md]
---

# C4 — Modèles et pièces

## Périmètre

Refactoring sur place dans `backend/contexts/comptabilite`, mêmes URLs et tables, aucune migration de schéma.
Le périmètre de caractérisation et de remplacement est :

- `GET /api/v1/comptabilite/modeles` (filtres `centerId?`, `actifs`, pagination `page`/`size`) ;
- `POST /api/v1/comptabilite/modeles`, `PUT /modeles/{id}`, `DELETE /modeles/{id}` ;
- `POST /api/v1/comptabilite/pieces` ;
- `POST /api/v1/comptabilite/pieces/{ecritureId}/extourne`.

Tables écrites : `modeles_piece`, `modeles_piece_lignes`, `ecritures_comptables`, `lignes_ecriture`.
Tables consultées pour les règles : journaux, plan comptable et périodes comptables. Les écritures produites restent
compatibles avec C3 et les producteurs legacy C5/C6 jusqu'à leurs slices respectives.

## Règles tactiques à caractériser sur le legacy

- Modèle : 2 à 20 lignes, au moins un débit et un crédit ; code normalisé/unique par centre ; journal et comptes
  actifs ;
  suppression d'un modèle sans suppression des pièces déjà saisies.
- Saisie : modèle actif, période ouverte et journal actif ; montant non négatif par ligne ; les zéros sont omis ; au
  moins
  deux lignes restantes et écriture équilibrée ; statut initial `VALIDEE`, numéro partagé du journal/année/centre, lien
  au
  modèle conservé.
- Extourne : uniquement une pièce saisie, jamais une extourne ni une seconde extourne ; nouvelle écriture inverse, même
  journal, date d'écriture du jour et date de pièce d'origine ; période du jour ouverte.
- Isolation de centre et autorisations selon les routes existantes : lecture des modèles ADMIN/SECRETAIRE, mutations de
  modèle ADMIN, saisie ADMIN/SECRETAIRE, extourne ADMIN.

Les détails d'erreurs HTTP, pagination, normalisation, numérotation et effets SQL sont à figer par le filet, pas à
déduire
de ce plan. Aucun seam de production n'est autorisé par cette fiche.

## Implémentation et validation (2026-10-15)

1. Écrire les tests de frontière HTTP et d'effets SQL sur le code legacy ; revue des goldens avant approbation.
2. Rejouer trois fois sur le legacy et enregistrer les fichiers effectivement protégés dans `covered.json`.
3. Routes et cas d'usage déplacés dans `backend/contexts/comptabilite` ; mêmes tables, même contrat HTTP et même garde
   de centre.
   Le nouveau contrôleur `ModelesEtPiecesControleur` sert seul ces routes ; le flag transitoire
   `comptabilite.c4.nouveau-code`
   est supprimé (2026-10-19, voir étape 6).
4. B1/B2 corrigés sans migration : verrous transactionnels PostgreSQL par centre/journal/année et par source. Le même
   `EcritureComptableJpaAdapter` legacy prend ces verrous pour les consommateurs C5/C6 encore en service. Tests
   concurrents
   H2 couvrent l'attribution distincte des numéros et une seule insertion par source.
5. Parité : la suite C4 (10 scénarios, goldens inchangés) passe contre le nouveau code. Les tests du contexte passent
   (272), dont les tests de concurrence ; `check-arch.ts` : 0 violation ; tests de non-régression C3/C5/C6 : 16 réussis.
6. Retrait du legacy C4 (2026-10-19, étape 4 de la recette, en vue de C7) : supprimés
   `ComptabiliteParametrageRestController`,
   `PiecesComptablesApplicationService`, `ModelePieceJdbcAdapter` (plateforme) et `ModelePiece`,
   `ModelePieceRepositoryPort`,
   `PiecesComptablesUseCase`, `PiecesComptablesService` (`bc-comptabilite`) ; les tests C4 de `ParametrageSoupleTest` et
   le
   fake `Modeles` sont retirés (leurs règles sont couvertes par le contexte et la caractérisation). Vérification : suite
   C4
   10/10 sans flag, 138 tests comptables plateforme/`bc-comptabilite` verts. Les verrous B1/B2 de
   `EcritureComptableJpaAdapter` restent pour C5/C6.

## Hors périmètre

C1/C2/C3, génération automatique des ventes/règlements (C5), comptabilisation du stock (C6), routes de réglage du plan
et
des journaux, interface Angular, migration de schéma.
