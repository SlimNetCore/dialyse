---
slice: dossier-medical-dm3-examens
status: characterized
date: 2026-11-07
sources: [.ddd/safety-net/dossier-medical-inventaire.md, .ddd/migration/slices/dossier-medical-dm3-examens.md, ADR 0024, 0065, 0072, 0080, GEL.md, known-bugs.md KB-MED-34 à 44]
---

# Filet DM3 : demandes d'examen (legacy, refactoring sur place)

> Le texte précédent de ce fichier (2026-11-01) décrivait des scénarios non écrits ; il est remplacé. Périmètre vérifié par lecture du code : `DemandeExamenRestController` est la seule route d'examen du legacy (GET liste, POST, 4 PUT de transition). Il n'existe ni catalogue / nomenclature d'examens, ni impression, ni route de résultats propre à DM3 : les résultats sont des observations biologiques (DM6, `observations`, `demandes-examen/{id}/observations`). L'inventaire n'omet donc aucune route ; la route `demandes-examen/{id}/observations` reste attribuée à DM6 (observée seulement pour le couplage).

## Point d'observation
HTTP (MockMvc, contexte Spring complet, sécurité réelle + `RoleScopeFilter`, `CenterAccessGuard`, H2 `test`), état SQL (`demandes_examen`, `lignes_demande_examen`), audit (`AuditRequestFilter`), diffusions, notifications durables. Aucun mock du code caractérisé, **aucun code de production modifié, aucun seam** (`seamAllowed` vide). Harnais : `characterization/dossiermedical/dm3/ExamensCharacterizationSupport` (au-dessus des harnais Planification). Données fictives. `lignes_demande_examen` n'a PAS de `center_id` (ADR 0072) : un scénario le verrouille (colonnes lues dans `INFORMATION_SCHEMA`).

