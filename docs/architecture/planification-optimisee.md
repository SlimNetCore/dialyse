# Planification optimisée (Timefold) — architecture, modèle et traçabilité

> Règles de gestion : `docs/reference/regles-de-gestion/05-planning-placement.md` §5.9 (RG-PLN-080 à 100), RG-INF-070 et RG-INF-071.
> Décision d'architecture : [ADR-003](adrs/ADR-003-optimisation-planning-timefold.md).

## Traçabilité Front ↔ Back (AGENTS.md §18)

| Écran / action             | Composant Angular                                           | Store                                         | Service HTTP                                            | Endpoint REST                                                | Application                                      | Domaine                                                                                              | Persistance                                                  |
|----------------------------|-------------------------------------------------------------|-----------------------------------------------|---------------------------------------------------------|--------------------------------------------------------------|--------------------------------------------------|------------------------------------------------------------------------------------------------------|--------------------------------------------------------------|
| Lancer un calcul           | `PlanningOptimisationComponent` (`/seances/optimisation`)   | `OptimisationStore.lancer`                    | `PlanningOptimisationApiService.lancer`                 | `POST /api/v1/planning/optimisations`                        | `OptimisationPlanningService.lancer`             | `ParametresOptimisation`, `EmpreinteOptimisation`, `OptimiseurPlanningPort` (adaptateur Timefold)    | `OptimisationDonneesJdbcAdapter`, `OptimisationRunJdbcAdapter` |
| Suivre / rouvrir           | idem                                                        | `OptimisationStore.suivre / ouvrir`           | `consulter`                                             | `GET /api/v1/planning/optimisations/{id}`                    | `OptimisationPlanningService.consulter`          | `RunOptimisation`                                                                                    | `OptimisationRunJdbcAdapter.findById`                        |
| Historique paginé          | idem (`mat-paginator`)                                      | `OptimisationStore.chargerHistorique`         | `historique`                                            | `GET /api/v1/planning/optimisations?page&size`               | `OptimisationPlanningService.historique`         | `RunOptimisation` (sans détail), `PagedResult`                                                       | `OptimisationRunJdbcAdapter.findPaged`                       |
| Arrêt anticipé             | idem                                                        | `OptimisationStore.arreter`                   | `arreter`                                               | `POST /api/v1/planning/optimisations/{id}/arret`             | `OptimisationPlanningService.arreter`            | `OptimiseurPlanningPort.arreter`                                                                     | —                                                            |
| Appliquer la proposition   | idem (administrateur, confirmation `ConfirmDialogComponent`) | `OptimisationStore.appliquer`                 | `appliquer`                                             | `POST /api/v1/planning/optimisations/{id}/application`       | `OptimisationApplicationService.appliquer`       | `VerificationDeplacementsService`, `PlanificationAffectationService.verifier`, `AffectationInfirmier`, `DeplacementTemporaire` | `PlacementPatientJdbcAdapter` (place, jours), `AffectationInfirmierJdbcAdapter`, `RemplacementInfirmierJdbcAdapter`, `DeplacementTemporaireJdbcAdapter` |
| Préférences des patients   | `PlanningPreferencesComponent` (onglet Patients, `mat-paginator`) | `PreferencesPlanningStore.setPatientsPagination / enregistrerPreference` | `PlanningPreferencesApiService.patients / enregistrerPreference` | `GET` / `PUT /api/v1/planning/preferences/patients[/{id}]` | `PreferencesPlanningService` | `PreferencePatient`, `PreferencePatientPort` | `PreferencePatientJdbcAdapter` (`planning_preference_patient`) |
| Profils des infirmiers     | idem (onglet Infirmiers, `mat-paginator`)                    | `PreferencesPlanningStore.setInfirmiersPagination / enregistrerProfil` | `infirmiers / enregistrerProfil` | `GET` / `PUT /api/v1/planning/preferences/infirmiers[/{id}]` | `PreferencesPlanningService` | `ProfilInfirmier`, `CompetenceInfirmier`, `ProfilInfirmierPort` | `ProfilInfirmierJdbcAdapter` (`infirmier_profil_planning`) |
| Réglages du centre         | idem (onglet Réglages, modification administrateur)         | `PreferencesPlanningStore.enregistrerReglages` | `reglages / enregistrerReglages` | `GET` / `PUT /api/v1/planning/preferences/reglages` | `PreferencesPlanningService` | `ReglagesOptimisation`, `ReglagesOptimisationPort` | `ReglagesOptimisationJdbcAdapter` (`planification_reglages`) |
| Déplacement temporaire affiché | `PlanningSemaineComponent` (repère `swap_horiz`)        | `PlanningStore`                                | `PlanningApiService`                                    | `GET /api/v1/planning/semaine`                               | planning de la semaine                           | `PlanningSemaineService` (`OccupantPlanning.temporaire`)                                              | `PlanningDonneesJdbcAdapter` + `DeplacementTemporaireJdbcAdapter` |
| Replanification nocturne   | cloche (`NotificationBellComponent`, `OPTIMISATION_PROPOSITION`) | `NotificationBellStore`                   | WebSocket `/topic/center/{id}/events`                   | —                                                            | `ReplanificationAutomatiqueService` (`ReplanificationAutomatiqueScheduler`) | `MotifProposition`                                                              | `ReglagesOptimisationJdbcAdapter.centresEnReplanificationAuto` |

