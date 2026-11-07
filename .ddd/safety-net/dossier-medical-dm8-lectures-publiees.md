---
slice: dossier-medical-dm8-lectures-publiees
status: characterized
date: 2026-11-07
sources: [.ddd/migration/slices/dossier-medical-dm8-lectures-publiees.md, ADR 0024, 0072, known-bugs.md KB-MED-98 à 105]
---

# Filet DM8 : lectures publiées du Dossier médical (legacy, refactoring sur place)

## Périmètre vérifié par lecture du code
- `DossierMedicalPatientRestController` : GET (MEDECIN, ADMIN, INFIRMIER), POST et PUT (MEDECIN) `/api/v1/patients/{id}/dossier-medical`, tables `dossier_medical_patient`.
- `FhirExportRestController` / `FhirExportService` : GET `.../dossier-medical/export-fhir` (MEDECIN), bundle de Patient, Condition, AllergyIntolerance, Observation, MedicationRequest, Procedure.
- `PatientStatsRestController` / `PatientStatsQueryService` : `stats/medical` (GET, POST `search`, ADMIN INFIRMIER MEDECIN SECRETAIRE) et `stats/export` (GET, POST `search`, ADMIN ; csv et pdf).
- `DirectionIndicatorsQueryService` : GET `/api/v1/direction/indicators` (DIRECTION) sur `resultats_analyses`, `serologies_patient`, `bilans_pre_greffe`.
- Déjà filetés ailleurs, cités et non rejoués : lecteurs de Planification `RisquesInfectieuxJdbc` et `ContraintesDesPatientsJdbc` (DM4 / P5, DM5), `DirectionBreakdownQueryService` (Pilotage), `VoletMedicalRestController` (Séance).

## Point d'observation
HTTP (MockMvc, contexte Spring complet, sécurité réelle + `RoleScopeFilter` + `CenterAccessGuard`, H2 `test`), état SQL des tables réelles, audit (`AuditActionResolver`), PDF extrait avec PDFBox. **Aucun seam, aucun code de production modifié** (`seamAllowed` vide, voir `seams.md`).

## Scénarios (38 tests, 38 goldens `src/test/resources/characterization/dossiermedical/dm8/<dossier>/<scénario>/*.approved.json`)
| Dossier | Classe | Tests | Contenu |
|---|---|---|---|
| dossier | `LeDossierMedicalDeBaseDuPatient…` | 11 | lecture (ne crée rien), enregistrement, remplacement (création conservée), valeurs et bornes de colonnes, absence de garde de patient, profils, refus sans écriture, cloisonnement par centre, audit, écritures simultanées, doublon et ligne illisible |
| fhir | `LExportFhirDuDossierMedicalCharacterization…` | 9 | ordre du bundle, ressources détaillées, bundle vide, patient inconnu ou autre centre, codes (CIM-10, ATC, LOINC, local), statuts, ligne illisible, plafond 500, droits |
| stats | `LesStatistiquesMedicalesEtLesExportsDuPatient…` | 10 | agrégats et tendances, bornes de période incluses, sans donnée, recherche par corps, CSV, PDF, recherche d'export, profils, cloisonnement, audit |
| direction | `LesIndicateursMedicauxDeLaDirection…` | 8 | forme et seuil d'anonymat (4 masqué, 0 reste 0), taux dans / sous / au-dessus de la cible à 5 patients, dernier résultat non nul de la période, sérologies positives, bilans de greffe, période (ordre, 5 ans), périmètre de la société, droits et audit |

## Déterminisme
Dates fixes `<D+n>` ; jour de la JVM avec garde de minuit ; identifiants et horodatages masqués ; centre renommé puis restauré pour le PDF ; concurrence verrouillée par invariants uniquement ; données fictives. Goldens approuvés un par un (lecture du `received`, puis écriture de l'`approved`).

Exécutions : suite DM8 3 fois consécutives (38 verts), dont une en `-Dsurefire.runOrder=random`, 0 flaky ; suite complète `./mvnw -o test -fae` (voir le rapport de clôture).

## Limites (non verrouillé)
Couverture de branches non mesurée et aucune mutation exécutée (aucun outil configuré dans le module, PIT documenté dans `.ddd/research/mutation-testing-pit.md`, tests Spring trop lents). Schéma PostgreSQL non rejoué (H2) : l'ordre FHIR contre module des accès vasculaires sans date (DM5) n'est pas tranché ici, tri nullable non observé sur PostgreSQL. Stock des indicateurs Direction (articles, lots) : seulement à zéro, non rejoué avec données (filet Stock). Observance (`alertes_observance`) et alertes autres que `HB_HORS_CIBLE` non couvertes. Le PDF est un golden lié à l'environnement (polices). Concurrence POST / PUT par invariants, pas de contrat à deux connexions. Lectures non tracées en audit sauf `GET dossier-medical` (KB-MED-100). Valeurs KDIGO non validées médicalement.

## Questions ouvertes
Reproduire ou corriger KB-MED-98 à 105, en particulier l'absence de garde de patient (98), les 500 sur validation (99), l'audit (100), le plafond silencieux FHIR (101) et la fuite par différence des totaux Direction (104). Rattachement de KB-MED-70 (codes FHIR) à DM8 : comportement figé tel quel (102).
