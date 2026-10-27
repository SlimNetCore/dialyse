---
status: characterized (filet vert sur le LEGACY, rien de commité)
date: 2026-10-27
sources: .ddd/tactical/planification.md (P7, B7), .ddd/migration/roadmap.md (§1 sexies, dette P7), ADR 0024, 0049 à 0060, docs/reference/regles-de-gestion/04 (RG-PAT-014, 030..034) et 05, known-bugs.md KB-PLN-78 à 88
---

# Filet de caractérisation — planification-p7-liberation-places

Refactoring sur place (ADR 0024). **Aucun seam, aucune modification de production** (`seamAllowed` vide). Seuls des tests et des goldens sont ajoutés.

## Périmètre et point d'observation

Entrée : `LiberationPlacesScheduler.libererPlaces()` (la méthode du cron `0 0 4 * * *`), appelée directement, contexte Spring complet, H2 `test`, aucun mock. Aussi : `LiberationPlacesService` (`centres()`, `libererCentre`), la libération à l'enregistrement d'une fiche (`PUT /api/v1/patients/{id}` -> `PatientApplicationService.updatePatient` -> `libererALEnregistrement`), `GET /api/v1/mouvements-patients`. Observation : colonnes de place et jours de `patients`, `mouvement_patient`, fiche lue en HTTP, grille des propositions (P5), port de lecture `PatientsAttendus` (Absences), compteurs de notifications / audit / diffusions / événements.

Commande : `./mvnw -o -q -pl platform -am test -Dtest='com.hemodialyse.backend.characterization.planification.liberation.*IntegrationTest' -Dsurefire.failIfNoSpecifiedTests=false` (`-Dcharacterization.approve=true` pour régénérer après revue ; `-Dsurefire.runOrder=random`). Harnais : `…/characterization/planification/LiberationCharacterizationSupport.java` (au-dessus de P6). Goldens : `backend/platform/src/test/resources/characterization/planification/p7-liberation/` (`nuit`, `centres`, `enregistrement`).

## Scénarios (29, 3 classes)

| Classe | Nb | Verrouille |
|---|---|---|
| NuitDeLiberationDesPlaces | 11 | transféré / guéri (lendemain de la date), décédé / greffé (jour même), sortis sans date (immédiat), séjours limités (fin incluse, sans fin jamais), états jamais libérés (permanent, non renseigné, inconnu, minuscules, sommeil), place partielle et jours (aucun, tous), patient sans place, forme du mouvement `PLACE_LIBEREE` (automatique, daté du jour, état conservé, place quittée), idempotence, replacement puis nouvelle libération, service par centre |
| CentresEtEchecsDeLaLiberation | 9 | isolation des deux centres, centre inactif, centre fermé / férié, **échec d'un patient (transaction unique du centre, B7)** avec chacun des trois patients en échec puis reprise, échecs répétés, fuseau UTC du job, calendrier de nuit (annotations `@Scheduled`), absence de verrou de placement, libération silencieuse |
| EnregistrementEtLecturesDeLaLiberation | 9 | libération à l'enregistrement (échéance J-1, 13 combinaisons état / date), sortie sans date refusée (422), nuit suivante, second enregistrement (double mouvement), liste des mouvements (4 lectures), matrice 11 profils + anonyme + routes de libération inexistantes, fiche rafraîchie, grille des propositions, **ordre de nuit** 02:30 -> 04:00 vu par `PatientsAttendus` |

## Lecteurs et écrivains hors module (constat du 2026-10-27)

