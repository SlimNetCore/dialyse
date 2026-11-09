---
slice: planification-p5-placement
status: built (2026-10-27, session 3/3) ; ADR 0059 proposed ; extension de seam Sérologie + audit ratifiée par l'utilisateur (2026-10-27)
date: 2026-10-27
order: 5
context: planification
sources: [.ddd/safety-net/planification-p5-placement.md, .ddd/tactical/planification.md (P1-P8, P26), ADR 0049 à 0059, KB-PLN-52 à 64]
---

# P5 — Placement (terminée)

Refonte sur place (ADR 0024) : pas de façade ni de drapeau, mêmes tables et mêmes URLs. Décisions utilisateur du 2026-10-27 : verrou avec création
de la ligne `planning_parametres` + 409, garde de centre et rôles sur la fiche, 400 paramètres illisibles, fuseau du centre, seam Patient,
isolement/sérologie, GMAO d'abord. Décisions techniques : ADR 0059 (proposed), ADR 0055 amendée (variante limitée au paramétrage).

## Fait

| Étape | Contenu |
|---|---|
| 0 — GMAO (additif) | événement publié `gmao.v1` `GenerateurDevenuIndisponible` (avec désignation), port publié `IndisponibilitesDesGenerateurs` (consommé en P8) |
| Session 1 — domaine + application | `PlacesDuCentre` (racine), `Place`, `PropositionsDePlace`, `CategorieDIsolement`, 6 cas d'usage, ports publiés `PlacementDesPatients`, `ReaffectationEnIsolement`, `PlacesDesPatients`, `CapaciteDesCentres` |
| Session 2 — adapters | `PlacesDuCentreJdbc`, `VerrouDesPlacesJdbc`, `RisquesInfectieuxJdbc`, `NomsDesPatientsJdbc`, ACL ressources/salles/notifications ; `PropositionsControleur`, `SallesControleur`, `GenerateurDevenuIndisponibleEcouteur` (gmao.v1) ; seam Patient (`PatientRestController` -> ACL `PlacementDeLaFiche`) |
| Session 3 — sérologie | `SerologieRestController` -> ACL `ReaffectationEnIsolementDeLaSerologie` : sérologie + réaffectation en UNE transaction (flush JPA avant la lecture SQL de la Planification), sérologie hors centre refusée (422), `ISOLEMENT_IMPOSSIBLE` dédupliquée, retour en salle ordinaire PROPOSÉ ; audit métier `SEROLOGIE_ENREGISTREE/MODIFIEE/SUPPRIMEE` (`AuditActionResolver`, KB-PLN-62) ; `SerologiePlacementHookTest` réécrit |
| Session 3 — retrait du legacy | supprimés : `PlacementPatientService`, `PlanningAffectationQueryService`, `GenerateurIndisponibleService` et leurs 3 tests (`PlacementPatientIntegrationTest`, `PlanningAffectationIntegrationTest`, `GenerateurIndisponibleServiceTest`) ; déjà supprimés en session 2 : `GmaoEvenementsPlanificationListener`, `PlanningAffectationRestController`, `SalleGenerateursRestController`. `PlanningAffectationRestControllerTest` (semaine) reste cohérent (vert) |
| Session 3 — frontend | libellé `RETOUR_SALLE_ORDINAIRE_PROPOSE` (fr/en/ar/kab, titre + message), présentation (`notification-presentation.util.ts`), rafraîchissement du planning (`planning.util.ts`), spec ; 165 tests unitaires verts (présentation + planning) |
| Seam | Patient (`PatientRestController`) et Sérologie (`SerologieRestController`, extension ratifiée par l'utilisateur le 2026-10-27) **fermés** : `seamAllowed` vide |

## Corrections de la revue P5 (2026-10-27, `.ddd/verify/review-planification-p5.md`)

| Constat | Correction |
|---|---|
| 1 major — `lock_timeout` jamais posé (transaction ouverte par l'ACL) | `UniteDeTravailSpring` pose `SET LOCAL lock_timeout` au premier passage de la transaction, quelle que soit son origine ; 3 tests de contrat qui passent par une transaction d'appelant (fiche, sérologie, optimisation), **échouent avec l'ancienne logique sur PG16 et passent avec la nouvelle** (H2 et PG16, 24 tests PG) |
| 2 major — optimisation sans verrou | seam `OptimisationApplicationService` (autorisé 2026-10-27, **fermé**) : port publié `VerrouDesPlacesDuCentre` appelé avant la relecture de l'empreinte ; test de course fiche/optimisation H2 + PG16 ; KB-PLN-65 ; ADR 0059 §9 |
| 3 major — extension de seam | **ratifiée par l'utilisateur par écrit le 2026-10-27** (`SerologieRestController`, `AuditActionResolver` `SEROLOGIE_*`) |
| 4 — temps réel avant commit | alertes à identifiant imposé diffusées `afterCommit` (`NotificationService`) |
| 5 — clé d'idempotence | jour du centre de `survenuLe` (`HorlogeDesCentres.jourDe`) ; test de rejeu après minuit |
| 6 — `nbPatients` | compte tous les patients du générateur (comme le legacy) ; test |
| 7 — ordre des verrous sérologie | exception documentée (ADR 0059 §10) |
| 9 — nom de test | `le_controle_cree_la_ligne_de_parametres_par_defaut_du_centre_et_la_verrouille` |
| 11 — test Absences à date relative | `ClotureMensuelleDesAbsencesNouveauCodeIntegrationTest` : plus de jour de mois fixe pour la déclaration en M-2 (dernier jour du mois, alias du golden inchangé), `assumeTrue` les ~12 jours/an où aucun jour de M-2 n'est déclarable (simulation 2026-2028 : l'ancien jour fixe échouait 40 jours/an) ; code de production et goldens inchangés |
| 12 — dates de retrait | jalons dans `roadmap.md` (fin de P6 / P7 / P8) |

## Conservé volontairement (dette vers P7 / P8)

- `Patient.libererPlacement` (P7, 04:00) non touché ; la fiche écrit encore les colonnes de place (ADR 0052).
- `PlacementPatientPort` / `PlacementPatientJdbcAdapter` (`definirJours`, `deplacer` : application d'une proposition par l'optimisation), `PlanificationAffectationService` (bc-seance : optimisation, vérification des déplacements), `PlanningDonneesPort` + `PlanningDonneesJdbcAdapter` (ACL `PlanningDesPlacesLegacy`, présence, optimisation), `SalleVuePort` + `SalleVueJdbcAdapter` + `SalleGenerateursService` (capacité GMAO `CapaciteDesSallesLegacy`, filet GMAO G3) : encore lus, retrait avec P6/P8. `placementActuel` et `nomPatient` du port ne sont plus appelés en production (tests de l'optimisation seulement).
- Corps de fiche illisible (JSON, identifiants) : toujours 500 (`ApiExceptionHandler` global, hors seam) — KB-PLN-56 corrigé pour propositions et salles seulement.

## Parité (filet P5, 76 tests, tous verts sur le nouveau code)

Écarts DÉCIDÉS : 28 goldens P5 + 1 golden GMAO approuvés en `*_nouveau_code` ; l'ancien comportement est archivé AVANT réécriture dans `p5-placement/_ecarts-legacy/**/*.legacy.json` (28 fichiers) et `gmao/planification/_ecarts-legacy/` (1), pris dans HEAD.

| Famille | Goldens | Décision |
|---|---|---|
| garde de centre / rôles (KB-PLN-52) | `autorisations/matrice_routes_x_profils`, `fiche/autre_centre`, `fiche/autre_centre_etat` | 403 ; fiche non modifiée |
| patient inconnu / hors centre | `fiche/requetes_invalides`, `isolement/requetes_et_perimetre` | 422 `PLACEMENT_PATIENT_INTROUVABLE` (code nouveau, à valider) |
| verrou (KB-PLN-53) | `fiche/aucune_ligne_de_parametres`, `fiche/course_sur_la_meme_place` (assertion réécrite : jamais deux acceptés, 200/409/422) | ligne par défaut créée ; course sérialisée |
| isolement réservé (KB-PLN-55) | `fiche/isolement_a_l_enregistrement`, `..._etat` | 422 `PLACEMENT_ISOLEMENT_RESERVE` (code nouveau) |
| entrées illisibles (KB-PLN-56, 64) | `propositions/{creneau_et_salle_souhaites, garde_de_centre, isolement, jours_imposes, limite, seances_invalides}`, `salles/{isolation_de_centre, pagination}` | 400 ; `size` plafonnée à 100 |
| alerte du générateur (KB-PLN-63) | `gmao/alertes_rejouees`, GMAO `alerte_selon_type_d_equipement` + assertions de `GenerateurIndisponibleCharacterizationIntegrationTest` (rejeu : 1) et `EquipementHttpCharacterizationIntegrationTest` (panne puis réforme = 1) | une alerte par générateur et par jour ; les 10 tests GMAO publient AUSSI `GenerateurDevenuIndisponible` |
| « nom prénom » (accepté) | 6 `isolement/*_notifications` + 3 `isolement/*_diffusions` | affichage « nom prénom » partout |
| sérologie (KB-PLN-60, 61, 62) | `isolement/modification_de_serologie_notifications` (+ alerte `RETOUR_SALLE_ORDINAIRE_PROPOSE`), `isolement/audit_de_la_serologie` | retour proposé ; audit métier |

Test « mois 19 » (`ClotureMensuelleDesAbsencesNouveauCodeIntegrationTest`) : NE reproduit PAS sur un worktree jetable de HEAD (10/10 verts), ni isolément sur l'arbre courant (10/10), ni dans les deux builds complets de la session 3 : aucun lien trouvé avec P5, échec de session 2 non reproductible (sans doute dépendant de l'état de la base partagée entre classes de tests pendant le développement du filet) ; à surveiller.

## Preuves

| Contrôle | Résultat |
|---|---|
| `./mvnw -o test` complet | BUILD SUCCESS : plateforme 2368 tests, 0 échec, 4 ignorés ; module planification 400 ; GMAO 749 |
| `check-arch.ts` | 0 violation |
| ArchUnit du module | vert (dans le build) |
| rejeu PG16 (`planification-p5-pg16-rejeu.sh 55471`) | code 0 : 20 tests du contrat PostgreSQL des places (dont `lock_timeout` et courses), contrôles SELECT-only du schéma réel + 5 requêtes de `verifications-production.md` exécutables |
| PIT du module (`pit-p5-module.sh`) | 1002 mutants, 912 tués (91 %), force des tests 94 %, 30 sans couverture (survivants non analysés un à un) |
| frontend | 165 tests unitaires verts (pas de ng serve / Playwright) |

## Plan de bascule

Refonte sur place : pas de palier ni de drapeau (ADR 0024). Avant déploiement : requêtes de `verifications-production.md` (section Slice P5). Après : surveiller 403 sur `POST`/`PUT /patients`, 422 `PLACEMENT_*`, 409 `PLANIFICATION_ECRITURE_CONCURRENTE`, 400 sur `/planning/affectations` et `/planning/salles`, unicité des alertes. Retour arrière : redéploiement de la version précédente (aucune migration, la ligne `planning_parametres` créée par défaut est inoffensive pour l'ancien code).

## Points à valider par l'utilisateur

1. Extension du seam à `SerologieRestController` et `AuditActionResolver` (hors de l'autorisation initiale) — voir `.ddd/safety-net/seams.md`.
2. Codes nouveaux `PLACEMENT_ISOLEMENT_RESERVE` et `PLACEMENT_PATIENT_INTROUVABLE` (remplace le 400) ; course sur la même place : 409 possible quand deux écritures créent simultanément la ligne par défaut.
3. « nom prénom » partout dans les alertes ; libellés fr/en/ar/kab de `RETOUR_SALLE_ORDINAIRE_PROPOSE` à relire par un locuteur.
4. 400 pour une fiche illisible : non fait (global) ; `DELETE` d'une sérologie ne réévalue toujours rien (KB-PLN-61).
5. Glossaire : termes P5 en `proposed`.

## Lot L2 (2026-11-09, ADR 0113)
La règle du risque infectieux lue par le placement (RG-PLN-012) change : seule la dernière sérologie conclusive (positive ou négative) d'un marqueur décide, un douteux ou un en cours plus récent n'efface plus un positif (KB-MED-106 CORRIGÉ). Aucun code de la Planification ne change (relais du langage publié) ; seul le double de test `RisquesInfectieuxDeTest` est aligné. Plus de patients sont placés en isolement ; la liste est donnée par la détection §75.1 de `verifications-production.md`. Validation médicale attendue ≤ 2026-12-15.
