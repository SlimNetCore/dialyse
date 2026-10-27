---
status: characterized (filet vert sur le LEGACY, rien de commité)
date: 2026-10-27
sources: .ddd/tactical/planification.md (P6), .ddd/migration/roadmap.md (§1 sexies, dette P4→P8), ADR 0049 à 0059, docs/reference/regles-de-gestion/05-planning-placement.md (RG-PLN-045), known-bugs.md KB-PLN-67 à 76
---

# Filet de caractérisation — planification-p6-planning-semaine-lectures

Refactoring sur place (ADR 0024). **Aucun seam, aucune modification de production** (`seamAllowed` vide). Seuls des fichiers de test et des goldens sont ajoutés.

## Périmètre

| Point d'entrée | Rôles observés | Lit / écrit |
|---|---|---|
| `GET /api/v1/planning/semaine` (`PlanningSemaineRestController` → `PlanningSemaineQueryService` → `PlanningSemaineService` ← `PlanningDonneesJdbcAdapter`) | 11 profils + anonyme | lecture seule : `salle`, `position_creneau`, `gmao_equipements`, `patients`, `serologies_patient`, `planning_parametres`, `center_holiday`, `center_closure_day`, `deplacement_temporaire`, `seances` |
| `GET /api/v1/direction/capacite` (`DirectionCapaciteQueryService`) | 11 profils + anonyme | lecture : `planning_parametres`, `position_creneau`, `patients`, `gmao_equipements` (via `GenerateursInstalles`) |
| Validation d'une séance (`SeanceUseCase.validate` → `SeancePlaceJdbcAdapter`) | cas d'usage (pas la route) | lit `deplacement_temporaire`, `patients` ; écrit `seances.salle_id/creneau_id/generateur_id` |

## Lecteurs SQL hors module des colonnes de place (`patients`), `salle`, `planning_*`, `deplacement_temporaire` (constat du 2026-10-27)

| Contexte | Fichier:ligne | Lit | Filet |
|---|---|---|---|
| Séance | `SeancePlaceJdbcAdapter.java:29-34` | `deplacement_temporaire`, `patients.salle_id/position_id/generateur_id` | **P6** (`LecturesDePlaceConsommeesParLaSeance…`) |
| Séance | `SeancePlanningJdbcAdapter.java:46-47` | `patients.jour_*`, `en_sommeil`, `etat_patient`, `date_evenement_etat`, `date_admission`, `center_holiday`, `center_closure_day` | existant `SeanceScanIntegrationTest` (non rejoué) |
| Séance | `SeanceRestController.java:855-872` (tableau de bord, jours de dialyse), `:1221-1227` (jour prévu) | `patients.jour_*` | non couvert ici (filet Séance) |
| Séance | `SeanceRestController.java:1017`, `:873` | `patients` (noms) | non couvert ici |
| Absences | `PatientsAttendusJdbc.java:53` | `patients.jour_*`, `en_sommeil`, `etat_patient`, `date_admission`, `center_holiday`, `center_closure_day` | existant Absences A3 (`PatientAttenduCharacterizationIntegrationTest`) |
| Direction | `DirectionCapaciteQueryService.java:43,49,53` | `position_creneau`, `patients`, `planning_parametres` | **P6** (`CapaciteDesCentres…`) + `DirectionCapaciteIntegrationTest` |
| Direction | `DirectionGmaoQueryService.java:112` | `patients.generateur_id` | existant `DirectionGmaoCharacterizationIntegrationTest` |
| Direction | `DirectionBreakdownQueryService.java:163-237`, `DirectionDashboardQueryService.java:115` | `patients` (effectif, hors colonnes de place) | hors P6 |
| Patient | `GenerateurImpactJdbcAdapter.java:27` | `patients.generateur_id` | P5 |
| Patient | `MouvementPatientJdbcAdapter.java:25-27,44,79` | `mouvement_patient` joint `salle`, `position_creneau` | P5 (mouvements) |
| Plateforme | `ReferentialRepositoryAdapter.java:56,64`, `ReferentialAdminTables.java:29` | `salle`, `position_creneau` (usages), `patients.position_id` | filets référentiel |
| Plateforme (P8) | `OptimisationDonneesJdbcAdapter.java:79,137-155`, `DeplacementTemporaireJdbcAdapter.java:31-49` | `patients`, `deplacement_temporaire` | P8 (la lecture `entre` est exercée par la semaine) |
| Planification legacy | `PlanningDonneesJdbcAdapter.java`, `SalleVueJdbcAdapter.java`, `PlacementPatientJdbcAdapter.java:28,42,63` | place, salles | P5 + P6 |

