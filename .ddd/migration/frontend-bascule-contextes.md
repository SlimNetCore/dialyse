# Bascule frontend vers les contextes (tous contextes)

Date : 2026-11-01. Principe : **aucun composant n'est rebranché tant que le backend
n'a pas la parité** (mêmes URLs stables ou DTOs compatibles, § O5). En attendant, chaque
contexte expose un SDK typé testé ; la bascule se fera service par service.

## SDK créés (branchés sur aucun composant — pas de régression)

| Fichier                                           | Base URL                                                | Couvre                                                                                 | Spec vitest                                             |
|---------------------------------------------------|---------------------------------------------------------|----------------------------------------------------------------------------------------|---------------------------------------------------------|
| `core/api/organisation-api.service.ts`            | `/api/v1/organisation`                                  | sociétés, centres, salles, créneaux, médecins, machines, protocoles                    | `organisation-api.service.spec.ts` (6 tests)            |
| `core/api/patient-couverture-api.service.ts`      | `/api/v1/patient-couverture`                            | patients, assurés, PEC, attestations                                                   | `patient-couverture-api.service.spec.ts` (5 tests)      |
| `core/api/seance-api.service.ts`                  | `/api/v1/seances`, `/api/v1/derogations-regularisation` | séances, validation/signature/régularisation, forfait, dérogations                     | `seance-api.service.spec.ts` (5 tests)                  |
| `core/api/dossier-medical-context-api.service.ts` | `/api/v1/dossier-medical`                               | antécédents, allergies, sérologies, demandes d'examen, observations, bilans pré-greffe | `dossier-medical-context-api.service.spec.ts` (6 tests) |
| `core/api/pilotage-api.service.ts`                | `/api/v1/pilotage`                                      | tableau de bord, instantanés, alertes, exports                                         | `pilotage-api.service.spec.ts` (5 tests)                |

Tests : `vitest run src/app/core/api/<...>.spec.ts` → **27/27 verts**.

## Règle de bascule par contexte (valable pour tous)

1. **Parité backend d'abord** : le contrôleur du contexte doit couvrir chaque endpoint
   legacy consommé (mêmes URLs stables recommandées, ou DTOs compatibles + écrans adaptés).
   Écarts connus : logos sociétés, activer/désactiver, transfert, import/export générique,
   référentiels non couverts (centres-payeurs, agences, caisses, transporteurs, forfaits,
   etats-patients, articles, generateurs), journal/dashboard/calendar/scan des séances,
   ordonnances/greffe/donneurs/KDIGO du dossier, abords vasculaires, résultats d'analyses
   structurés, EPO/fer, observance.
2. ** Dette de sérialisation ** : les contrôleurs des contextes retournent aujourd'hui des
   objets du domaine aux accesseurs style record, sérialisés `{}` par Jackson (constaté
   sur `BilanPreGreffe`, même cause que DM7). Avant toute bascule composant : exposer des
   DTOs sérialisables (ou `@JsonProperty`) et les faire valider par les specs vitest (assertions sur le corps, pas
   seulement le statut).
3. **Composants** : rebrancher un composant = remplacer l'injection du service legacy par
   le SDK du contexte, adapter les types, vérifier `mat-paginator` + i18n (fr/ar/kab/en),
   responsive 320px→desktop, puis e2e Playwright du parcours.
4. **Nettoyage** : supprimer le service legacy et ses specs quand plus aucun composant
   ne l'utilise (règle 17, zéro code mort) ; retirer les endpoints legacy côté backend
   dans la même tâche (slice de retrait).

## Mapping legacy → nouveau (point de départ)

| Legacy (frontend)                                                                    | Nouveau SDK                                                             | Écart bloquant la bascule                                                                                                                                |
|--------------------------------------------------------------------------------------|-------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------|
| `SocieteApiService`, `SocieteAdminApiService` (`/api/v1/societes`)                   | `OrganisationApiService`                                                | logos, activer/désactiver, transfert, centres CRUD complets, recherche/pagination                                                                        |
| `ReferentialApiService` (`/api/v1/referentials/*`, 13 listes)                        | `OrganisationApiService` (salles/créneaux/medecins/machines/protocoles) | centres-payeurs(+details), agences, caisses, transporteurs, categories-transport, forfaits, etats-patients, articles, generateurs, positions             |
| `ReferentialAdminApiService` (`/api/v1/admin/referentials/*`)                        | — (aucun équivalent)                                                    | CRUD générique + import/export + templates : décision ADR requise                                                                                        |
| appels patients/assurés/PEC/attestations (`BackendApiService`, `seance.store`, etc.) | `PatientCouvertureApiService`                                           | vérifier chaque endpoint legacy vs nouveau (DTOs)                                                                                                        |
| appels séances (`BackendApiService`, `seance.store`)                                 | `SeanceApiService`                                                      | journal, dashboard (+details/export), calendar, scan, consommables, volets, raccourcis, deverrouillage legacy                                            |
| `DossierMedicalApiService` (`/api/v1/patients/*`)                                    | `DossierMedicalContexteApiService`                                      | abords vasculaires, prescriptions EPO/fer, analyses structurées, ordonnances, greffe complète (donneurs, RCP, KDIGO, export PDF), observance, constantes |
| `DirectionApiService` (`/api/v1/direction/*`)                                        | `PilotageApiService`                                                    | synthèse PDF/HTML via `ModeleDocumentPrinter`, temps réel, snapshots legacy                                                                              |