Toute requête porte le centre de la session (`CenterAccessGuard`) ; aucune donnée d'un autre centre n'est lue, calculée
ni appliquée (tests d'intégration `PlanningOptimisationIntegrationTest`). Aucune mise en cache : une proposition reflète
l'instant du calcul.

## Pipeline

```
lancer ──► OptimisationDonneesPort.charger (centre, horizon) ──► empreinte SHA-256 ──► RunOptimisation EN_COURS
                                         │
                                         ▼  (thread du moteur, 1 calcul / centre)
                    ┌──────────────── TimefoldOptimiseurAdapter ─────────────────────┐
                    │ phase PATIENTS    PlanPatients    → PatientsConstraintProvider    │ PATIENTS
                    │ phase INFIRMIERS  PlanInfirmiers  → InfirmiersConstraintProvider  │ ROULEMENT, COUVERTURE
                    │ phase COMPLET     PlanComplet     → CompletConstraintProvider     │ COMPLET (conjoint)
                    │ phase MAINTENANCE PlanMaintenance → MaintenanceConstraintProvider │ MAINTENANCE
                    │ indicateurs avant / après (domaine) ◄ placements + jours + vacations │
                    └──────────────────────────────┬──────────────────────────────────┘
                                                   ▼
                              Ecouteur.termine → RunOptimisation TERMINEE (résumé + détail JSON)
appliquer ──► empreinte identique ? ──► règles de planification revérifiées ──► tout ou rien
```

## Modèle d'optimisation

### Phase patients — `PlanPatients`

- **Entité** `PlacementPatient` : un patient actif (placé ou en attente) ; **variables** `poste` = un `PosteSerie`
  (générateur × créneau), nulle = non placé, et `schema` = ses jours de dialyse (`SchemaJours`) parmi ses schémas
  candidats : un seul (ses jours prescrits) sauf si sa préférence demande à l'optimisation de les choisir
  (`SchemasJoursService`, schémas bien espacés). Une heuristique de construction par classe d'entités (variables en
  produit cartésien) puis un recuit simulé.
- **Domaine de valeurs** réduit par isolement : un patient à risque ne voit que les postes des salles d'isolement,
  un patient sans risque que les autres.
