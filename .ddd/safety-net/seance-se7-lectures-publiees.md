---
slice: seance-se7-lectures-publiees
status: umbrella (planned) ; réécrit d'après la réalité le 2026-11-08, l'ancien contenu annonçait « characterized » sans test
date: 2026-11-08
sources: [.ddd/migration/slices/seance-se7-lectures-publiees.md, grep ciblés du 2026-11-08]
---

# Filet SE7 (slice-parapluie) : état réel

Aucun test ne porte le nom SE7 en tant que tel : le filet est réparti par sous-slice. Seule `seance-se7a-routes-residuelles` est caractérisée (voir `.ddd/safety-net/seance-se7a-routes-residuelles.md`). Les affirmations de l'ancienne version (« aucune requête SQL directe sur `seances` hors du contexte », « tous les consommateurs passent par `seance.v1` ») étaient FAUSSES.

## Inventaire constaté (2026-11-08)

- **Routes HTTP restantes hors `contexts/seance`** : 12 (`SeanceRestController` : journal, dashboard, dashboard/details, dashboard/export, calendar, calendar/holiday POST+DELETE, calendar/closure POST+DELETE ; `SeanceStationRestController` : recentes ; `VoletMedicalRestController` et `VoletParamedicalRestController` : PUT). `SeanceRestController` fait encore 758 lignes (9 routes, le reste est la logique du tableau de bord).
- **Job** : `RappelDesRegularisationsPlanifie` est déjà dans `contexts/seance` (SE4). `NotificationService` reste injecté dans `SeanceRestController`.
- **bc-seance** : 36 fichiers main, 7 de test ; côté plateforme `SeanceJpaEntity`, `SeanceRepositoryAdapter`, `SeanceJpaRepository`, `SeancePlaceJdbcAdapter`, `SeancePlanningJdbcAdapter`, adaptateurs de volets et d'analyses.
- **Lecteurs et écrivains SQL de `seances` hors contexte** : 21 fichiers, 37 occurrences (`FROM|JOIN|UPDATE|INTO seances`), dont 18 fichiers consommateurs réels (3 sont des adaptateurs du langage publié ou la route résiduelle). Répartition : Facturation 5 fichiers (13 occ., dont 5 UPDATE), Stock 2, Absences 3 (5), Planification 1, Dossier médical 1 (2), Pilotage direction 3 (5), `PatientStatsQueryService` 1, `CahierDialyseReportService` 1, migration 3 (rollback, import historique, usages de forfait), plus `SeanceRestController` (4).
- **Passage par `seance.v1`** : AUCUN des 18 consommateurs. Les 12 interfaces de `seance.v1` (`FichesFournies`, `ForfaitsFournis`, `HistoriqueFourni`, `PlaceFournie`, `PlanningFourni`, `RappelsFournis`, `SaisiesDeSeanceFournies`, `VoletsFournis`, etc.) n'ont pour implémentations que des adaptateurs `platform/infrastructure/acl/seance/*Legacy`.
- **Garde** : `TableDesSeancesTest` est toujours `@Disabled` (aveugle à `platform/src/main`, `/acl/`, `/persistence/`).
- **Colonnes de place de `patients`** (`salle_id`, `position_id`, `generateur_id`) lues et écrites hors Séance : `SeancePlaceJdbcAdapter`, `Planification`, `ReferentialAdminTables` (usages).

## Ce qui est verrouillé par les filets existants (non refait ici)

SE1 à SE6 (écritures via `contexts/seance`, 9 familles de goldens), DM1 (volet paramédical, garde de centre des routes résiduelles), Absences A4 (dashboard détails/exports), PC1 (dashboard deux mois), Facturation F2 à F6 et retrait, Stock S4/S6b, Planification P4 à P7, Pilotage PD1 à PD5. Les nouveaux scénarios SE7a ne les répètent pas.

## Sous-slices

Voir `.ddd/migration/slices/seance-se7a` à `seance-se7f`. Non couvert à ce jour : tout SE7b à SE7f (voir leurs fichiers pour le risque et les filets à compléter).
