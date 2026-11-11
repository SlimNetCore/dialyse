---
status: accepted
date: 2026-11-11
decision-makers: [architecte]
context-scope: platform
related: ADR 0024, 0133 (S6c), 0134:143 ; `.ddd/migration/reviews/inventaire-platform-restant.md` (C1, C2, C6) ; rapport `.ddd/migration/reviews/pl1-retrait-entites-mortes.md`
---

# 0135 - Retrait des entités JPA mortes de la plateforme (PL1)

Refactoring sur place (ADR 0024) : mêmes tables, mêmes URLs, mêmes colonnes. Aucune règle métier touchée.

## Décision
- 18 entités et 10 dépôts JPA sans consommateur sont supprimés (payeurs, stock, bons, `app_settings`, `user_center_assignment`), ainsi que `AssurePatientId`,
  `PointBiologiqueResponse`, `EntityWriteResponse`.
- Avec `defer-datasource-initialization: true`, Hibernate (`ddl-auto: update`) crée les tables H2 AVANT `db/schema.sql` : les `CREATE TABLE IF NOT EXISTS`
  déjà présents pour les payeurs étaient des no-op et divergeaient des tables réelles (50 / 500 / NOT NULL contre 255 / nullable). `db/schema.sql` porte
  désormais un `CREATE TABLE` + `CREATE INDEX IF NOT EXISTS` explicite par table, qui reproduit le schéma Hibernate.
- Preuve : un test jetable (retiré) a vidé `INFORMATION_SCHEMA` (colonnes, types, longueurs, précisions, nullabilité, défauts, clés, uniques, index,
  avant / après chaque sous-étape) : schéma `PUBLIC` identique, hors ordre des colonnes ajoutées par ALTER.
- Production inchangée (Flyway, `ddl-auto: validate`, `schema.sql` non joué).

## Conséquences
- `TableDesPayeursTest` n'exclut plus aucune entité (`ENTITES_JPA_CONNUES` vide, garde de non-existence des trois entités).
- 5 entrées retirées de `.ddd/safety-net/covered.json` (code mort couvert) ; aucune ajoutée.
- Toute entité retirée à l'avenir impose d'abord le `CREATE TABLE` de sa table dans `schema.sql`, vérifié par comparaison d'`INFORMATION_SCHEMA`.
