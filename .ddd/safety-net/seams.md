---
status: approved
date: 2026-10-22
---

# Seams accordés (legacy gelé, ADR 0024)

| Date | Slice | Fichiers | Seam | Accord | Test |
|---|---|---|---|---|---|
| 2026-10-22 | absences-patients-a2-cycle-de-vie | `bc-seance/.../domain/absence/port/AbsencePatientRepositoryPort.java` (méthode `saveIfAQualifier`), `platform/.../persistence/adapter/absence/AbsencePatientJdbcAdapter.java` (`UPDATE … AND statut = 'A_QUALIFIER'`), `platform/.../application/absence/AbsencePatientService.java` (`reconcilier` compte une annulation seulement si la ligne a été écrite) | La réconciliation de nuit legacy (conservée pour A3) n'écrase plus une absence qualifiée ou rattrapée par le contexte `absences-patients` entre sa lecture et son écriture (revue `.ddd/verify/review-absences-a2.md`, constat 1). Comportement inchangé tant que l'absence est « à qualifier » ; s'efface avec A3. | utilisateur (message du coordinateur, 2026-10-22) | `AbsencePatientIntegrationTest.the_night_reconciliation_never_overwrites_an_absence_handled_in_the_meantime` |
