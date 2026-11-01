---
slice: seance-se7-lectures-publiees
status: characterized
date: 2026-11-01
sources: [.ddd/migration/slices/seance-se7-lectures-publiees.md, .ddd/tactical/seance.md, ADR 0062]
---

# Filet de caractérisation — SE7 Lectures publiées et retrait du legacy Séance

## Point d'observation

HTTP (MockMvc sur le contexte Spring complet, sécurité réelle + `CenterAccessGuard`/`RoleScopeFilter`,
H2 du profil `test`) et état SQL de la table `seances` (et tables satellites `consommables_seance`,
`derogations_regularisation`). Aucun mock du code caractérisé. Tous les consommateurs aval (Facturation, Stock, Absences
patients, Dossier médical, Pilotage, Planification) lisent la Séance via
`seance.v1` et les ports publiés (`SeanceRepository`, `SeancesDuJour`, `SeancesValideesNonSignees`,
`FicheSeance`, `ConsommablesDeSeance`, `SeancesEnRetardValidation`) ; aucune requête SQL directe sur la
table `seances` hors du contexte `seance`.

## Consommateurs directs à remplacer (grep exhaustif de `seances` dans `src/main`)

| #  | Fichier (main)                                                                            | Requête / usage                                                | Port publié cible                              |
|----|-------------------------------------------------------------------------------------------|----------------------------------------------------------------|------------------------------------------------|
| 1  | `contexts/facturation/infrastructure/acl/legacyseance/SeancesFacturablesJdbc.java`        | lecture SQL sur `seances` (facturables par période/centre)     | `SeancesDeFacturation` / `SeancesFacturables`  |
| 2  | `contexts/facturation/infrastructure/acl/legacyseance/SeancesDeFacturationJdbc.java`      | lecture SQL sur `seances` (détail facturation)                 | `SeancesDeFacturation`                         |
| 3  | `contexts/facturation/infrastructure/acl/legacyseance/LiaisonDesSeancesJdbc.java`         | liaison séance ↔ facture                                       | `LiaisonDesSeances`                            |
| 4  | `contexts/stock/infrastructure/acl/legacyseances/StatutDesSeancesLegacy.java`             | `SELECT statut FROM seances WHERE id = ? AND center_id = ?`    | port publié Stock → `StatutDesSeances`         |
| 5  | `contexts/stock/infrastructure/acl/legacyseances/PerimetreDesSeancesLegacy.java`          | `SELECT salle_id, creneau_id FROM seances …`                   | port publié Stock → `PerimetreDesSeances`      |
| 6  | `contexts/absences-patients/infrastructure/acl/legacyseance/SeancesRealiseesJdbc.java`    | `SELECT DISTINCT patient_id, date_seance … FROM seances`       | port publié Absences → `SeancesRealisees`      |
| 7  | `contexts/absences-patients/infrastructure/acl/legacyseance/ExclusionsDeSeancesJdbc.java` | `COUNT(*) FROM seances WHERE exclusion_absence_id = ?`         | port publié Absences → `ExclusionsDeSeances`   |
| 8  | `contexts/absences-patients/infrastructure/acl/legacyplanning/PatientsAttendusJdbc.java`  | `SELECT patient_id FROM seances …` (écartés)                   | port publié Absences → `SeancesRealisees`      |
| 9  | `contexts/planification/infrastructure/acl/legacyseance/SeancesRealiseesJdbc.java`        | `JOIN seances … patients` (séances réalisées de la semaine)    | port publié Planification → `SeancesRealisees` |
| 10 | `platform/infrastructure/web/rest/SeanceRestController.java`                              | dashboard mensuel, exports CSV/PDF/XLSX, comptage (`COUNT(1)`) | `SeancesDuJour` / read model dédié             |
| 11 | `platform/infrastructure/reporting/CahierDialyseReportService.java`                       | `COUNT(*) FROM seances WHERE center_id = ? AND patient_id = ?` | read model dédié                               |
| 12 | `platform/application/direction/DirectionBreakdownQueryService.java`                      | `JOIN seances … patients` (pilotage)                           | read model dédié                               |
| 13 | `platform/application/direction/DirectionDashboardQueryService.java`                      | `COUNT(*) FROM seances WHERE center_id IN (…)`                 | read model dédié                               |
| 14 | `platform/application/query/PatientStatsQueryService.java`                                | `FROM seances s LEFT JOIN volet_paramedical …`                 | read model dédié                               |

