---
slice: seance-se7e-lectures-pilotage-et-rapports
status: planned
date: 2026-11-08
order: 7.5
context: seance
parent: seance-se7-lectures-publiees
depends_on: [seance-se7b-tableau-de-bord-mensuel]
sources: [.ddd/safety-net/seance-se7-lectures-publiees.md]
---

# SE7e : lectures de Pilotage direction, statistiques patient, cahier de dialyse, migration

| Fichier | Occurrences | Nature |
|---|---|---|
| `platform/.../application/direction/DirectionDashboardQueryService.java` | 3 | comptages et agrégats multi-centres |
| `platform/.../application/direction/DirectionBreakdownQueryService.java` | 1 | répartition (jointure `patients`) |
| `platform/.../application/direction/DirectionAbsencesQueryService.java` | 1 | comptage par centre |
| `platform/.../application/query/PatientStatsQueryService.java` | 1 | statistiques (jointure `volet_paramedical`) |
| `platform/.../infrastructure/reporting/CahierDialyseReportService.java` | 1 | comptage de séances d'un patient |
| `platform/.../migration/MigrationRollbackJdbcAdapter.java`, `HistoricalRecordJdbcAdapter.java`, `ReferentialAdminTables.java` | 1 + table + usage | import historique, annulation, usages de forfait |

Filet existant : Pilotage PD1 à PD5, Absences A4. Risque : moyen (agrégats lus par la Direction, multi-centres), migration d'import = écriture sur `seances` à traiter comme Facturation (rollback refuse si `facture_id`). Un lot par fichier de Direction, puis rapports, puis migration.