- **Dures** : un générateur ne sert qu'un patient par jour et créneau (`GENERATEUR_DOUBLE`), isolement (`ISOLEMENT`).
- **Moyenne** : `PATIENT_NON_PLACE`.
- **Souples** (poids dans `PoidsOptimisation`) : `INFIRMIERS_REQUIS` (100 par vacation), `SALLES_OUVERTES` (30),
  `GENERATEURS_UTILISES` (10), `RESERVE_SECOURS` (50, 1 secours pour 8), `STABILITE_PATIENTS` (créneau 3, salle 2,
  générateur 1, × stabilité × 6), `ESPACEMENT_JOURS` (2 par point d'écart au meilleur schéma), `CHANGEMENT_JOURS`
  (20 × stabilité par jour actuel abandonné), `CRENEAU_PREFERE` (40), `TRANSPORT_PARTAGE` (15 par jour commun à deux
  patients d'un même transporteur sur des créneaux différents).

### Phase infirmiers — `PlanInfirmiers`

- **Entité** `Vacation` : une vacation exigée sur une case (salle, créneau, date) ; **variable** `infirmier`
  (nulle = non pourvue). Nombre de vacations par case = ⌈patients / ratio⌉ (même calcul que `PresenceInfirmierService`).
- **Candidats** filtrés par vacation : ni absent, ni non habilité en isolement, ni déjà prévu sur le créneau.
- **Épinglage** (couverture) : les infirmiers déjà prévus sont `@PlanningPin` ; seules les vacations manquantes bougent.
- **Dures** : pas deux salles au même créneau (`VACATION_DOUBLE_CRENEAU`), plafond par jour (`MAX_VACATIONS_JOUR`).
- **Moyenne** : `VACATION_NON_POURVUE`.
- **Souples** : `DEPASSEMENT_HEBDOMADAIRE` (500), `EQUITE_CHARGE` (somme des carrés), `INFIRMIERS_MOBILISES` (objectif
  `ECONOMIE`), `DOUBLE_VACATION`, `CONTINUITE_SALLE`, `AFFINITE` (salle / créneau connus), `STABILITE_ROULEMENT`,
  `QUALIFICATION` (au moins un non aide-soignant par case), `REPOS_HEBDOMADAIRE` (300 par jour au-delà de
  `7 − repos`), `QUOTA_HEURES` (100 par heure au-delà du quota : temps plein × taux d'activité), `COMPETENCE` (250 par
  compétence demandée absente de la case ; faits `ExigenceCompetence` dérivés des patients placés).

### Modèle conjoint — `PlanComplet` (périmètre `COMPLET`)

- **Entités** `PlacementPatient` et `Vacation` dans la même solution. Les vacations sont **potentielles** : par case
  ouverte de la semaine type, autant que la salle peut exiger d'infirmiers (⌈générateurs de la salle / ratio⌉), initialisées
  sur le roulement actuel.
- **Couverture** : un flux `Bilan(case, demande, offre)` concatène la demande (⌈patients placés / ratio⌉ par case,
  recalculée à chaque déplacement de patient) et l'offre (vacations tenues) ; `VACATION_NON_POURVUE` (moyenne) =
  demande − offre, `VACATION_INUTILE` (souple, 50) = offre − demande. Le poids d'une vacation inutile reste inférieur au
  gain d'une case fermée pour ne pas bloquer la recherche dans une « vallée ».
- `QUALIFICATION` est évaluée par case (au moins un non aide-soignant parmi les vacations tenues) ; les compétences
  demandées sont dérivées des patients placés dans la case pendant le calcul.
- Le résultat ne garde que les vacations utiles face aux placements retenus après vérification par le domaine.

### Phase maintenance — `PlanMaintenance` (périmètre `MAINTENANCE`)

- **Entité** `SeanceTemporaire` : une séance datée dont le générateur effectif est indisponible
  (`MaintenanceGenerateursService.seancesImpactees`, interventions GMAO planifiées ou en cours) ; **variable** `poste`
  parmi les places libres ce jour-là (générateurs disponibles, même catégorie d'isolement, non occupés à ce créneau,
  `postesOccupes`), nulle = sans solution.
- **Dure** `POSTE_TEMPORAIRE_DOUBLE` ; **moyenne** `SEANCE_SANS_SOLUTION` ; **souples**
  `CHANGEMENT_CRENEAU_TEMPORAIRE` (100), `CHANGEMENT_SALLE_TEMPORAIRE` (30).
- L'application enregistre des `DeplacementTemporaire` (table `deplacement_temporaire`, un par patient et date) ; le
  planning de la semaine place le patient dans sa case temporaire à cette date.

### Replanification automatique nocturne

`ReplanificationAutomatiqueScheduler` (02:30 UTC) → `ReplanificationAutomatiqueService.replanifier` pour chaque centre
volontaire : COUVERTURE (2 semaines) → MAINTENANCE (2 semaines) → PATIENTS (semaine suivante), chaque étape lancée par le
rappel de fin de la précédente (`OptimisationPlanningService.lancer(..., aLaFin)`). `MotifProposition` (stratégie par
périmètre) mesure l'intérêt d'une proposition ; une valeur positive déclenche `OPTIMISATION_PROPOSITION`.

## Pistes d'amélioration

Réalisées : modèle conjoint, choix des jours, maintenance GMAO et déplacements temporaires, contraintes de personnel,
préférences des patients (créneau, transport partagé), replanification nocturne notifiée.

1. **Explicabilité** : détail des contraintes responsables du score (nécessite l'analyse de score Timefold, Enterprise,
   ou un recalcul par contrainte côté domaine).
2. **Affinités entre patients** et préférences de salle (en plus du créneau et du transport).
3. **Annulation d'un déplacement temporaire** depuis le planning de la semaine (aujourd'hui il expire avec sa date ou est
   remplacé par un nouveau calcul de maintenance).
4. **Impact des déplacements temporaires sur le personnel** : enchaîner une couverture sur les dates concernées quand
   un déplacement change de créneau.
