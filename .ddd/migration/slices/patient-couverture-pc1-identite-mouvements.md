---
slice: patient-couverture-pc1-identite-mouvements
status: planned
date: 2026-10-29
order: 1
context: patient-couverture
sources: [.ddd/tactical/patient-couverture.md, ADR 0004, 0024, 0064]
---

# PC1 — Identité administrative et mouvements du patient

> Statut vrai au 2026-11-02 : `planned` (module gelé, `backend/contexts/patient-couverture/GEL.md`, ADR 0073). Aucun filet PC1 propre (identité, mouvements non caractérisés). Reprise sur place : filet legacy d'abord, mêmes tables et URL, états du LEGACY (`PERMANENT`, `TRANSFERE`, `GUERRI`, `DECEDE`, `GREFFE`…), `date_evenement_etat`.
> Règle R2 « un état de sortie exige une date » : **déjà couverte par le legacy** (`FinOccupation.verifierDate`, `PATIENT_DATE_EVENEMENT_REQUISE`, RG-PAT-031, golden Planification P7 « sortie sans date refusée ») ; à reproduire telle quelle, non à ajouter.
> Lectures SQL hors contexte (relevé du 2026-11-02, à transformer en liste blanche datée au filet PC1) : **25 fichiers** de `src/main` hors `contexts/patient-couverture` lisent ou écrivent `patients`, `mouvement_patient`, `assure`, `assure_patient`, `prise_en_charge`, `attestation_droit` (grep `(FROM|JOIN|INTO|UPDATE) <table>` ; dont `PecValidationJdbc` du module seance, `SituationsDesFacturesJdbc`, `DirectionDashboardQueryService`, `PatientSummaryQueryService`, les ACL de Planification, `SeanceRestController`).

Agrégat `Patient` (root) : admission, transfert, sortie, décès, greffe, guérison. Invariant R2 (date de sortie requise),
R1 (un seul assuré principal).

## Cas d'usage

`AdmettrePatient`, `TransfererPatient`, `SortirPatient`, `DeclarerDeces`, `DeclarerGreffe`, `DeclarerGuerison`,
`ConsulterPatient`, `ListerPatients`.

## Entrées legacy

`bc-patient` (Patient, mouvements), `PatientApplicationService`, `MouvementPatientJdbcAdapter`.

## Prérequis

Bloquer la sortie sans date d'événement (décision utilisateur) ; lister cas existants (verifications-production §25.2).

## Données

Tables patient/mouvements ; aucune migration. centerId obligatoire.