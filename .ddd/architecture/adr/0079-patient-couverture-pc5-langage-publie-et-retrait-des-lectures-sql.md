---
status: accepted
date: 2026-11-04
decision-makers: [utilisateur (décision déléguée à l'assistant, jalons acceptés le 2026-11-04), architecte]
context-scope: patient-couverture
related: ADR 0024 (refactoring sur place), 0052 (la place est écrite par la Planification), 0064, 0073, 0074, 0076, 0077, 0078 ; `.ddd/migration/slices/patient-couverture-pc5-lectures-publiees.md`
---

# 0079 - Patient et couverture, PC5 : langage publié `patientcouverture.v1` et retrait des lectures SQL hors contexte

## À confirmer avec les métiers
Aucun comportement métier ne change en PC5 (refactoring pur, parité stricte). Rien à faire valider par les experts métier. Les points d'argent reproduits des ADR 0077 et 0078 (forfait applicable sans regarder le statut, éligibilité à la facturation exigeant l'attestation même pour un vacancier, compteurs « à échéance » comptant les lignes échues) restent À CONFIRMER avec les métiers : ils sont désormais portés, à l'identique, par le langage publié.

## Contexte
Après PC1 à PC4, le module `contexts/patient-couverture` possède les six tables (`patients`, `mouvement_patient`, `assure`, `assure_patient`, `prise_en_charge`, `attestation_droit`), mais 23 sources hors module les lisaient ou les écrivaient encore en SQL direct (liste blanche datée du 2026-11-02, PC1). PC5 pose le langage publié et migre les lecteurs qui peuvent l'être sans changer le moindre résultat.

## Décision
1. **Trois ports publiés** (`publishedlanguage`, convention de `PresenceDesPatients` : interfaces, `UUID` et `java.time`, bornées au centre, records imbriqués), des **lectures par intention**, SANS règle nouvelle :
   - `FichesDesPatients` : `existe`, `noms`, `situation` (inconnu, suivi, parti) ;
   - `CouvertureDesPatients` : `forfaitApplicable`, `prisesEnChargeTarifees`, `causeDeNonFacturation`, `pecValidee` ;
   - `CompteursDeCouverture` : les six compteurs du tableau de bord (prises en charge par statut, à échéance, attestations, à échéance, patients créés, effectif d'un jour).
2. **Adapters dans le module** (`FichesPublieesJdbc`, `CouvertureDesPatientsJdbc`, `CompteursDeCouvertureJdbc`) : le SQL, l'ordre et les tris des lecteurs d'origine sont repris À L'IDENTIQUE (y compris les particularités : la période du tableau de bord se compare à `created_at` avec des dates, l'effectif d'aujourd'hui est celui du jour d'UTC). **Contrat** : un même jeu de 14 tests passe sur le double en mémoire, sur H2 et sur PostgreSQL 16 (`patient-couverture-pc5-pg16-rejeu.sh`).
3. **Six lecteurs migrés** : Absences `PatientsDuCentreJdbc`, `NomsDesPatientsJdbc`, `ForfaitsDesPatientsJdbc` (devenus des traducteurs minces `…DeLaFiche` / `…DeLaCouverture`), la partie PEC de Facturation `SeancesFacturablesJdbc`, la Séance `SeanceBillingEligibilityJdbcAdapter` (remplacé par `SeanceBillingEligibilityParLaCouverture`, le port `SeanceBillingEligibilityPort` n'est pas modifié) et `DashboardRestController`. Dépendances Maven ajoutées : Absences, Facturation (la plateforme l'avait déjà) vers `contexts-patient-couverture`, sans cycle (le module dépend de la Planification, jamais d'Absences ni de Facturation).
4. **Parité stricte** : tous les filets (PC1 à PC4, Planification, Absences, Facturation, Séance, Direction, Stock) restent verts SANS modifier un seul golden ; le build complet est vert.
5. **Garde réactivée** : `TableDuPatientCouvertureTest` n'est plus `@Disabled`. Elle scanne tout le backend hors du module, y compris les requêtes sur plusieurs lignes ; les 18 lecteurs et écrivains restants sont une LISTE BLANCHE NOMMÉE, DATÉE, avec raison et échéance ; une entrée morte ou un nouveau lecteur fait échouer le test.

## Jalons acceptés (les 18 sources de la liste blanche et leur raison)
| Jalon | Raison | Échéance |
|---|---|---|
| Planification `NomsDesPatientsJdbc`, `PatientsDuCentreJdbc` | dépendance Maven CIRCULAIRE : le module dépend de la Planification (la place, ADR 0052) | extraction des interfaces publiées dans un module d'API partagé ; les ports `noms` et `situation` sont prêts |
| Planification `PlacesDuCentreJdbc`, `PlacesTenuesJdbc`, `MouvementsDesPatientsJdbc` (écrivent), `PlanningDesPlacesDuCentreJdbc`, `SejoursDesPatientsJdbc`, `SeancesRealiseesJdbc`, `LectureDesPreferencesJdbc` ; plateforme `SeancePlanningJdbcAdapter` | colonnes de PLACE de `patients`, propriété de la Planification (ADR 0052) | aucune (inverser la propriété est hors périmètre) |
| Planification `ContraintesDesPatientsJdbc` | lecture croisée Planification / dossier médical, pas une lecture de fiche | reprise du dossier médical |
| Facturation `SeancesFacturablesJdbc` (jointure séances-patients), `SituationsDesFacturesJdbc` | jointures avec les tables de Facturation dans un tri / un `GROUP BY` ; les découper changerait le résultat (KB-FAC-49) | reprise de Facturation |
| Direction `DirectionDashboardQueryService`, `DirectionBreakdownQueryService` | agrégats avec le prédicat d'effectif dans des `GROUP BY` | reprise du Pilotage |
| `SeanceRestController` (1300 lignes) | contrôleur de la Séance (tableau de bord, journal) hors ports | reprise de la Séance |
| `contexts/seance` `PecValidationJdbc` | code MORT : module gelé et hors réacteur (ADR 0074) ; le port `pecValidee` est prêt | reprise ou suppression de la Séance |
| `MigrationRollbackJdbcAdapter` (+ `HistoricalRecordJdbcAdapter`, migrateurs, `bc-assure`) | outil de migration, écrit en bloc hors verrou du centre, à lancer hors activité (ADR 0077 point 12, ADR 0078 point 10) | reprise de la migration par le module (décision (3) : `bc-assure` reste tant que les migrateurs JPA le référencent) |
| `JourDuCentreLegacy` (hors tables du patient) | `organisation.v1` ne publie pas de lecture du fuseau (liste blanche de `TableDeLOrganisationTest`) | `organisation.v1` |
| Types historiques non SQL : entités JPA `PatientJpaEntity`, `AssureJpaEntity`, `AssurePatientJpaEntity`, `PatientRepositoryAdapter`, `PatientMapper`, `bc-patient` (utilisés par la Séance, FHIR, le cahier de dialyse, KDIGO…) | le modèle patient historique reste le modèle de lecture de ces contextes | reprise de ces contextes |

## Alternatives écartées
- Migrer aussi la Planification en déplaçant les interfaces publiées vers un module partagé : refactoring de réacteur hors périmètre.
- Découper les jointures `GROUP BY` de Facturation et de la Direction : risque de changer tris, regroupements et arrondis.

## Conséquences
- Le module est entièrement repris (PC1 à PC5) ; la dette est la liste blanche ci-dessus, gardée par un test.
- Aucun golden créé ni modifié ; le code nouveau est couvert par PIT (adapters : 98 % de mutants tués) et par le contrat sur trois implémentations.
