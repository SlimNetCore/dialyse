---
status: built
date: 2026-10-22
slice: absences-patients-a2-cycle-de-vie
order: 2 (A1 à A6, voir `.ddd/tactical/absences-patients.md` § 10)
sources: [.ddd/tactical/absences-patients.md (§ 8, 10, 11, 12, décisions 2026-10-21), .ddd/safety-net/absences-patients-a2-cycle-de-vie.md, known-bugs.md (KB-ABS-10 à 21), ADR 0024, 0031, 0038, 0039, docs/reference/regles-de-gestion/06-absences-patients.md]
---

> **Refonte sur place (ADR 0024)** : même table `absence_patient`, mêmes URLs, mêmes noms JSON ; une seule migration additive (V43, index
> unique partiel PostgreSQL). Pas de façade, de flag ni de plan de bascule par paliers ; retour arrière = commit précédent (V43 est
> inoffensive à laisser : un index).

# A2 — Cycle de vie et valorisation des absences de patients

**Comportement observable** : déclarer, qualifier, rattraper, annuler une absence (`POST /`, `PUT /{id}/qualification|rattrapage|annulation`) ;
exclusion et réinclusion d'une séance par la Facturation vues côté absence.

## Réalisation

- **domaine** : `AbsencePatient` (agrégat), `Valorisation`, `ForfaitApplicable`, `Commentaire`, `DateOuverte`, `DroitDeCorrection`,
  `FaitsDuRattrapage`, `EvenementDAbsence`, `RegleDAbsenceViolee`, `SaisieInvalide`, `EcritureConcurrente`, ports `AbsencesRepository`, `PeriodesFacturees`.
- **application** : `DeclarerUneAbsence`, `QualifierUneAbsence`, `RattraperUneAbsence`, `AnnulerUneAbsence`, `RetirerLAbsenceDeLExclusion`, ports
  `UniteDeTravail`, `PatientsDuCentre`, `ForfaitsDesPatients`, `TauxDeTva`, `GenerateurIdentifiants`, `Intervenant` ; `SeancesRealisees.realisee`, `SessionCourante.intervenant`.
- **infrastructure** : `persistence/AbsencesJdbc` (verrous `FOR UPDATE`), `transaction/UniteDeTravailSpring`, ACL `legacypatient/PatientsDuCentreJdbc`,
  `legacycouverture/ForfaitsDesPatientsJdbc`, `comptabilite/TauxDeTvaComptabilite`, `facturation/PeriodesFactureesDeLaFacturation`.
- **interfaces** : `AbsencesEcrituresControleur`, `ErreursMetierDesAbsencesAdvice` (global : l'exclusion remonte les mêmes refus), `EntreesDesAbsencesAdvice`
  (remplace `ConsultationDesAbsencesAdvice`), `facturation/ExclusionDeLaFacturation` (fournit `DeclarationDesAbsences`). Langage publié : `SignalementDesSaisies`.
- **platform** : `SignalementDesSaisiesLegacy` (ACL notifications, ajoutée à `covered.json` à la demande du hook) ; migration `V43__absences_patients_unicite_du_rattrapage.sql`.
- **Retiré du legacy** : routes d'écriture d'`AbsencePatientRestController` ; `AbsencePatientService.declarer|qualifier|rattraper|annuler` ; `AbsencePatient.declaree|qualifier|rattraper` ;
  `AbsencePatientRepositoryPort.findById|datesRattrapage` ; `AbsenceDonneesPort.nomPatient` ; ACL `AbsencesDesPatientsLegacy` (+ test, déplacé dans le module). **Conservés pour A3** :
  `detecter`, `reconcilier`, `rattraperPeriode`, `controlerCentre`, `synthese`, `AbsencePatient.detectee|annuler`, `ValorisationAbsenceService`, `ForfaitAbsenceJdbcAdapter`, scheduler, `POST /rattrapage-detection`.
- **Frontend** : bouton « rattraper » réservé au droit de correction (`actionPossible`), code `ABSENCE_ECRITURE_CONCURRENTE` (fr/en/ar) ; 14 tests `features/absences` verts.

## Décisions de l'utilisateur (22/10) et défauts retenus

| Sujet | Réalisation |
|---|---|
| KB-16 forfait | reproduit à l'identique (`ForfaitsDesPatientsJdbc`, 6 tests) ; argent figé, aucune écriture de valeur après création |
| Annulation d'une rattrapée | refusée 422 `ABSENCE_NON_MODIFIABLE`, date conservée ; unicité du rattrapage visant `RATTRAPEE` (V43, contrôle § 24.1) |
| Entrées illisibles, 415, commentaire > 1000 | 400 `BAD_REQUEST` / 415 ; ordre existence, droit, état, période facturée, valeurs |
| Exclusion Facturation | diffuse `SAISIE_INFIRMIER / ABSENCE` après validation ; l'absence d'une exclusion ne s'annule pas (422 `ABSENCE_D_EXCLUSION`), seule la réinclusion la retire (revue du 22/10) |
| Défauts à valider | déclaration perdante 409 ; doublon avec annulée 422 ; droit de correction pour tout rattrapage ; commentaire sans motif conservé ; `tauxTva` `0.00` ; exonérée 0 % ; fuseau UTC |

## Parité

84 tests du filet exécutés contre le nouveau code : **49 verts sans changement de golden** ; **35 goldens `*_nouveau_code`** (écarts décidés) remplacent 35 goldens caducs
(déclaration 4, annulation 5, qualification 5, rattrapage 8, autorisations 3, valorisation 5, effets 2, exclusion 3). Les anciens goldens n'étaient pas commités
(filet A2 non suivi) et ont été remplacés : le comportement legacy exact n'est plus rejouable que par `known-bugs.md` (KB-ABS-10 à 21) et `git show e006fa13`
(code legacy) — à commiter avant d'écrire des `*_nouveau_code` pour les prochaines slices ; la course de déclarations donne 409 (4 manches) ou 422 (1), jamais 500.
Quelques scénarios ont changé d'acteur (rattrapage par l'administrateur ou le médecin) ou de clé (ordre des contrôles) : relus à la main, tous attribuables à une décision.