La garde « pas de SQL sur les tables de Séance hors contexte » est vérifiée par les tests d'architecture
`TableDuStockTest`, `TableDuPatientCouvertureTest`, `TableDeLOrganisationTest` (listes blanches `legitime`)
et par `PgPlanAdapterTest` (lecture simple autorisée uniquement).

## Tests existants couvrant la slice

- `backend/platform/src/test/java/.../infrastructure/web/rest/SeanceRestControllerTest` et `SeanceListIntegrationTest`,
  `SeanceDetailsIntegrationTest`, `SeanceStationIntegrationTest`, `SeanceScanIntegrationTest`,
  `SeanceHistoriqueIntegrationTest`, `SeanceDeverrouillageIntegrationTest`, `SeanceSuppressionIntegrationTest`
  (lecture/écriture séance, multi-centre) ;
- `backend/platform/src/test/java/.../characterization/facturation/validation/...` et
  `backend/contexts/facturation/.../contrat/SeancesJdbcContrat*` (contrat JDBC séance) ;
- `backend/contexts/stock/.../acl/legacyseances/*` tests associés et
  `backend/platform/src/test/java/.../characterization/stock/referentiel/ArticlesDeLaSeanceCharacterizationIntegrationTest` ;
- `backend/contexts/absences-patients/.../acl/legacyseance/*` et
  `backend/platform/src/test/java/.../characterization/absencespatients/consultation/ExportsEtPilotageDesAbsencesA4CharacterizationIntegrationTest` ;
- `backend/contexts/planification/.../acl/legacyseance/SeancesRealiseesJdbc` et tests de contrat
  `LecturesDeLaSemaineJdbcContrat*` ;
- `backend/platform/src/test/java/.../architecture/TableDuStockTest`, `TableDuPatientCouvertureTest`,
  `TableDeLOrganisationTest` (garde « pas de SQL hors contexte ») ;
- Tests domaine `backend/contexts/seance/src/test/java/.../domain/SeanceTest` et `backend/bc-seance/...`.

## Scénarios verrouillés (multi-centre)

- Liste/détail séance filtré par `centerId` de session ; un centre A ne voit pas les séances du centre B (pagination
  `{items,total,page,size}`).
- Cycle de vie `CREE -> VALIDEE -> SIGNEE` (RG-SEA-001) ; verrou `FACTUREE` sur `FacturationValidée` (RG-SEA-002) ;
  pas de statut `ABSENT` (RG-SEA-003).
- Consommables déclarés = motif de sortie stock (RG-SEA-007) ; forfait surchargé prioritaire (RG-SEA-008) ;
  déverrouillage admin après 7 j (RG-SEA-046).
- Isolation inter-centres sur toutes les lectures publiées (`center_id` systématiquement propagé dans les ports
  et adaptateurs, clés de cache incluant le `centerId`).
- Aucun SQL direct sur `seances` hors du contexte `seance` (garde « pas de SQL sur les tables de Séance hors
  contexte ») ; les consommateurs aval passent par `seance.v1`.

## Stabilité

Suite exécutée sur H2 (profil `test`). Isolation centre vérifiée par les tests d'intégration séance et par les
tests d'architecture. Aucun mock du code caractérisé.

## Points d'attention

- Le retrait effectif de `bc-seance` suppose que tous les SQL hors contexte aient été remplacés par les ports
  publiés ; la garde « pas de SQL sur les tables de Séance hors contexte » doit rester verte.
- Les règles de gestion associées : RG-SEA-0xx (voir `docs/reference/regles-de-gestion/07-seances.md`).
- Compatibilité H2 (dev) / PostgreSQL (prod) : aucun SQL spécifique à un moteur.
- Tout nouveau code d'erreur métier est cité dans le référentiel `docs/reference/regles-de-gestion/`.
- Le contexte le plus couplé (dernier de la migration) : Facturation, Stock, Absences, Dossier médical, Pilotage,
  Planification dépendent de la Séance ; la bascule doit être atomique par consommateur.
