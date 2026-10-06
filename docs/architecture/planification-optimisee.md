# Planification optimisée (Timefold) — architecture, modèle et traçabilité

> Règles de gestion : `docs/reference/regles-de-gestion/05-planning-placement.md` §5.9 (RG-PLN-080 à 092) et RG-INF-070.
> Décision d'architecture : [ADR-003](adrs/ADR-003-optimisation-planning-timefold.md).

## Traçabilité Front ↔ Back (AGENTS.md §18)

| Écran / action             | Composant Angular                                           | Store                                         | Service HTTP                                            | Endpoint REST                                                | Application                                      | Domaine                                                                                              | Persistance                                                  |
|----------------------------|-------------------------------------------------------------|-----------------------------------------------|---------------------------------------------------------|--------------------------------------------------------------|--------------------------------------------------|------------------------------------------------------------------------------------------------------|--------------------------------------------------------------|
| Lancer un calcul           | `PlanningOptimisationComponent` (`/seances/optimisation`)   | `OptimisationStore.lancer`                    | `PlanningOptimisationApiService.lancer`                 | `POST /api/v1/planning/optimisations`                        | `OptimisationPlanningService.lancer`             | `ParametresOptimisation`, `EmpreinteOptimisation`, `OptimiseurPlanningPort` (adaptateur Timefold)    | `OptimisationDonneesJdbcAdapter`, `OptimisationRunJdbcAdapter` |
| Suivre / rouvrir           | idem                                                        | `OptimisationStore.suivre / ouvrir`           | `consulter`                                             | `GET /api/v1/planning/optimisations/{id}`                    | `OptimisationPlanningService.consulter`          | `RunOptimisation`                                                                                    | `OptimisationRunJdbcAdapter.findById`                        |
| Historique paginé          | idem (`mat-paginator`)                                      | `OptimisationStore.chargerHistorique`         | `historique`                                            | `GET /api/v1/planning/optimisations?page&size`               | `OptimisationPlanningService.historique`         | `RunOptimisation` (sans détail), `PagedResult`                                                       | `OptimisationRunJdbcAdapter.findPaged`                       |
| Arrêt anticipé             | idem                                                        | `OptimisationStore.arreter`                   | `arreter`                                               | `POST /api/v1/planning/optimisations/{id}/arret`             | `OptimisationPlanningService.arreter`            | `OptimiseurPlanningPort.arreter`                                                                     | —                                                            |
| Appliquer la proposition   | idem (administrateur, confirmation `ConfirmDialogComponent`) | `OptimisationStore.appliquer`                 | `appliquer`                                             | `POST /api/v1/planning/optimisations/{id}/application`       | `OptimisationApplicationService.appliquer`       | `VerificationDeplacementsService`, `PlanificationAffectationService.verifier`, `AffectationInfirmier` | `PlacementPatientJdbcAdapter`, `AffectationInfirmierJdbcAdapter`, `RemplacementInfirmierJdbcAdapter` |

