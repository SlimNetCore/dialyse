---
status: accepted
date: 2026-11-02
decision-makers: [utilisateur (décision du 2026-11-02, décision déléguée à l'assistant pour l'acceptation formelle), architecte]
context-scope: patient-couverture
related: ADR 0024 (refactoring sur place), 0064, 0004, 0072 (même démarche pour le dossier médical) ; `.ddd/verify/verify-patient-couverture.md` ; `backend/contexts/patient-couverture/GEL.md`
---

# 0073 - Patient & couverture : gel du module parallèle et reprise sur place

## Contexte
La vérification indépendante (`verify-patient-couverture.md`, verdict fail, 4 blockers) a établi que `backend/contexts/patient-couverture` n'a jamais été branché (`platform/pom.xml` ne le référence pas) : 100 % du trafic passe par le legacy (`bc-patient`, `bc-assure`, `bc-insurance`, `bc-pec`, `PatientRestController`). Le module : lit `patients.date_sortie` (colonne inexistante ; la réalité est `date_evenement_etat`) ; redéfinit les états patient (`ACTIF/SORTI/GUERI`) contre le vocabulaire legacy (`PERMANENT/TRANSFERE/GUERRI/DECEDE/GREFFE…`) ; n'écrit aucun mouvement ; n'a pas de garde de centre (IDOR) ; expose des URL nouvelles `/api/v1/patient-couverture/*` contraires à l'ADR 0024 ; n'a aucune parité (aucun golden `_nouveau_code`).

Audit de production (demande A, 2026-11-02) : `2978e639` et les commits suivants n'ont modifié **aucun** code legacy de production patient, assuré ou PEC sans filet (entités JPA `PatientJpaEntity`, `AssureJpaEntity`, `AssurePatientJpaEntity`, `AttestationJpaEntity`, `PecJpaEntity` inchangées ; `schema.sql` n'a reçu que `machine`, `protocole`, `seances`, `demandes_examen` ; `covered.json` n'a reçu aucune entrée patient-couverture ; les changements de `PatientRestController`, `MouvementsPatient` et `PatientApplicationService` appartiennent à Planification P5/P7, caractérisés). **Aucun revert nécessaire.** Preuve : `backend/scripts/patient-couverture-pg16-rejeu.sh` (PostgreSQL 16 jetable : V1..V52, 5 entités conformes à `information_schema`).

## Décision
1. **Geler le module** : retiré du réacteur (`backend/pom.xml`) et du `Dockerfile` ; dossier, ADR 0064, fiches, tactique et glossaire conservés (`GEL.md`).
2. **Vocabulaire des états patient = celui du legacy** (`PERMANENT`, `OCCASIONNEL`, `VACANCIER_LOCAL`, `VACANCIER_ETRANGER`, `TRANSFERE`, `DECEDE`, `GREFFE`, `GUERRI`) ; date de l'événement = `date_evenement_etat`.
3. **« Un état de sortie exige une date »** : déjà imposée par le legacy (`FinOccupation.verifierDate`, erreur `PATIENT_DATE_EVENEMENT_REQUISE`, RG-PAT-031, golden Planification P7 « sortie sans date refusée »). Rien à implémenter en PC1.
4. **Langage publié** : repris au moment de PC5, pas avant. **Liste blanche des lectures SQL hors contexte** : établie au filet PC1.
5. **Désactiver** `TableDuPatientCouvertureTest` (motif dans `@Disabled`) ; exclure le module du balayage de `ReglesDeGestionDocumentationTest`.
6. **Statuts réels** : PC1 `planned`, PC2 `characterized`, PC3 `characterized`, PC4 `planned`, PC5 `planned`.
7. L'ADR 0064 est **partiellement remplacée** : le contexte unique et le refactoring sur place restent valides ; le module `contexts/patient-couverture` tel que codé (URL, tables, états) est abandonné.

## Conséquences
- Plus de risque de split brain ni de démarrage en échec ; le travail gelé reste matière de réflexion.
- Les routes `/api/v1/patient-couverture/*` ne sont plus servies (aucune n'a jamais été exposée en production).