## Point d'observation

HTTP (MockMvc, contexte Spring complet, sécurité réelle + `RoleScopeFilter`, H2 `test`), état SQL des séances, audit, diffusions, notifications. Aucun mock. Harnais : `…/characterization/planification/SemaineCharacterizationSupport.java` (au-dessus de P5, P3, P2, P1). Commande :
`./mvnw -o -q -pl platform -am test -Dtest='com.hemodialyse.backend.characterization.planification.semaine.*IntegrationTest' -Dsurefire.failIfNoSpecifiedTests=false` (`-Dcharacterization.approve=true` pour régénérer après revue ; `-Dsurefire.runOrder=random`). Goldens : `backend/platform/src/test/resources/characterization/planification/p6-semaine/` (80 fichiers).

## Scénarios (79, 7 classes)

| Classe | Nb | Verrouille |
|---|---|---|
| GrilleDeLaSemaine | 14 | forme complète, décor standard, début le dimanche, semaines passées et futures (-60 à +400), sans date, centre vide / sans salle / sans créneau, tri salles et créneaux, capacité (générateurs en service seulement, jour fermé = 0), noms et tri, patients non affichés, 7 jours, lecture seule et `no-store` |
| JoursFermesEtPatientsDeLaSemaine | 18 | jours d'ouverture (formes, minuscules = 400), férié / fermeture exceptionnelle / cumul / motif absent / hors semaine / autre centre, à replanifier, sommeil, transféré / décédé / greffé / guéri, sortis avant la semaine, sortie lointaine, admission en cours de semaine ou plus tard, séjours limités, états inconnus, risque infectieux |
| ConflitsDeLaSemaine | 9 | les six conflits, générateurs indisponibles (hors service, maintenance, réformé, autre salle, inconnu), isolement sans salle d'isolement, générateur mixte, sortie |
| DeplacementsEtSeancesRealisees | 15 | déplacement temporaire (cible, cible inconnue, générateur inconnu, sans effet, conflictuel, unicité), séances réalisées (hors planning, statuts, place non mémorisée, sans case, périmètre, sans conflit, date de référence) |
| LecturesDePlaceConsommeesParLaSeance | 6 | place figée à la validation (habituelle, déplacement prioritaire, sans place, déplacement ignoré, générateur hors service), relecture par la semaine |
| EntreesAutorisationsEtAuditDeLaSemaine | 7 | date et centre illisibles, garde de centre (SUPERADMIN, sans centre), isolation de centre, méthodes / chemins, matrice 11 profils + anonyme (x2), audit et diffusions |
| CapaciteDesCentres | 10 | forme complète, file masquée, paramètres par défaut, file active, générateurs comptés, périodes (422 / 500), période par défaut, société sans centre / centre inactif, autre société, matrice 11 profils + anonyme |

## Non-déterminisme

Dates par alias de la seule famille `<S1.LUN>` (`<J-n>` pour la capacité) ; aucune date réelle écrite (le harnais échoue si une date non nommée reste) ; admission fixée à 2000-01-01 pour les semaines très anciennes ; aucun jour de mois fixe ; attente de minuit (UTC et Alger) et scénarios ignorés entre 23:00 et 24:00 UTC (hérité de P3). Identifiants de séance déterministes (hachés), tri SQL par date puis patient. Lignes `centers` / `societes` / `app_user` de la capacité créées ou rattachées puis rétablies.

