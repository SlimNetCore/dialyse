---
status: accepted
date: 2026-11-10
review-by: 2026-12-15
decision-makers: [utilisateur (« à confirmer <= 2026-12-15 »), architecte]
context-scope: platform, bc-patient, bc-assure, patient-couverture
related: ADR 0024, 0073, 0077, 0078, 0079 ; inventaire `.ddd/migration/reviews/inventaire-bc-patient-bc-assure.md`
---

# 0133 - Retrait de bc-patient / bc-assure : plan S1-S6

Refactoring sur place (ADR 0024) : mêmes tables, mêmes URLs, pas de Strangler. Aucun golden modifié.

## Contexte
Les modules `backend/bc-patient` et `backend/bc-assure` sont des reliquats du modèle patient historique. Aucun utilisateur dans `backend/contexts` ; seul `backend/platform` les consomme (migration historique, adapters/mapper JPA, 4 lecteurs de `Patient`). L'ADR 0079 les disait utilisés par Séance, FHIR, KDIGO : périmé.

## Décision
Retirer les deux modules en 6 slices, chacune un comportement, livrée et vérifiée seule.

| # | Slice | Risque | Statut |
|---|---|---|---|
| S1 | Supprimer le calcul des mouvements mort de bc-patient (5 classes, 2 tests) | faible | **faite** (2026-11-10) |
| S2 | Notification de saisie infirmière : nom/prénom via `FichesDesPatients` | faible | **faite** (2026-11-10) |
| S3 | Cahier de dialyse : identité/assurance via le langage publié | moyen | **faite** (2026-11-10) |
| S4 | PDF greffe (`GreffePdfService`) : idem | moyen | **faite** (2026-11-10) |
| S5 | ACL Séance `FichesFournieLegacy` : fiches depuis `FichesDesPatients` | moyen | à faire |
| S6 | Outil de migration historique réécrit dans `patient-couverture`, puis retrait des adapters/entités/mapper JPA et des deux modules | élevé | à faire, à confirmer |

S2-S5 sont indépendantes. Critère de retrait final : inventaire §7 (0 import `domain.(patient|assure).`, plus d'entités JPA patient/assuré, poms nettoyés, build complet vert).

## Décision utilisateur
La migration historique reste utilisée en production : S6 est une **réécriture** dans `patient-couverture` (cas d'usage `AdmettreUnPatient` / `GererLesAssures` ou SQL du contexte), pas une suppression. **À confirmer <= 2026-12-15** (verrou du centre ou SQL direct hors activité, ADR 0077 pt 12).

## S1 : preuve
- 0 référence Java hors bc-patient aux 5 classes (seules mentions : commentaires dans `PredicatDEffectif`, tests de caractérisation, script pg16, golden `type_inconnu.legacy.json` : texte, pas de dépendance).
- Équivalents dans `contexts/patient-couverture` : `PatientTest` (date requise l.117-123, avant admission l.125-130, `dernierJourOccupe` l.364-371, type de mouvement par état l.168-173, reprise l.186, séjour l.229).
- `Patient.libererPlacement` (bc-patient) n'a plus d'appelant ; sa libération vit dans `contexts/planification` (P7). À retirer avec F2/S6.

## S2 : preuve
- `SaisieInfirmierNotifier` n'utilisait que `PatientRepositoryPort.findById(patient, centre)` pour `getNom()` / `getPrenom()` ; il lit maintenant `FichesDesPatients.noms(centre, [patient])` (même table, même borne de centre, patient absent ou d'un autre centre = pas de nom, valeurs nulles conservées). Aucun ajout au langage publié, bean `fichesDesPatients` déjà câblé, aucune dépendance Maven nouvelle.
- Test `NotificationServiceSaisieInfirmierTest` adapté d'abord (rouge de compilation vu), puis vert.
- Décision utilisateur : `SaisieInfirmierNotifier.java` inscrit dans `.ddd/safety-net/covered.json` (filet = ce test) pour lever le hook ; `seamAllowed` reste vide.

## S3 : preuve
- `CahierDialyseReportService` n'utilisait que `PatientRepositoryPort.findById(patient, centre)` pour `getPrenom/getNom/getCodePatient/getSexe/getDateNaissance/getGroupeSanguin/getNumeroAssurance/getTelMobile` (ligne d'identité du PDF). Aucun équivalent publié ne portait code, groupe sanguin et mobile : ajout ADDITIF `FichesDesPatients.carte(centre, patient)` -> `CarteDuPatient` (`FichesPublieesJdbc` : une requête sur `patients` bornée au centre ; double en mémoire ; test de contrat commun double + H2). Patient absent ou d'un autre centre : vide, donc `PATIENT_INTROUVABLE` comme avant.
- Paramètres Jasper et jrxml inchangés (`PATIENT_ID`, `PATIENT_LIGNE`, dates, `EDITE_*`) : le PDF est identique. Seul écart de bord : un numéro d'assurance vide en base levait `IllegalArgumentException` au chargement, il est maintenant omis de la ligne.
- Tests : `CahierDialyseReportServiceTest` réécrit d'abord (rouge de compilation vu), puis vert ; le double anonyme de `IsolementDesPatientsParLaPlanificationTest` reçoit la nouvelle méthode.

## S4 : preuve
- `GreffePdfService` n'utilisait que `PatientRepositoryPort.findById(patient, centre)` pour `getNom/getPrenom/getCodePatient/getGroupeSanguin` (ligne « Patient : ... » du PDF). Tous portés par `FichesDesPatients.carte(centre, patient)` (S3) : aucun ajout au langage publié, aucune dépendance Maven nouvelle. Patient absent ou d'un autre centre : vide, ligne omise comme avant (le 404 reste porté par `DossierDeGreffePublie.duPatient`).
- Aucun test n'existait : `GreffePdfServiceTest` ajouté d'abord (rouge de compilation vu : constructeur sur `PatientRepositoryPort`), puis vert ; il capture le HTML décoré (ligne patient, groupe sanguin absent = `-`, carte absente = pas de ligne). HTML et PDF identiques.

## Conséquences
- 251 LOC main et 213 LOC test en moins, aucun changement de comportement.
- Les références documentaires à `FinOccupation` / `MouvementsPatient` restent comme historique.