Toute requête porte le centre de la session (`CenterAccessGuard`) ; aucune donnée d'un autre centre n'est lue, calculée
ni appliquée (tests d'intégration `PlanningOptimisationIntegrationTest`). Aucune mise en cache : une proposition reflète
l'instant du calcul.

## Pipeline

```
lancer ──► OptimisationDonneesPort.charger (centre, horizon) ──► empreinte SHA-256 ──► RunOptimisation EN_COURS
                                         │
                                         ▼  (thread du moteur, 1 calcul / centre)
                    ┌──────────────── TimefoldOptimiseurAdapter ────────────────┐
                    │ phase PATIENTS   PlanPatients  → PatientsConstraintProvider │  PATIENTS, COMPLET
                    │ phase INFIRMIERS PlanInfirmiers → InfirmiersConstraintProvider │ ROULEMENT, COUVERTURE, COMPLET
                    │ indicateurs avant / après (domaine) ◄ placements + vacations │
                    └──────────────────────────────┬─────────────────────────────┘
                                                   ▼
                              Ecouteur.termine → RunOptimisation TERMINEE (résumé + détail JSON)
appliquer ──► empreinte identique ? ──► règles de planification revérifiées ──► tout ou rien
```

## Modèle d'optimisation

### Phase patients — `PlanPatients`

- **Entité** `PlacementPatient` : un patient actif (placé ou en attente) ; **variable** `poste` = un `PosteSerie`
  (générateur × créneau), nulle = non placé. Les jours de dialyse sont des données et ne changent jamais.
- **Domaine de valeurs** réduit par isolement : un patient à risque ne voit que les postes des salles d'isolement,
  un patient sans risque que les autres.
- **Dures** : un générateur ne sert qu'un patient par jour et créneau (`GENERATEUR_DOUBLE`), isolement (`ISOLEMENT`).
- **Moyenne** : `PATIENT_NON_PLACE`.
- **Souples** (poids dans `PoidsOptimisation`) : `INFIRMIERS_REQUIS` (100 par vacation), `SALLES_OUVERTES` (30),
  `GENERATEURS_UTILISES` (10), `RESERVE_SECOURS` (50, 1 secours pour 8), `STABILITE_PATIENTS` (créneau 3, salle 2,
  générateur 1, × stabilité × 6).

### Phase infirmiers — `PlanInfirmiers`

- **Entité** `Vacation` : une vacation exigée sur une case (salle, créneau, date) ; **variable** `infirmier`
  (nulle = non pourvue). Nombre de vacations par case = ⌈patients / ratio⌉ (même calcul que `PresenceInfirmierService`).
- **Candidats** filtrés par vacation : ni absent, ni non habilité en isolement, ni déjà prévu sur le créneau.
- **Épinglage** (couverture) : les infirmiers déjà prévus sont `@PlanningPin` ; seules les vacations manquantes bougent.
- **Dures** : pas deux salles au même créneau (`VACATION_DOUBLE_CRENEAU`), plafond par jour (`MAX_VACATIONS_JOUR`).
- **Moyenne** : `VACATION_NON_POURVUE`.
- **Souples** : `DEPASSEMENT_HEBDOMADAIRE` (500), `EQUITE_CHARGE` (somme des carrés), `INFIRMIERS_MOBILISES` (objectif
  `ECONOMIE`), `DOUBLE_VACATION`, `CONTINUITE_SALLE`, `AFFINITE` (salle / créneau connus), `STABILITE_ROULEMENT`,
  `QUALIFICATION` (au moins un non aide-soignant par case).

## Pistes d'amélioration

1. **Optimisation conjointe patients + infirmiers** dans un seul modèle (la demande d'infirmiers dépend alors des
   placements dans le même calcul) : meilleure qualité, au prix d'un modèle plus complexe.
2. **Choix des jours des nouveaux patients** : proposer, pour un patient sans jours prescrits, le schéma de jours
   (lundi-mercredi-vendredi…) qui équilibre le mieux la charge ; aujourd'hui les jours sont une donnée.
3. **Maintenance GMAO planifiée** : tenir compte des interventions préventives datées (`gmao_interventions`) pour
   ménager des générateurs de remplacement à la date voulue, et alerter quand une maintenance tombe sur un créneau plein.
4. **Séances de remplacement datées** : en cas de panne ou de maintenance un jour donné, proposer un déplacement
   temporaire des patients concernés sans toucher à leur place habituelle.
5. **Contraintes de personnel** : repos hebdomadaire et quotas d'heures, préférences et compétences par poste (par ex.
   abord vasculaire, patients pédiatriques), contrats à temps partiel.
6. **Préférences des patients** : créneau préféré, transport partagé, affinités entre patients.
7. **Explicabilité** : détail des contraintes responsables du score (nécessite l'analyse de score Timefold, Enterprise,
   ou un recalcul par contrainte côté domaine).
8. **Replanification continue** : relance automatique nocturne et notification quand une proposition apporte un gain
   significatif ou quand une absence crée un sous-effectif.
