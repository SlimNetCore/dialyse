---
slice: comptabilite-c7-retrait-bc-comptabilite
status: built
date: 2026-10-19
sources: [ .ddd/migration/roadmap.md §1 bis, ADR 0024, ADR 0025, ADR 0027, slices comptabilite-c1 à c6 ]
---

# C7 — Retrait de `bc-comptabilite`

## Objectif

Décommissionner le module legacy `backend/bc-comptabilite` une fois toutes les capacités de la Comptabilité servies par
le contexte `backend/contexts/comptabilite` (C1 à C6). Aucun changement de comportement, aucune migration de schéma.

## Réalisation (2026-10-19)

1. **Absences** : `ForfaitAbsenceJdbcAdapter` lit la TVA du type « HEMODIALYSE » par le langage publié
   `ParametrageFiscal` du contexte (exonérée = 0, KB-CPT-19 reproduit) au lieu de l'ancien `ParametrageFiscalPort`.
2. **Plateforme** — retirés : `ComptabiliteApplicationService` (ACL transitoire de C5), les cinq ACL C1/C2
   (`PlanComptableDuContexte`, `JournauxDuContexte`, `ComptesClientsDesPayeursDuContexte`, `MappingComptableDuContexte`,
   `ParametrageFiscalDuContexte`), `EcritureComptableJpaAdapter`, `PeriodeComptableJpaAdapter`, l'ancien
   `Sage100ExportAdapter`, les dépôts Spring Data et les entités JPA legacy des écritures et périodes.
3. **Schéma de test** : les tables `ecritures_comptables`, `lignes_ecriture` et `periodes_comptables` étaient créées en
   H2
   par les entités legacy (`ddl-auto: update`, absentes de `schema.sql`). Elles sont décrites à l'identique (colonnes,
   index, contrainte unique, clé étrangère) par `EcritureComptableJpa`, `LigneEcritureJpa`, `PeriodeComptableJpa` du
   contexte, utilisées pour le schéma seulement (lecture et écriture par JDBC). Production : Flyway inchangé.
4. **Tests** : les suites de caractérisation C1 à C6 génèrent leurs écritures par l'outil de test
   `GenerationDEcritures` (contexte, même forme de commande, refus rendus en `BusinessException`) ; goldens inchangés.
   `ComptabiliteRestControllerIntegrationTest` insère par le dépôt du contexte ; `TraducteursMappingTvaTest` (ACL
   retirées) est supprimé.
5. **Module** : `bc-comptabilite` retiré de `backend/pom.xml` (module et gestion de dépendance) et de
   `platform/pom.xml` ; dossier supprimé (21 classes).

## Ce qui reste hors périmètre

Écrans Angular de régénération (C5) et de détail des échecs du stock (C6) ; décisions encore ouvertes de
`known-bugs.md` (KB-CPT-01 à 45 marqués « reproduire »).

