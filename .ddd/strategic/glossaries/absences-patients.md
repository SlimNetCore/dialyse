---
status: validated (cinq termes proposed le 2026-10-22)
date: 2026-10-21
context: Absences patients (core, à confirmer)
sources: [.ddd/discovery/glossary.md, .ddd/discovery/event-storming.md §5, réponse Q5, .ddd/tactical/absences-patients.md (termes ajoutés le 2026-10-21, validés par l'utilisateur le 2026-10-21)]
---
# Glossaire — Absences patients

| Terme métier | Définition | Identifiant cible | Nom legacy | Synonymes à bannir | Règle / exemple | Statut |
|---|---|---|---|---|---|---|
| Absence de patient | Jour de dialyse prévu sans séance réalisée | `AbsencePatient` | `absences_patient` | absence (seul), `ABSENT` | refus si séance réalisée (RG-ABS-012) ou période facturée (RG-ABS-024) | validated |
| Détection d'absence | Repérage automatique des jours prévus sans séance réalisée (7 j glissants) | politique `DetectionAbsences` | `AbsencePatientScheduler` | — | RG-ABS-040 ; statut « à qualifier » | validated |
| Qualification | Attribution d'un motif justifiant ou non | `AbsencePatient.qualifier(motif)` | `qualifier` | — | `A_QUALIFIER → JUSTIFIEE / NON_JUSTIFIEE` | validated |
| Motif d'absence | Raison de l'absence, justifiante ou non | `MotifAbsence` | `MotifAbsence.justifiante()` | — | — | validated |
| Rattrapage | Séance supplémentaire compensant une absence | `AbsencePatient.rattraper()` | `RATTRAPEE` | régularisation | — | validated |
| Valorisation | Montant perdu figé à la création : forfait PEC HT × (1 + TVA) | `Valorisation` | `ValorisationAbsenceService` | — | RG-ABS-030 ; TTC → HT pour le pilotage | validated |
| Clôture mensuelle des absences | Arrêt du mois : plus aucune absence créée ni requalifiée | `MoisAbsences.cloturer()` / `MoisDAbsencesCloture` | — (à créer) | clôture (seul) | ADR 0009 | validated |
| Source de l'absence | Origine de l'absence : détectée par le contrôle de nuit ou déclarée par un utilisateur | `SourceAbsence` (`AUTOMATIQUE` = détectée, `DECLAREE`) | `SourceAbsence` | — | RG-ABS-002 ; valeurs stockées et renvoyées inchangées | validated |
| Annulation d'absence | Retrait définitif d'une absence (patient finalement présent, déclaration erronée), avec un commentaire | `AbsencePatient.annuler(commentaire)` | `annuler`, `ANNULEE` | suppression | RG-ABS-022 ; l'absence annulée reste en base et bloque une nouvelle absence le même jour | validated |
| Réconciliation | Annulation automatique d'une absence « à qualifier » quand la séance du jour a finalement été réalisée | politique `ReconciliationAbsences` | `reconcilier` | — | RG-ABS-040 (1) ; commentaire « Séance réalisée à cette date » | validated |
| Absence en retard | Absence encore « à qualifier » plus de 3 jours après la séance manquée | `AbsencePatient.enRetard(aujourdhui)` | `qualificationEnRetard` | — | RG-ABS-043 (délai de qualification) | validated |
| Absence comptabilisée | Absence qui compte comme perte (à qualifier, justifiée ou non justifiée) | `StatutAbsence.comptabilisee()` | `comptabilisee` | — | RG-ABS-006 ; ni rattrapée ni annulée | validated |
| Patient attendu | Patient qui devait venir en dialyse ce jour-là (jour coché, admis, pas sorti, centre ouvert) | port `PatientsAttendus` | `patientsAttendusSansSeance` | — | RG-ABS-041 ; lu chez Planification / Patient & couverture / Organisation | validated |
| Séance réalisée | Séance du patient ce jour-là au statut validée, signée ou facturée (fait lu chez Séance) | port `SeancesRealisees` | `seanceRealisee` | présence | RG-ABS-012 ; une séance `ABSENT` (exclue de facturation) n'est pas réalisée | validated |
| Période facturée | Jour couvert par une facture du patient (fait lu chez Facturation) | port publié `FacturationDuPatient.periodeFacturee` | `periodeFacturee` | — | RG-ABS-024 | validated |
| Date ouverte | Date de séance ni facturée pour le patient ni dans un mois d'absences clos : seule une date ouverte accepte une écriture | VO `DateOuverte` | — (à créer) | — | condition de toutes les commandes d'écriture | validated |
| Détection d'une période passée | Relance, à la demande de l'administrateur, de la réconciliation et de la détection jour par jour entre deux dates | use case `DetecterUnePeriodePassee` | `rattraperPeriode`, route `/rattrapage-detection` | « rattrapage » (réservé à la séance de rattrapage) | RG-ABS-046 ; l'URL legacy est conservée | validated |
| Mois d'absences | Mois d'un centre, ouvert ou clos, pour les absences | `MoisAbsences` | — (à créer) | période (seul) | identité centre + mois | validated |
| Réouverture du mois d'absences | Retour d'un mois clos à l'état ouvert, avec un motif | `MoisAbsences.rouvrir(motif)` / `MoisDAbsencesRouvert` | — (à créer) | — | sur le modèle de la Facturation (ADR 0037) | validated |
| Absence suivie | Absence telle que le suivi la lit : modèle de lecture (identité, patient, date, source, statut, motif, valeur figée, traces) ; précurseur de l'agrégat `AbsencePatient` (A2) | `AbsenceSuivie` | ligne de `absence_patient` | — | slice A1 ; lecture seule | proposed |
| Suivi de la semaine | Absences non annulées et séances réalisées de la semaine du planning (dimanche au samedi), pour colorer le planning | `SuivreLaSemaine`, `SemaineDuPlanning` | `semaine`, `SuiviSemaine` | — | RG-ABS-051 | proposed |
| Délai de qualification | Trois jours après la séance manquée : au-delà, une absence « à qualifier » est en retard | `DelaiDeQualification` | `DELAI_QUALIFICATION_JOURS` | — | RG-ABS-043 | proposed |
| Séance de rattrapage proposée | Séance réalisée du patient, après la séance manquée, au plus tard aujourd'hui, qui ne rattrape pas déjà une autre absence ; proposée seulement pour une absence encore rattrapable | `SeancesDeRattrapage`, `ListerLesSeancesDeRattrapage` | `seancesDeRattrapage` | — | RG-ABS-021 ; 422 `ABSENCE_NON_MODIFIABLE` si rattrapée ou annulée | proposed |
| Centre masqué (Direction) | Centre de 1 à 4 absences comptabilisées, dont les chiffres d'absence sont masqués et exclus du total de la Direction | `AbsencesStats` (champs `null`), `centresMasques` | — | — | RG-DIR-012, KB-ABS-07 | proposed |

## Questions ouvertes (termes proposés le 2026-10-22, slice A1)
1. Valider les cinq termes ci-dessus (absence suivie, suivi de la semaine, délai de qualification, séance de rattrapage proposée, centre masqué).
2. « Suivi de la semaine » : garder ce nom ou « absences de la semaine » ?
3. `AbsenceSuivie` disparaît-elle au profit de `AbsencePatient` en A2, ou reste-t-elle comme modèle de lecture distinct ?
