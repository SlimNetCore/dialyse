---
status: accepted (CLOT1, action M1) ; décision déléguée, à confirmer métiers/DPO <= 2026-12-15
date: 2026-11-15
decision-makers: [utilisateur (directive CLOT1), architecte]
context-scope: platform (infrastructure/migration/*, acl/reprise, acl/gmao)
related: ADR 0140 (D7), 0173, 0192 ; revue globale 2026-11-15 M1
---

# 0204 - Les migrators de reprise : règles de normalisation propres à l'import, noyau de l'outil de reprise

## Contexte
`infrastructure/migration/service/*` (1 887 lignes : `HistoryRules` 325, `PatientMigrator` 362, `MigrationDomainService` 250, `LegacyValueParser` 217, `RecordRules`, `AssureMigrator`, `AffectationMigrator`, `OpeningBalanceMigrator`, `PatientRecordMigrator`, `EntityMigrator`, `Email`, `PhoneNumber`, `RowValues`) portent des règles qui ressemblent à du Patient et couverture. La revue (M1) demandait de trancher : noyau assumé ou extraction.

## Vérification (Grep, 2026-11-15)
Aucune de ces classes ne contient `Jdbc*`, `SELECT/INSERT/UPDATE/DELETE`, ni n'importe un repository ou une entité de contexte. Leurs seuls imports d'autres contextes sont des **ports publiés** (`patientcouverture.publishedlanguage.RepriseDesAssures`, `RepriseDesPatients`, `RepriseDesAffectations`) et le noyau partagé (`CentreId`, `PagedResult`). Elles ne touchent donc aucune table de contexte (ADR 0173).

## Décision
Ces classes sont des **règles de normalisation propres à l'import du legacy** (analyse de valeurs historiques, dédoublonnage, appariement, report de soldes d'ouverture), pas des règles du métier courant. Elles font partie intégrante de l'**outil de reprise**, noyau délibéré (ADR 0140 D7), et restent isolées derrière les ports publiés des contextes : toute écriture passe par ces ports, jamais par une table. Pas d'extraction vers `contexts/reprise` : un contexte de plus à maintenir pour un outil à durée de vie bornée ne se justifie pas, et les règles n'ont aucun consommateur hors de l'import.

**Condition de retrait : l'événement « fin de la reprise de production »** ; à cet événement, l'outil entier est retiré (service, model, port, persistance `migration_*`, importer, `acl/reprise`, `acl/gmao`) ainsi que les ACL qui n'existent que pour lui. Aucune date n'est posée.

**Statut de la décision : déléguée, à confirmer par les métiers et le DPO au plus tard le 2026-12-15** (séance unique de revue des KB).

## Conséquences
+ pas de refonte ; la règle de l'ADR 0173 (aucune table d'un autre contexte) reste la garde.
- 1 887 lignes restent en plateforme jusqu'à l'événement ; un nouveau besoin métier courant ne doit jamais y être ajouté (il va dans le contexte propriétaire).
