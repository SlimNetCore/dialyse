---
status: proposed
date: 2026-11-12
sources: ADR 0139, .ddd/safety-net/known-bugs.md (KB-PL4-1..5)
---

# PL4 - réalisation

Statistiques du patient (6 routes) déplacées de `backend/platform` vers `contexts/dossier-medical`. Refactoring sur place, mêmes URLs/tables/rôles.

## Créé (dossier-medical)
- domain : `Tension`, `PeriodeInversee`, `PeriodeDeStatistiques`, `FormatDExportInconnu`, `FormatDExport`
- application : `SeancesDuPatient`, `StatistiquesParamedicales`, `StatistiquesMedicales`, `ExportDesStatistiques`, `ImpressionDesStatistiquesDuPatient`, `GardeDesStatistiques`, `LecturesDesStatistiques`, trois cas d'usage `Consulter...Paramedicales/Medicales`, `ExporterLesStatistiquesDuPatient`
- infrastructure : `acl/seance/SeancesDuPatientPubliees`, `impression/StatistiquesDuPatientEnPdf`
- interfaces/http : `StatistiquesDuPatientControleur`, `ErreursDesStatistiquesDuPatientAdvice`, DTO `RechercheDeStatistiquesDemande`, `StatistiquesReponses`
- câblage : `IndicateursEtStatistiquesConfiguration`

## Supprimé / modifié (plateforme)
- Supprimés : `PatientStatsRestController`, `PatientStatsQueryService`, `PatientStatsSearchRequest`, `PatientStatsRestControllerTest` (retirés de covered.json).
- Modifiés (couverts) : `AuditActionResolver` (code `STATISTIQUES_PATIENT_CONSULTATION`), `DatesIllisiblesDeLaDirectionAdvice`.

## Goldens
Corrections : cloisonnement, audit (DM8) et entrées mal formées, sans données (PL4) en `*_nouveau_code.approved.json` ; anciens en `_ecarts-legacy/**/*_avant_pl4.legacy.json`. Approuvés un à un après lecture du `.received`.

## Écarts
KB-PL4-1, 2, 4, 5 corrigés ; KB-PL4-3 reproduit (dette). Voir ADR 0139.

## Docs
Glossaire dossier-medical, RG-MED-049i, annexe de traçabilité, 07-seances, known-bugs.

## Questions ouvertes
- Doc fonctionnelle/maquette : non régénérée ici (à faire par l'appelant si la procédure l'exige).
- Les écrans/filtres du journal d'audit ne listent pas les codes en dur (libellé porté par le résolveur) : à confirmer côté frontend.
