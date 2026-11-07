---
slice: dossier-medical-dm5-antecedents-allergies
status: characterized
date: 2026-11-07
sources: [.ddd/safety-net/dossier-medical-inventaire.md, ADR 0024, 0072, 0081, 0082, 0083, known-bugs.md KB-MED-58 à 71]
---

# Filet DM5 : antécédents, allergies et abords vasculaires (legacy, refactoring sur place)

## Périmètre vérifié par lecture du code
Trois contrôleurs seulement : `AntecedentRestController` (GET, POST, PUT, PUT `resoudre`, DELETE), `AllergieRestController` (GET, GET `critiques`, POST, PUT, DELETE), `AbordVasculaireRestController` (GET, POST, PUT, DELETE). Tables réelles : `antecedents_medicaux`, `allergies_patient`, `abords_vasculaires` (entités JPA `*JpaEntity`) ; les schémas parallèles gelés `antecedents` / `allergies` du module `contexts/dossier-medical` (GEL.md, ADR 0072) ne sont ni lus ni écrits. Les abords vasculaires sont rattachés à DM5 par délégation (à confirmer avec les métiers). Lecteurs : FHIR (`FhirExportService`, `Condition` / `AllergyIntolerance` / `Procedure`), Planification (`ContraintesDesPatientsJdbc`, compétence `CATHETER`, RG-PLN-097), `GreffePdfService` (antécédents), import et rollback d'historique.

## Point d'observation
HTTP (MockMvc, contexte Spring complet, sécurité réelle + `RoleScopeFilter` + `CenterAccessGuard`, H2 `test`), état SQL des trois tables, audit (`AuditRequestFilter`), diffusions, notifications durables, export FHIR et, pour les abords, le bean `ContraintesDesPatients` de la Planification appelé tel quel (sans mock ni modification) à un jour fixe. **Aucun seam, aucun code de production modifié** (`seamAllowed` reste vide).

## Scénarios (51 tests, 51 goldens `src/test/resources/characterization/dossiermedical/dm5/<lot>/<dossier>/<scénario>/*.approved.json`)
| Lot | Classe | Tests | Contenu |
|---|---|---|---|
| 1 antécédents | `LEnregistrementDUnAntecedent…` | 7 | forme et ligne, diagnostic (CIM10 / LOINC / ATC / LOCAL, normalisation, libellé libre, systèmes inconnus), 5 types, dates (futur, 1900, 2200, illisible), doublons (actif / résolu / autre système / autre patient), bornes de colonnes, corps illisibles, patient inconnu / autre centre |
| 1 | `LaConsultationEtLaResolutionDesAntecedents…` | 6 | tri et pagination, forme complète (Cache-Control), correction destructrice (17 cas), unicité à la correction, résolution (13 cas : rejeu, avant le début, sans date, future), suppression silencieuse |
| 1 | `LesDroitsEtLesEffetsDesAntecedents…` | 5 | droits 14 profils + anonyme x 5 routes, refus sans écriture, cloisonnement (15 cas dont SUPERADMIN, session B), audit, export FHIR `Condition`, diffusions et notifications vides |
| 2 allergies | `LEnregistrementDUneAllergie…` | 7 | forme et défauts (date, statut), substance, listes fermées, HAUTE exige des manifestations, dates, doublons autorisés, corps illisibles / patient inconnu |
| 2 | `LaConsultationEtLaCorrectionDesAllergies…` | 5 | tri et pagination, forme complète, `critiques` (toute criticité HAUTE, REFUTEE comprise ; évolution après écritures), correction étroite (13 cas), suppression silencieuse |
| 2 | `LesDroitsEtLesEffetsDesAllergies…` | 5 | droits 14 profils + anonyme x 6 routes (INFIRMIER refusé y compris sur `critiques`), refus, cloisonnement, audit, export FHIR `AllergyIntolerance` |
| 3 abords | `LEnregistrementEtLaModificationDesAbordsVasculaires…` | 6 | forme, champs libres et bornes, dates, PUT = remplacement complet, PUT = upsert et déplacement inter-patients / inter-centres, corps illisibles / patient inconnu |
| 3 | `LaConsultationEtLaSuppressionDesAbordsVasculaires…` | 3 | tri, pagination, lecture ADMIN / INFIRMIER, forme complète, suppression silencieuse |
| 3 | `LesDroitsEtLesEffetsDesAbordsVasculaires…` | 5 | droits (lecture ouverte à l'INFIRMIER), refus, cloisonnement, audit, export FHIR `Procedure` |
| 3 | `LesAbordsVasculairesEtLaPlanification…` | 2 | règle `CATHETER` du lecteur (13 profils d'abords, 3 jours de calcul) et effet de chaque écriture HTTP (POST, PUT, DELETE) sur la compétence exigée |

## Déterminisme
Dates explicites fixes `<D+n>` ; seules la date par défaut d'une allergie et la limite « futur » lisent le jour de la JVM (`<J>`, `<J+1>`, `<J+30>`), avec garde de minuit (attente si moins de 2 min) ; aucune dépendance au jour UTC ni à Africa/Algiers. Le lecteur de la Planification est appelé avec un jour fixe. Identifiants et horodatages masqués (`<HORODATAGE>`, alias nommés). Données fictives (« Patient <alias> », substances et diagnostics génériques). Les listes sont triées par l'API ; l'ordre des abords sans date dépend du SGBD (H2 seul rejoué) : l'export FHIR des abords est donc trié par type dans le test. 3 exécutions consécutives de la suite DM5 (dont une en ordre aléatoire, `-Dsurefire.runOrder=random`) : 0 flaky.

## Limites (non verrouillé)
Concurrence (doublons simultanés d'antécédents, pas de verrou) ; schéma PostgreSQL (H2 seul : bornes de colonnes, tri des NULL à rejouer) ; usage de la compétence `CATHETER` par l'optimisation (verrouillé par les filets de Planification, pas ici) ; `GreffePdfService` (antécédents, DM7), import et rollback d'historique, purge, export FHIR hors des trois ressources de DM5 ; pas de mesure de couverture de branches ni de mutation testing (aucun outil configuré dans le `pom.xml` ; PIT documenté dans `.ddd/research/mutation-testing-pit.md`, non lancé : tests Spring trop lents).

## Questions ouvertes (à confirmer avec les métiers et le DPO)
Rattachement des abords vasculaires à DM5. Reproduire ou corriger KB-MED-58 à 71, en particulier les deux trous de sécurité KB-MED-61 (aucune garde de patient) et KB-MED-62 (le PUT d'un abord écrase un abord d'un autre patient ou d'un autre centre), l'accès de l'infirmier aux allergies critiques (KB-MED-69), le statut REFUTEE dans le bandeau d'allergies critiques (KB-MED-66) et les codes FHIR inventés (KB-MED-70).
