---
slice: dossier-medical-dm6-biologie
status: characterized
date: 2026-11-07
sources: [.ddd/safety-net/dossier-medical-inventaire.md, ADR 0024, 0072, 0081-0084, known-bugs.md KB-MED-72 à 84]
---

# Filet DM6 : biologie, observations LOINC et résultats d'analyses (legacy, refactoring sur place)

## Périmètre vérifié par lecture du code
Deux contrôleurs seulement : `ObservationBiologiqueRestController` (GET liste, GET `demandes-examen/{id}/observations`, POST, PUT, DELETE) et `ResultatAnalyseRestController` (GET, POST `analyses/search`, POST, PUT, DELETE). Aucune route de saisie en lot, d'import, d'export, d'impression ou de résultats par séance dans le legacy HTTP (recherche par `Grep` sur `observations_biologiques` / `resultats_analyses` : seuls les lecteurs listés ci-dessous et l'import / rollback d'historique en SQL). L'inventaire n'omet donc aucune route. Tables réelles : `observations_biologiques`, `resultats_analyses` (jamais les tables du module gelé, ADR 0072) ; `center_id` n'est pas ajouté à `lignes_demande_examen`. Deux modèles coexistent sans projection : observation codée LOINC (valeur numérique + unité OU texte) et bilan à 20 colonnes fixes. Aucune interprétation normal / anormal / critique n'est calculée par DM6 (elle vit chez les lecteurs : anémie DM2, KDIGO DM7).

## Point d'observation
HTTP (MockMvc, contexte Spring complet, sécurité réelle + `RoleScopeFilter` + `CenterAccessGuard`, H2 `test`), état SQL des deux tables, audit (`AuditRequestFilter`), diffusions, notifications durables, et lecteurs observés tels quels : `suivi-anemie` (DM2, `BilansBiologiquesJdbc`), `stats/medical` et `stats/export` (DM8), `greffe/kdigo` (DM7), `export-fhir` (DM8). **Aucun seam, aucun code de production modifié** (`seamAllowed` reste vide).

## Scénarios (37 tests, 37 goldens `src/test/resources/characterization/dossiermedical/dm6/<lot>/<dossier>/<scénario>/*.approved.json`)
| Lot | Classe | Tests | Contenu |
|---|---|---|---|
| 1 observations | `LEnregistrementDUneObservationBiologique…` | 7 | forme et défauts (statut FINAL, source), valeur numérique / texte (12 cas), bornes de colonnes, analyte LOINC (15 cas), statut et date (futur, 1900), doublons et référence de demande, corps illisibles / patient inconnu |
| 1 | `LaConsultationEtLaCorrectionDesObservationsBiologiques…` | 7 | tri et pagination, filtres code et période (injection de motif), forme complète (Cache-Control), correction (12 cas dont dérivée immuable), correction par patient + centre, suppression 204 silencieuse, lecture par demande (KB-MED-44) |
| 1 | `LesDroitsEtLesEffetsDesObservationsBiologiques…` | 5 | droits 14 profils + anonyme x 5 routes, refus sans écriture, cloisonnement (15 cas), audit, export FHIR, diffusions / notifications vides, statut de la demande inchangé (KB-MED-37) |
| 2 analyses | `LEnregistrementEtLaModificationDesAnalyses…` | 6 | forme et date par défaut, valeurs sans contrôle et arrondi `numeric(38,2)`, corps illisibles / patient inconnu, PUT = remplacement complet, PUT = upsert et déplacement inter-patients / inter-centres, refus |
| 2 | `LaConsultationEtLaRechercheDesAnalyses…` | 5 | tri et pagination, période (inversée = 400), forme complète, recherche (18 cas), suppression 204 silencieuse |
| 2 | `LesDroitsEtLesEffetsDesAnalyses…` | 7 | droits 14 profils + anonyme x 5 routes, refus, cloisonnement (14 cas), audit (dont la recherche journalisée comme création), lecteurs (anémie, statistiques, CSV), effets de chaque écriture sur anémie / KDIGO / statistiques, FHIR |

## Déterminisme
Dates explicites fixes `<D+n>` ; seules la date par défaut du bilan et la limite « futur » d'une observation lisent le jour de la JVM (`<J>`, `<J+1>`), avec garde de minuit (attente si moins de 2 min) ; aucune dépendance au jour UTC ni à Africa/Algiers. L'âge du patient du scénario KDIGO est calculé à partir de `LocalDate.now()` (né il y a 50 ans et 30 jours : stable). Identifiants et horodatages masqués (alias nommés) ; le journal d'audit est trié sur l'alias de l'identifiant (les identifiants générés sont aléatoires). Données fictives (« Patient <alias> », valeurs rondes). Goldens créés un par un (lecture du `received`, puis écriture de l'`approved`), aucune approbation en lot. 3 exécutions consécutives de la suite DM6 plus la suite complète finale, dont plusieurs en ordre aléatoire (`-Dsurefire.runOrder=random`) : 0 flaky (une flakiness d'ordre de l'audit détectée et corrigée dans le harnais avant clôture).

## Limites (non verrouillé)
Concurrence (écritures simultanées, pas de verrou) ; schéma PostgreSQL non rejoué (le DDL `V1__baseline_schema.sql` est identique pour les décimales : `numeric(38,2)` et `numeric(14,4)`) ; ordre des ex æquo de date (tri sans départage) et de la lecture par demande (aucun tri) ; indicateurs Direction (`DirectionIndicatorsQueryService`, lecture SQL de `resultats_analyses`), PDF de statistiques (`exportPdf`), import et rollback d'historique, purge : non observés ; DM8 observé seulement par les trois lectures citées ; aucune mesure de couverture de branches ni mutation (aucun outil configuré, PIT documenté dans `.ddd/research/mutation-testing-pit.md`, non lancé : tests Spring trop lents). Les unités et valeurs de référence ne sont stockées nulle part (`ObservationBiologique` n'a ni référence ni interprétation).

## Questions ouvertes (à confirmer avec les métiers et le DPO)
Rattachement de la route `demandes-examen/{id}/observations` à DM6 (confirmé par la note DM3). Reproduire ou corriger KB-MED-72 à 84, en particulier les trous de sécurité KB-MED-73 (aucune garde de patient) et KB-MED-74 (le PUT d'une analyse écrase un bilan d'un autre patient ou d'un autre centre), KB-MED-77 (lecture par demande sans garde), la journalisation de la recherche comme création (KB-MED-81) et l'absence de plausibilité des valeurs (KB-MED-79). Fusion future des deux modèles (observation LOINC / bilan à colonnes fixes) : décision de modèle, hors filet.
