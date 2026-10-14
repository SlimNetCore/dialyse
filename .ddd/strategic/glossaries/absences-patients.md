---
status: validated
date: 2026-10-09
context: Absences patients (core, à confirmer)
sources: [.ddd/discovery/glossary.md, .ddd/discovery/event-storming.md §5, réponse Q5]
---
# Glossaire — Absences patients

| Terme métier | Définition | Identifiant cible | Nom legacy | Synonymes à bannir | Règle / exemple | Statut |
|---|---|---|---|---|---|---|
| Absence de patient | Jour de dialyse prévu sans séance réalisée | `AbsencePatient` | `absences_patient` | absence (seul), `ABSENT` | refus si séance réalisée (RG-ABS-012) ou période facturée (RG-ABS-024) | proposed |
| Détection d'absence | Repérage automatique des jours prévus sans séance réalisée (7 j glissants) | politique `DetectionAbsences` | `AbsencePatientScheduler` | — | RG-ABS-040 ; statut « à qualifier » | proposed |
| Qualification | Attribution d'un motif justifiant ou non | `AbsencePatient.qualifier(motif)` | `qualifier` | — | `A_QUALIFIER → JUSTIFIEE / NON_JUSTIFIEE` | proposed |
| Motif d'absence | Raison de l'absence, justifiante ou non | `MotifAbsence` | `MotifAbsence.justifiante()` | — | — | proposed |
| Rattrapage | Séance supplémentaire compensant une absence | `AbsencePatient.rattraper()` | `RATTRAPEE` | régularisation | — | proposed |
| Valorisation | Montant perdu figé à la création : forfait PEC HT × (1 + TVA) | `Valorisation` | `ValorisationAbsenceService` | — | RG-ABS-030 ; TTC → HT pour le pilotage | proposed |
| Clôture mensuelle des absences | Arrêt du mois : plus aucune absence créée ni requalifiée | `MoisAbsences.cloturer()` / `MoisDAbsencesCloture` | — (à créer) | clôture (seul) | ADR 0009 | proposed |