## Scénarios (33 tests, 50 goldens `src/test/resources/characterization/dossiermedical/dm3/<classe>/<scénario>/*.approved.json`)
| Classe | Tests | Contenu |
|---|---|---|
| `LaDemandeDExamenCreationCharacterizationIntegrationTest` | 10 | création (statut `DEMANDE`, lignes, forme de la réponse), 4 catégories + valeurs refusées, dates (défaut masquée, aujourd'hui, demain, 1900, 2056, illisible), 15 formes de lignes invalides, systèmes de codage (CIM10, ATC, LOCAL, LOINC) et normalisation, tailles de colonnes (500 à `DATA_ACCESS_ERROR`), champs facultatifs, corps / identifiants illisibles, patient inconnu ou d'un autre centre, rejeu (deux demandes) |
| `LesTransitionsDeLaDemandeDExamenCharacterizationIntegrationTest` | 8 | parcours nominal (lignes et `created_at` conservés), matrice 5 états x 4 actions (20 cas), annulation avant / après résultat, rejeu et retours en arrière (422, empreinte inchangée), conclusion (6 formes), recherche par (id, patient, centre) + 15 erreurs, données incohérentes en base, prélèvements simultanés (invariants) |
| `LaListeDesDemandesDExamenCharacterizationIntegrationTest` | 7 | tri par date décroissante + pages, 14 bornes de pagination, plafond 200, tous statuts + lignes + conclusion, forme complète (avec `Cache-Control`), listes vides, deux patients d'un même centre |
| `LesDroitsEtLesEffetsDesDemandesDExamenCharacterizationIntegrationTest` | 5 | droits 14 profils + anonyme x 6 routes, refus sans écriture, cloisonnement des centres (13 cas dont SUPERADMIN), audit (écritures et refus tracés, lectures non), événements de domaine jamais publiés |
| `LesLecteursDesDemandesDExamenCharacterizationIntegrationTest` | 3 | observation (DM6) liée à une demande : statut inchangé ; références souples vers demande inconnue / autre centre ; schéma des deux tables |

## Lecteurs de la slice (recherche par Grep ciblé)
FHIR (`FhirExportService`), KDIGO, Direction, Planification, séance, impressions : **aucun ne lit** `demandes_examen` / `lignes_demande_examen`. Seuls consommateurs : DM6 (`observations_biologiques.demande_examen_id`, référence souple sans clé étrangère), DM7 (`etapes_bilan_pre_greffe.demande_examen_id`, référence souple, non exercé ici), `MigrationRollbackJdbcAdapter` (purge technique d'un import, hors filet), `MedicalPhase3AccessIntegrationTest` (nettoyage), module gelé `contexts/dossier-medical` (non branché).

## Déterminisme (3 exécutions vertes : 1 ordre par défaut, 2 avec `MethodOrderer$Random`)
Dates explicites : FIXES nommées `<D+n>`. Seuls la date par défaut et la limite « futur » lisent `LocalDate.now()` (JVM, KB-MED-34) : dates relatives nommées `<J>`, `<J+1>`, garde d'attente de minuit (fuseau de la JVM, 2 minutes), date par défaut masquée après contrôle de plausibilité ; jamais de dépendance au jour UTC vs jour du centre Africa/Algiers. Identifiants des fixtures calculés par nom, générés `<UUID_GENERE_n>`, horodatages `<HORODATAGE>`, clés JSON triées, lignes triées dans l'assertion (KB-MED-36), `created_at` / `updated_at` comparés en base (jamais en chaîne JSON : précisions différentes).

## Seams
Aucun requis ni appliqué.

## Limites (NON verrouillé)
- H2 (`ddl-auto: update`), pas PostgreSQL : schéma Flyway réel et messages SGBD (masqués) non observés ; ordre SQL non garanti (les tris du test sont explicites).
- Concurrence : invariants seulement (KB-MED-41).
- Égalité de date entre demandes d'un même patient : ordre de la liste non défini (non verrouillé, aucune demande de même date dans les goldens de tri).
- `DemandeExamenEvent` et les ports ne sont pas dans `covered.json` (aucun scénario ne les exerce seuls) ; événements non publiés (KB-MED-37).
- Routes DM6 (`observations`) et DM7 (`etapes`) : seulement le couplage ; leur propre comportement relève de leurs filets.
- Pas de couverture de branches ni de mutation mesurées (outil non exécuté dans cette session).

## Fichiers legacy ajoutés à `covered.json` (19)
`DemandeExamenRestController` ; 3 DTO d'entrée, 2 de sortie ; `DemandeExamenRepositoryAdapter`, entités et dépôts JPA des demandes et des lignes ; domaine `bc-medical` : `DemandeExamen`, `LigneDemandeExamen`, `DemandeExamenDomainService`, `TransitionStatutDemandeSpecification`, `StatutDemandeExamen`, `CategorieExamen`, et le socle partagé `ConceptCode` / `CodingSystem` (formats et normalisation exercés ; partagés avec DM5 / DM6).

## Bugs connus et trous de sécurité
KB-MED-34 à 44 (`.ddd/safety-net/known-bugs.md`). **Sécurité (non corrigés, verrouillés)** : KB-MED-38 (aucun contrôle d'appartenance du patient), KB-MED-39 (introuvable en 400, une donnée incohérente casse la liste), KB-MED-40 (l'ADMIN ne peut pas écrire : à confirmer), KB-MED-44 (observations liées à une demande d'un autre centre ou inconnue).

## Questions ouvertes (décision pour l'utilisateur / les métiers)
Périmètre DM3 (aucune route d'examen omise, `demandes-examen/{id}/observations` laissée à DM6, à confirmer) ; droits d'écriture (KB-MED-40) ; publication de `ResultatExamenRecu` et transition automatique depuis la saisie du résultat (KB-MED-37) ; conclusion obligatoire, LOINC obligatoire, prescripteur = compte (KB-MED-42) ; 400 vs 404 (KB-MED-39) ; horloge du centre (KB-MED-34).
