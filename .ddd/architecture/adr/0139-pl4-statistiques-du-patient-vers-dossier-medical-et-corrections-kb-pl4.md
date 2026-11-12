---
status: accepted
date: 2026-11-12
decision-makers: [utilisateur, architecte]
context-scope: dossier-medical, platform
related: ADR 0024, 0138 ; `.ddd/safety-net/pl4-statistiques-patient.md` ; `.ddd/safety-net/known-bugs.md` KB-PL4-1 à 5
---

# 0139 - PL4 : les statistiques du patient passent au Dossier médical ; corrections KB-PL4-1, 2, 4, 5

Refactoring sur place (ADR 0024) : mêmes 6 URLs (`/api/v1/patients/stats/{paramedical,medical,export}` en GET et `/search` en POST), mêmes
tables, mêmes rôles, même JSON, même CSV et même HTML du PDF. Pas de façade ni de routage.

## Décision de placement
`PatientStatsRestController`, `PatientStatsQueryService` et `PatientStatsSearchRequest` (plateforme) sont supprimés. Dans
`contexts/dossier-medical` : domaine `Tension`, `PeriodeDeStatistiques`, `FormatDExport` ; cas d'usage `ConsulterLesStatistiquesParamedicales`,
`ConsulterLesStatistiquesMedicales`, `ExporterLesStatistiquesDuPatient` ; ports `SeancesDuPatient`, `ImpressionDesStatistiquesDuPatient` ;
adapters `SeancesDuPatientPubliees` (ACL sur le langage publié des Séances), `StatistiquesDuPatientEnPdf`, `StatistiquesDuPatientControleur`
et son `ErreursDesStatistiquesDuPatientAdvice` (messages DM8 conservés). Les analyses viennent du langage publié existant
`StatistiquesMedicalesPubliees`.

## Écarts décidés par l'utilisateur (2026-11-12)
| KB | Décision |
|---|---|
| KB-PL4-1 | Corrigé : `centerId` absent ou illisible, `patientId`/`from`/`to` illisibles, corps POST absent ou illisible = 400 avec le code usuel du contexte |
| KB-PL4-2 | Corrigé : patient inconnu ou d'un autre centre = 404 `PATIENT_INTROUVABLE`, même réponse dans les deux cas |
| KB-PL4-3 | Reproduit : une tension illisible devient vide (dette consignée) |
| KB-PL4-4 | Corrigé : les POST `/search` ont leur propre code d'action `STATISTIQUES_PATIENT_CONSULTATION` (lecture, aussi sur un 403) ; libellé ajouté dans `AuditActionResolver` ; le journal historique n'est pas réécrit |
| KB-PL4-5 | Corrigé : garde de centre (403) avant la validation de la plage |

## Conséquences
- Goldens modifiés : nouveaux `*_nouveau_code.approved.json`, anciens archivés dans `_ecarts-legacy/**/*_avant_pl4.legacy.json`.
- `DatesIllisiblesDeLaDirectionAdvice` ne couvre plus ces routes ; `FrontieresDesContextesTest` verrouille la disparition des classes de la plateforme.
- Règles de gestion : RG-MED-049i ajoutée ; annexe de traçabilité mise à jour.
