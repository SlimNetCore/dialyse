---
status: accepted
date: 2026-10-29
deciders: utilisateur
---

# ADR 0064 — Patient & couverture : contexte unique, module `backend/contexts/patient-couverture`, refactoring sur place

## Contexte

Le domaine Patient & couverture (Supporting, OHS) fusionne quatre micro-modules legacy (`bc-patient`, `bc-assure`,
`bc-insurance`, `bc-pec`) et porte l'identite administrative du patient, ses mouvements (admission, transfert, sortie,
deces, greffe, guerison), l'assure principal, les PEC et les attestations de droits (ADR 0004). Il est reference par
Facturation (payeur, priorite PEC), Comptabilite, Seance (creation conditionnee par
PEC valide), Planification (jour de dialyse), Absences patients (reinclusion) et Dossier medical.

## Decision

1. Un seul contexte `patient-couverture` (`backend/contexts/patient-couverture`, module Maven
   `contexts-patient-couverture`), package racine `com.hemodialyse.backend.contexts.patientcouverture`, couches
   `domain`, `application`, `infrastructure`, `interfaces`, `publishedlanguage`, `configuration` (ADR 0011, 0024). Pas
   de
   strangler progressif : refactoring sur place, memes tables et memes URLs, configuration toujours active.
2. Quatre agregats : `Patient` (root), `Assure` (root), `PEC` (root), `Attestation` (entite satellite de Patient).
   Invariants : un seul assure principal par patient (R1), date de sortie requise (R2), cycle PEC
   `CREE -> VALIDEE -> CLOTUREE` (R3), attestation liee a une PEC active (R5).
3. Langage publie `patientcouverture.v1` expose les evenements de domaine (`PatientAdmis`, `PatientTransfere`,
   `PatientSorti`, `GreffeDeclaree`, `AssurePrincipalAffecte`, `PECCreee`, `PECValidee`, `PECCloturee`,
   `AttestationEnregistree`) en types JDK uniquement. Le contexte ne depend d'aucun autre contexte que par son langage
   publie (Organisation pour la lecture des centres cibles) ; il est lu par les autres contextes via OHS.
4. Ports sortants : `PatientRepository`, `AssureRepository`, `PECRepository`, `AttestationRepository`,
   `OrganisationsPublishedLanguage`, `PublicateurDEvenements`, `JournalDesMessagesTraites`, `Horloge`,
   `GenerateurIdentifiants`. `PECRepository` est expose comme port publie pour Seance (creation conditionnee).
5. Caches `ref.patient.detail`, `ref.pec.active`, `ref.liste.patients` avec invalidation `@CacheEvict` synchrone dans
   l'adaptateur HTTP ; cle de cache incluant toujours le `centerId`.
6. Multi-centre strict : toute donnee est rattachée a un `centerId` (ADR 0010, shared kernel `CentreId`).

## Consequences

- `FrontieresDesContextesTest` gagne une regle "le legacy n'utilise le patient & couverture que par son langage publie".
- Les contextes aval (Facturation, Comptabilite, Seance, Planification, Absences patients, Dossier medical, Pilotage)
  consomment `patientcouverture.v1` via ACL et les ports publies (`PECRepository`).
- `Organisation` doit etre disponible comme dependance du contexte (lecture des centres cibles).
- Les agregats et use cases sont a implementer lors des slices dedies ; le squelette ne contient aucun code metier.
