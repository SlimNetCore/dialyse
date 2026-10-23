---
status: approved
date: 2026-10-22
---

# Seams accordés (legacy gelé, ADR 0024)

| Date | Slice | Fichiers | Seam | Accord | Test |
|---|---|---|---|---|---|
| 2026-10-22 | absences-patients-a2-cycle-de-vie | `bc-seance/.../domain/absence/port/AbsencePatientRepositoryPort.java` (méthode `saveIfAQualifier`), `platform/.../persistence/adapter/absence/AbsencePatientJdbcAdapter.java` (`UPDATE … AND statut = 'A_QUALIFIER'`), `platform/.../application/absence/AbsencePatientService.java` (`reconcilier` compte une annulation seulement si la ligne a été écrite) | La réconciliation de nuit legacy (conservée pour A3) n'écrase plus une absence qualifiée ou rattrapée par le contexte `absences-patients` entre sa lecture et son écriture (revue `.ddd/verify/review-absences-a2.md`, constat 1). Comportement inchangé tant que l'absence est « à qualifier » ; s'efface avec A3. | utilisateur (message du coordinateur, 2026-10-22) | `AbsencePatientIntegrationTest.the_night_reconciliation_never_overwrites_an_absence_handled_in_the_meantime` |

## Seams retirés

| Date | Slice | Seam | Raison |
|---|---|---|---|
| 2026-10-22 | absences-patients-a3-detection-de-nuit | `saveIfAQualifier` (A2) : méthode, `UPDATE … AND statut = 'A_QUALIFIER'` et son test de course | La réconciliation legacy est retirée avec `AbsencePatientService`. Le nouveau code relit l'absence sous verrou (`FOR UPDATE`) dans sa propre transaction et ne l'annule que si elle est encore `reconciliable` (détectée, à qualifier, hors période facturée) : la course est couverte par `ReconciliationAbsences` (test `la_reconciliation_laisse_les_absences_declarees_qualifiees_ou_facturees`) et `AbsencesContrat`. `seamAllowed` est vide. |

## Exceptions ponctuelles de `covered.json` (A3)

| Date | Slice | Fichier | Raison |
|---|---|---|---|
| 2026-10-22 | absences-patients-a3-detection-de-nuit | `platform/.../application/notification/NotificationService.java` | Surcharge `notifyAbsencesAQualifier(..., UUID idImpose)` (délègue au `send` à identifiant imposé déjà existant) : alerte unique par centre et par jour, sûre en multi-instance. Le reste du fichier n'est pas caractérisé. `DomainServiceConfig` (bean retiré) a été retiré de `covered.json` après l'édition. |
