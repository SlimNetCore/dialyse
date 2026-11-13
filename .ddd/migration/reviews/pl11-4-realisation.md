---
status: proposed (code, goldens, ADR 0160, docs, butoirs faits ; PL11-5 partiel)
date: 2026-11-13
sources: pl11-analyse.md, pl11-decisions.md, ADR 0160
---
# PL11-4 : salles chez l'Organisation : ÉTAT FINAL
- Code et détail : ADR `.ddd/architecture/adr/0160-pl11-4-les-salles-chez-organisation.md`.
- Goldens pl11-0-salles : 5 écarts relus un par un (D3/D4/D8/D10) -> `*_nouveau_code.approved.json`, archives `_ecarts-legacy/pl11-0-salles/*_avant_pl11-4.legacy.json` ; 5 autres identiques ; aucun .received.
- Usage GMAO : langage publié `EquipementsDeLaSalle` (GMAO) lu par l'ACL `UsageDeLaSalleParLaGmao` (le test de butoir interdisait le SQL direct sur gmao_equipements).
- PL11-5 : `AppariementDeReferentiels` + `AppariementParLesContextes` ; `PatientMigrator`, `HistoryRules`, `MigrationConfig` et les doubles de test l'utilisent. RESTE : `findAllForMatching` subsiste sur `ReferentialAdminRepositoryPort` (adaptateur SQL sans table, `ReferentialAdminDomainService.Matching`, `InMemoryReferentialAdminRepository`) ; à retirer avec l'adaptateur en PL11-6.
- Butoirs : TableDesEquipementsDeLaGmaoTest, TableDuPatientCouvertureTest, TableDesSeancesTest (4->2) soldés ; pl11-analyse.md §6 mis à jour ; aucun report.
- Docs : ADR 0160, KB-PL11-1/3/8 CORRIGÉ, KB-PL11-7 reproduit (D9 question ouverte), RG-REF-005, annexe, glossaire Organisation.
- covered.json : nouveaux fichiers platform inscrits : SallesParOrganisation, AppariementDeReferentiels, AppariementParLesContextes.
- Passes ciblées 1, 2, 3 (random) vertes.