Écrivains des colonnes de place / jours (`patients`) : `LiberationPlacesService` (ce filet), `PlacementPatientJdbcAdapter.java:42,48` (P5), `PlacesDuCentreJdbc.java:88` (contexte planification, P5), fiche patient (`PatientApplicationService.updatePatient` -> `libererALEnregistrement`, `PatientRepositoryAdapter.save` : écrit la ligne entière), optimisation (P8, `OptimisationApplicationService`). Écrivain de `mouvement_patient` : `PatientApplicationService` (admission, changement d'état), `LiberationPlacesService` ; seul `MouvementPatientJdbcAdapter.save` écrit.

Lecteurs de la règle de sortie (`FinOccupation`) : `PlanningDonneesJdbcAdapter` (semaine, P6), `OptimisationDonneesJdbcAdapter` (P8), `EffectifSql` (effectifs Direction), `SeanceRestController`, `PresenceDesPatients` (planification, via `PatientsAttendusJdbc` : Absences). Lecteurs de `mouvement_patient` : `MouvementPatientJdbcAdapter.findPaged` (route `GET /mouvements-patients`). Voisins de nuit lisant la place : `ReplanificationAutomatiqueScheduler` 02:30 (P8, sans zone), `ControleDeNuitDesAbsences` 02:30 (Algiers). Filets des lecteurs : P6 (semaine, capacité, séance), Absences A3, Direction (existants, non rejoués).

## Non-déterminisme

Dates par alias `<J-4>`...`<J+4>` (jours -7 à +7 nommés par scénario, le harnais échoue si une date réelle reste) ; aucun scénario ne dépend du jour de la semaine (jours de dialyse fixes lundi + mercredi, ou les sept jours), du jour du mois ni de l'instant ; garde de proximité de minuit (UTC et Alger) et scénarios ignorés entre 23:00 et 24:00 UTC (hérités de P3), sauf le scénario `fuseau` qui ne lit que le jour UTC. Identifiants de patients déterministes (hachés par alias), horodatage de création des mouvements non verrouillé. L'ordre de traitement des patients d'un lot n'étant pas garanti, le scénario d'échec fait échouer chacun des trois patients tour à tour. Le garde-fou d'échec est une contrainte `CHECK` posée sur `mouvement_patient` par le test et retirée à la fin de chaque test (aucun code de production touché).

## Stabilité

3 exécutions de la suite des 29 scénarios (ordre par défaut puis deux ordres aléatoires) : 0 échec, 0 flaky.

## Couverture et mutation

Couverture de branches non mesurée par outil (pas de JaCoCo ni de PIT configurés sur le module) ; couverture raisonnée : chaque branche de `FinOccupation.dernierJourOccupe` / `placeLiberee`, `MouvementsPatient.placeAReprendre` (affecté / non affecté, trois colonnes), `LiberationPlacesService` (centre, échéance, échec), `LiberationPlacesScheduler` (liberees > 0 et catch) est exercée par au moins un scénario. Mutation non exécutée.

## Limites (NON verrouillé)

- Le cron lui-même n'est pas déclenché par le temps (appel direct de la méthode) ; la zone du cron est verrouillée par lecture de l'annotation.
- Course entre la libération et l'enregistrement d'une fiche sur le même patient (perte de mise à jour) : non déterministe, non observée (KB-PLN-84). Un verrou de ligne tenu par une autre transaction n'est pas rejoué.
- Un échec autre que l'écriture du mouvement (échec de `patients.save`, lecture d'une ligne illisible) n'est pas rejoué : même chemin d'exception, résultat supposé identique.
- Le fuseau du centre n'est pas rejoué (fenêtre 23:00-24:00 UTC) : le legacy lit toujours le jour UTC.
- Patient.java : seul `libererPlacement` (lignes 122-131) est verrouillé par ce filet ; le reste de la fiche relève de P5. `PatientApplicationService.java` n'est PAS enregistré dans `covered.json` (seul l'appel `libererALEnregistrement` est exercé, ligne 107) : le seam de la fiche demandera son inscription ou un accord.
- `ControleDeNuitDesAbsences` n'est pas exécuté : l'ordre de nuit est observé par `PatientsAttendus` (les jours attendus avant / après la libération).
- Mutants : non mesurés.

## Seams

Aucun nécessaire pour le filet. Pour la cible, deux points à trancher (aucun appliqué) : l'horloge du service (`Clock.systemUTC()` fixé dans le constructeur public) et l'auto-appel `libererSiEchue` (B7) : ils se corrigent dans le nouveau code (`Horloge`, transaction par patient).

## Bugs connus

KB-PLN-78 à 88 (`known-bugs.md`).
