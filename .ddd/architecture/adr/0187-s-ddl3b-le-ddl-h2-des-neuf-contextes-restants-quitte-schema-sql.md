---
status: accepted (livrée le 2026-11-14)
date: 2026-11-14
decision-makers: [architecte]
context-scope: organisation, patient-couverture, pilotage-direction, planification, comptabilite, facturation, seance, dossier-medical, platform (configuration, gardes)
related: ADR 0024, 0137, 0140 D7, 0186 ; `inventaire-platform-restant-v2.md` ligne 17 ; `s-ddl3-realisation.md`
---

# 0187 - S-DDL3b : le DDL H2 des huit contextes restants quitte `schema.sql`

## Décision
1. 63 tables quittent `db/schema.sql` pour `contexts/<ctx>/src/main/resources/db/h2/<ctx>-schema.sql` : organisation 9, patient-couverture 12, pilotage-direction 2, planification +5 (`planning_parametres`, `infirmier`, `infirmier_affectation`, `infirmier_absence`, `infirmier_remplacement`), comptabilité +5, facturation 7, séance 5, dossier médical 18. `schema.sql` ne garde que les 15 tables d'identité, licence, audit, reprise et outbox (ADR 0140 D7).
2. `tva_types` : propriétaire = Comptabilité (règles de TVA : package `comptabilite.…tva`, glossaire) ; la Facturation dépend de la Comptabilité par Maven (`contexts-facturation` -> `contexts-comptabilite`), jamais l'inverse.
3. Ordre de `schema-locations` : (a) `organisation` AVANT `schema.sql` : `app_user_center` référence `centers` par clé étrangère (seule dépendance de FK qui franchit une frontière) ; (b) `organisation` AVANT `planification` : la reprise des salles d'isolement (`UPDATE salle ... FROM planning_parametres`) lit une table de la Planification et écrit une table de l'Organisation, sens de la dépendance Maven `planification -> organisation` respecté. `salle` garde ses deux `ALTER` (isolement, capacité) dans l'Organisation ; les deux `UPDATE` suivent `planning_parametres` dans la Planification. Le reste est sans effet.
4. `ALTER ... seances` de la Facturation (facture_id, forfait_override_*, statut_avant_exclusion, exclusion_absence_id) : no-op prouvé (le `CREATE seances` déclare déjà les 10 colonnes ; H2 en mémoire, jamais de base antérieure) ; supprimés au lieu d'être déplacés. Les `ALTER` de `facture_reglements` et `factures` restent à la Facturation, dans le même ordre (parité des rangs de colonnes).
5. Gardes datées 2027-06-30 citant `schema.sql` SOLDÉES par suppression (jamais reportées) : `TableDesSeancesTest` (liste 5 -> 4), `TableDesAnalysesTest` (4 -> 3), `TableDuDossierMedicalTest` (6 -> 5). Le script du contexte Séance est hors du périmètre de la garde des séances (le contexte propriétaire est exclu du balayage), ceux de Dossier médical idem pour les siennes.
6. `SchemaH2DeLIdentiteEtDeLOrganisationTest` rejoue désormais `organisation-schema.sql` puis `schema.sql` (même signature attendue). Flyway V1-V59, `seed.sql`, tests de contexte à DDL en ligne : inchangés. La référence `schema-h2-reference.txt` n'a pas changé.

## Preuve
`SchemaH2DesScriptsParContexteTest` vert sans régénération de la référence (INFORMATION_SCHEMA identique à l'ancien script unique).