## Stabilité

Trois exécutions de la suite des 79 scénarios : ordre par défaut, puis deux ordres aléatoires : 0 échec, 0 flaky.

## Couverture et mutation

Pas de JaCoCo ni de PIT dans la chaîne Maven hors ligne : aucune mesure de branches ni de mutants. Couverture raisonnée : toutes les branches de `PlanningSemaineService.construire` (fermeture, déplacement, séances réalisées, six conflits, générateur mixte), de `PlanningDonneesJdbcAdapter.charger(UUID, LocalDate)` et de `DirectionCapaciteQueryService` ont un scénario. Fichiers ajoutés à `covered.json` (10) : `PlanningSemaineRestController`, `PlanningSemaineQueryService`, `PlanningSemaineService`, `PlanningSemainePort`, `PlanningSemaine`, `DeplacementTemporaire`, `CapaciteTheoriqueCalculator`, `SeancePlacePort`, `PlaceSeance`, `SeancePlaceJdbcAdapter`. Déjà couverts (P5 ou avant) et exercés aussi ici : `PlanningDonneesJdbcAdapter`, `SalleVueJdbcAdapter`, `Planning`, `DirectionCapaciteQueryService`.
**Non ajoutés** (exercés partiellement) : `DeplacementTemporaireJdbcAdapter` (seule la lecture `entre`), `DeplacementTemporairePort`, `DirectionDashboardRestController` (seule `/capacite`), `DirectionAccessGuard`, `FinOccupation` (partagé avec Patient).

## Limites (NON verrouillé)

- Écriture des déplacements temporaires et application d'optimisation : filet P8. Libération des places 04:00 : P7.
- Route `POST /seances/{id}/valider` (verrou des séances passées, notifications) : validation observée par le cas d'usage seulement. Création / scan de séance (`SeancePlanningJdbcAdapter`) : filet Séance existant, non rejoué ; tableau de bord des séances (`SeanceRestController`) : filet Séance.
- Absences (`PatientsAttendusJdbc`) et Direction GMAO : filets existants, non rejoués.
- Fuseau : fenêtre 23:00-24:00 UTC non rejouée (la date par défaut est l'UTC) ; PostgreSQL (H2 seulement) ; volumétrie (~300 places) ; concurrence lecture / écriture.
- Valeur masquée de l'anonymat : seuil 1 à 4 vérifié sur 2 patients seulement.

## Questions de décision pour l'utilisateur

1. Codes HTTP : entrées illisibles 500 → 400 (KB-PLN-67), méthode / chemin inconnus 500 → 405 / 404 (KB-PLN-68), anonyme 403 → 401 (KB-PLN-70).
2. Fuseau : date par défaut et période par défaut au fuseau du centre (KB-PLN-69), comme P3 à P5.
3. Droits : médecin seul lit la semaine mais pas les salles (KB-PLN-64 / 70) ; faut-il laisser la Direction lire la semaine ? ; le total de la capacité peut révéler une file masquée (KB-PLN-75).
4. Lectures publiées : un seul calcul de présence (sommeil, sortis, admission) partagé par la semaine, les Absences et la Direction (KB-PLN-71, RG-PLN-045) ; port `PlacesDesPatients` à définir (patients attendus + place d'un jour).
5. Déplacement temporaire : tolérance silencieuse d'une cible incohérente (KB-PLN-73) à conserver ou à refuser à l'écriture (P8) ? place figée sans contrôle du générateur (KB-PLN-74) ?
6. Jours fermés : capacité à zéro pour un jour férié (KB-PLN-72) ; valeur de jours d'ouverture en minuscules (400).
7. Générateurs de la capacité Direction : compter hors service et maintenance (KB-PLN-76) ?
