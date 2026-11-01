# Filet de non-régression — DM8 lectures publiées et retrait legacy

Slice : `dossier-medical-dm8-lectures-publiees`. Contexte : `dossier-medical`.
Date : 2026-11-01.

## Point d'observation

- Garde d'architecture : `TableDuDossierMedicalTest` (platform, 3 tests : périmètre,
  détection prouvée par test négatif, non-dérive de la liste des tables).
- Tables possédées : `demandes_examen`, `lignes_demande_examen`, `serologies`,
  `antecedents`, `allergies`, `observations_biologiques`, `bilans_pregreffe`,
  `etapes_bilan_pregreffe`.

## Périmètre de la garde

- Inclus : `shared-kernel`, modules `bc-*`, autres contextes (`src/main`, hors
  `/entity/`, `/migration/`, `/acl/`, `/persistence/`).
- Exclus (dette connue, comme SE7/O4) : la plateforme legacy (entités JPA
  `SerologieJpaEntity`, etc. et contrôleurs REST encore en service le temps du
  retrait de `bc-medical`), les ACL (`RisquesInfectieuxJdbc` côté Planification,
  passage légitime par langage publié en SQL direct transitoire).

## Scénarios verrouillés

1. Aucun source hors `dossier-medical` ne lit les 8 tables en SQL direct.
2. Test négatif : une lecture `FROM serologies` dans un bac temporaire est détectée.
3. Non-dérive : toute table lue en SQL par le contexte est listée dans la garde.

## Points d'attention

- `bc-medical` n'utilise que JPA (aucun SQL direct) : la garde passe sans exclusion
  supplémentaire ; son retrait complet (entités + contrôleurs platform) reste à planifier.
- Ports publiés restants à exposer quand un consommateur les exigera :
  `RisquesInfectieux`, `AbordsVasculaires` (aujourd'hui lus via ACL SQL transitoire).
- Données de santé : cloisonnement strict par `center_id`, aucune lecture cross-centre.