## Tests et mesures

| Niveau | Tests |
|---|---|
| Domaine (`AbsencePatientTest`, `ValorisationTest`, `CommentaireTest`) | 44 + 12 + 3 |
| Application (`EcrituresDesAbsencesTest`, doubles `AbsencesEnMemoire`, `UniteDeTravailEnMemoire`) | 26 |
| Contrat des absences : double / H2 / **PostgreSQL 16** | 10 / 9 / 12 |
| Concurrence (verrous, attente, 409) : H2 / **PostgreSQL 16** | 13 / 13 (dont les 10 du contrat) |
| ACL (forfaits, taux de TVA, table des motifs, session) | 6 + 2 + 10 + 7 (session) |
| ArchUnit du module | 9 |
| Module complet | 210 |

- Rejeu PostgreSQL 16 jetable (`bash backend/scripts/absences-a2-pg16-rejeu.sh 55449`, port != 5432, V1..V43) : 39/39 (absences, concurrence, lectures A1).
- **PIT** (`bash backend/scripts/pit-a2-module.sh`, `domain.*` + `application.*`) : 187 mutants tués sur 191 (98 %), force 98 %, lignes 98 %. Survivants : seuil de `Valorisation.depuis`
  (équivalent), signalement de `RattraperUneAbsence` non asserté, `Identifiants.requis`, `Intervenant` (garde).
- `pit-a2-cycle-de-vie.sh` (filet HTTP) cible des classes legacy retirées : historique, non rejoué.

## Corrections de la revue (`.ddd/verify/review-absences-a2.md`, 22/10)

- **KB-20** : annulation de l'absence d'une exclusion interdite (`ExclusionsDeSeances`, `ExclusionsDeSeancesJdbc`, 422 `ABSENCE_D_EXCLUSION`) ; la réinclusion retire l'absence quel que soit son état, sauf une annulée héritée (refus
  `ABSENCE_D_EXCLUSION_MODIFIEE`, jamais supprimée, § 24.4) ; critère d'horodatages supprimé. Messages fr/en/ar/kab côté Absences et Facturation.
- **Seam legacy accordé** (`seams.md`, `covered.json` `seamAllowed`) : `saveIfAQualifier` sur la réconciliation de nuit ; test de régression de la course (`AbsencePatientIntegrationTest`).
- `lock_timeout` 5 s (PostgreSQL) → 409, test sur PostgreSQL 16 ; V43 : renvoi 24.1 corrigé, l'application ne démarre pas en cas de doublon.
- KB-12/14 alignés : une qualification sans commentaire garde le commentaire (golden `commentaire_conserve_nouveau_code`) ; test du signalement du rattrapage ; commentaire de `FacturationContexteConfiguration` ;
  `qualificationEnRetard` legacy (mort) retiré ; `SignalementDesSaisies` assumé en port requis (ADR 0039).

## Points à valider

1. Absences d'exclusion **déjà annulées** en production (§ 24.4) : séance `ABSENT` sans sortie tant qu'on ne les traite pas à la main.
2. La réinclusion supprime désormais l'absence d'une exclusion même requalifiée ou rattrapée (comme le legacy B11), puisque l'annulation est interdite : confirmer.
3. **404** pour une absence inconnue sur les écritures (était 422), aligné sur A1.
4. Rattrapage réservé à `ADMIN`/`MEDECIN` : secrétaires et infirmiers le perdent (écran adapté).
5. ACL `AbsencesDesPatientsLegacy` supprimée (déplacée dans le module) ; `SignalementDesSaisiesLegacy` ajoutée à `covered.json`.
6. V43 : contrôle § 24.1 à exécuter en production AVANT déploiement.
7. Fuseau du centre toujours reporté (déclaration du jour entre minuit et 1 h refusée).

## Reste à faire

A3 (détection de nuit : retire `synthese`, `findPaged`, la duplication du forfait et du délai), A4 lectures publiées, A5 retrait, A6 clôture mensuelle. Documentation client à régénérer depuis le référentiel (RG-ABS-054 à 057 nouvelles, 003, 020 à 023 amendées).
