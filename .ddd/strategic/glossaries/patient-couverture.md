---
status: validated
date: 2026-10-09
context: Patient & couverture (supporting)
sources: [.ddd/discovery/glossary.md, .ddd/discovery/event-storming.md §2]
---
# Glossaire — Patient & couverture

| Terme métier | Définition | Identifiant cible | Nom legacy | Synonymes à bannir | Règle / exemple | Statut |
|---|---|---|---|---|---|---|
| Patient | Personne suivie par un centre, vue sous l'angle administratif | `Patient` | `patients` | client, user | — | proposed |
| Admission | Entrée du patient dans un centre | `Patient.admettre()` / `PatientAdmis` | `createPatient` | création | mouvement `ADMISSION` | proposed |
| État du patient | Situation administrative (permanent, occasionnel, vacancier, transféré, décédé, greffé, guéri) | `EtatPatient` | `etat`, `PatientType` | statut, type | unification avec `PatientType` à décider (H5) | proposed |
| Mouvement de patient | Événement de vie administrative | `MouvementPatient` | `TypeMouvementPatient` | — | « place libérée » sort vers Planification (ADR 0005) | proposed |
| Sortie | Fin de prise en charge dans le centre (transfert, décès, greffe, guérison) | `Patient.declarerSortie()` / `PatientSorti` | `updatePatient` | — | date d'évènement exigée (RG-PAT-031) | proposed |
| Assuré | Titulaire des droits d'assurance rattaché au patient | `Assure` | `bc-assure` | — | un seul assuré principal à la fois | proposed |
| Prise en charge (PEC) | Accord d'un payeur couvrant une période et un forfait | `PriseEnCharge` | `PriseEnCharge`, `PecStatus` | — | `CREE → VALIDEE → CLOTUREE` ; période demandée vs effective | proposed |
| Forfait de PEC | Prix HT de la séance prévu par la PEC | `ForfaitPec` | `forfaitId` | tarif | — | proposed |
| Attestation de droits | Justificatif de droits du patient | `AttestationDeDroits` | `bc-insurance`, `ATTESTATION_DROITS` | — | absente ⇒ cause de non-facturation | proposed |

Homonymes : *Greffe* (état de sortie, ici) ≠ *Greffe réalisée* (statut de bilan, Dossier médical).
