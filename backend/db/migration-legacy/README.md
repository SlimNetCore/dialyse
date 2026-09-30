# Migrations legacy (archivées — NON exécutées)

Ces scripts V1…V38 n'ont jamais été appliqués par Flyway : ils sont écrits en dialecte H2 (`MERGE … KEY`) et embarquent
des données de démo. Le schéma de production a été construit
par Hibernate (`ddl-auto: update`) + `db/schema.sql`.

Depuis l'intégration de Flyway, la référence est
`backend/platform/src/main/resources/db/migration/V1__baseline_schema.sql`
(dump du schéma de production). Ce dossier est conservé uniquement pour l'historique.
Voir `docs/FLYWAY.md`.

