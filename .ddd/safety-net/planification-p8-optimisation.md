---
status: characterized (filet vert sur le LEGACY, rien de commité)
date: 2026-10-28
sources: .ddd/tactical/planification.md (P20 à P24, B3), .ddd/migration/roadmap.md (§1 sexies, dette P8), ADR 0024, 0049 à 0061, docs/reference/regles-de-gestion/05-planning-placement.md, known-bugs.md KB-PLN-89 à 105
---

# Filet de caractérisation — planification-p8-optimisation

Refactoring sur place (ADR 0024). **Aucun seam, aucune modification de production** (`seamAllowed` vide). Seuls des tests, un harnais et des goldens sont ajoutés. Le solveur Timefold est RÉEL (aucun double) et rendu déterministe sans toucher au code (voir ci-dessous).

## Périmètre et points d'observation

| Entrée | Observation |
|---|---|
| `/api/v1/planning/optimisations` : lancer (POST), historique (GET), consulter (GET `/{id}`), arrêt (POST `/{id}/arret`), application (POST `/{id}/application`), suppression (DELETE), calendrier (GET `/{id}/calendrier`), impression (GET `/{id}/impression`) | MockMvc, contexte Spring complet, sécurité réelle + `RoleScopeFilter`, H2 `test` |
| `ReplanificationAutomatiqueScheduler.replanifier()` (cron 02:30) | appel direct de la méthode du job, chaîne asynchrone attendue par les bilans `REPLANIFICATION_NOCTURNE` |
| `OptimisationDonneesPort.charger` (lecture des données à optimiser), `DeplacementTemporairePort` (`enregistrer`, `entre`), `DeplacementTemporaire.creer` | appel direct des beans (contexte complet) |
| Réglages P1 (heures par vacation, temps plein, repos), centres en replanification | lus par les lancements et la nuit |

Effets observés : état SQL normalisé (`patients` colonnes de place et jours, `infirmier_affectation`, `infirmier_remplacement`, `deplacement_temporaire`, `planification_optimisation`), `notification_evenement`, diffusions temps réel (canal du courtier), `audit_log`, et les lectures P4 (présence de la semaine) et P6 (planning de la semaine).

Harnais : `backend/platform/src/test/java/com/hemodialyse/backend/characterization/planification/OptimisationCharacterizationSupport.java` (au-dessus de P6 `SemaineCharacterizationSupport`). Tests : `.../planification/optimisation/` — 4 classes, 39 scénarios. Goldens : `backend/platform/src/test/resources/characterization/planification/p8-optimisation/` (`lancement`, `application`, `nuit`, `donnees`).

Commande : `./mvnw -o -q -pl platform -am test -Dtest='com.hemodialyse.backend.characterization.planification.optimisation.*IntegrationTest' -Dsurefire.failIfNoSpecifiedTests=false` (`-Dcharacterization.approve=true` pour régénérer après revue ; ordre aléatoire : `-Dsurefire.runOrder=random '-Djunit.jupiter.testmethod.order.default=org.junit.jupiter.api.MethodOrderer$Random'`). Durée : environ 2 minutes (31 s pour le lancement, 70 s pour l'application, dont 35 cas d'empreinte, 7 s la nuit, 4 s les données).

## Déterminisme du solveur (sans seam)

- Durée au plancher de l'API (`dureeMaxSecondes = 2`) ; un problème vide (aucune entité) n'appelle pas le solveur (instantané) ; un problème non vide dure environ 2 s (limite sans amélioration `max(2 s, durée/3)`).
- Graine fixe (`20260926`) du code de production, mode `NO_ASSERT`, **jeux de données à optimum unique** : un seul générateur utile (petit décor : une salle, un créneau, un générateur) ou des générateurs inutilisables (`EN_PANNE`), un seul infirmier candidat, un seul remplaçant. Ne jamais comparer le choix du solveur entre générateurs équivalents (un essai avec quatre générateurs a donné une salle différente au COMPLET).
- Attente par interrogation de la base (`statut <> EN_COURS`, plafond 120 s) puis attente de la fin de TOUS les calculs avant chaque nettoyage (un calcul écrirait dans des tables vidées). Le score est écrit dans les goldens (`PATIENTS 0hard/0medium/-550soft`) : deterministe sur ces jeux.
- La nuit de 02:30 : calculs de 30 s au plus, mais les jeux sont vides (instantané) ou s'arrêtent d'eux-mêmes (« No doable selected move »).
- Dates : alias nommés `<S0.DIM>`, `<S1.LUN>`, `<S-2.LUN>`… (harnais P3) ; empreinte (qui contient les dates) masquée (`<EMPREINTE>`) ; aucune dépendance au jour de la semaine ni à minuit (attente à moins de deux minutes de minuit, hérité de P3) ni au jour du mois. Les horodatages sont masqués.

## Scénarios (39)

**Lancement, statut, résultat** (`LancementEtSuiviDesOptimisationsCharacterizationIntegrationTest`, 16) : 202 `EN_COURS` puis proposition (PATIENTS) ; cinq périmètres sur un patient en attente ; calcul vide ; défauts de l'API et semaine en cours ; réglages du centre lus ; deuxième lancement refusé / autre centre / possible après la fin ; deux lancements simultanés ; 18 validations de paramètres et deux jeux de limites acceptées ; consultations mal formées (500, pagination) ; arrêt anticipé ; historique paginé ; purge à 20 ; calendrier (défaut, mercredi, semaine hors proposition, sans calendrier, inconnu, autre centre) ; impression PDF et refus ; suppression (en cours, secrétaire, autre centre, calendrier purgé) ; garde de centre.

**Application** (`ApplicationDesPropositionsCharacterizationIntegrationTest`, 11) : placement (places, notification, diffusion, lectures P6 et P4 avant/après) ; double application ; exécutions non applicables (en cours, échec, sans résultat, inconnue) ; place incohérente (proposition truquée, rien écrit) ; roulement (affectation modifiée, supprimée, inactif intact) ; couverture (remplacements, présence P4, seconde application refusée) ; maintenance (déplacement temporaire, place habituelle inchangée, semaine P6 « temporaire », nouveau calcul vide) ; complet ; **35 changements du centre entre le calcul et l'application** (qui périme, qui non) ; audit ; concurrence application / fiche (invariants).

**Replanification de 02:30** (`ReplanificationDeNuitCharacterizationIntegrationTest`, 8) : calendrier du job (cron, zone déclarée et résolue) ; option désactivée ; trois calculs et bilan `RIEN` (réglages 3/35/2 reportés) ; proposition `GAIN` signalée, rien appliqué ; horizon de la couverture (5 cas) ; report (calcul en cours) ; réglages illisibles + autre centre ; deux passages et centre inactif.

**Données lues, autorisations, déplacements temporaires** (`DonneesEtAutorisationsDeLOptimisationCharacterizationIntegrationTest`, 4) : matrice 11 profils + anonyme × 9 requêtes ; lecture complète des données à optimiser (patients en attente / sortis / pédiatriques / cathéter / à choisir / à risque, indisponibilités GMAO, temporaires, profils, autre centre) ; un déplacement temporaire par patient et par date (remplacement, borne incluse, ordre, centre) ; place complète exigée.

## Stabilité

4 exécutions complètes de la suite (39 scénarios) sur le legacy : 2 en ordre par défaut, 2 en ordre aléatoire (classes et méthodes) : 0 échec, 0 flaky ; 1 min 53 s à 2 min par exécution (solveur compris). Un premier essai a révélé une collision de noms de goldens, corrigée avant livraison.

## Couverture et mutation

Pas de JaCoCo ni de PIT dans la chaîne Maven hors ligne (comme P6/P7) : aucune mesure de branches ni de mutants ; et la mutation sur un solveur borné par le temps (2 s par calcul) serait impraticable. Couverture raisonnée : toutes les routes du contrôleur ; `OptimisationPlanningService` (lancement, refus, arrêt, suppression, calendrier, consultation, historique, purge) ; `OptimisationApplicationService` (quatre périmètres, empreinte, déjà appliquée, non applicable, introuvable, place incohérente, notification) ; `ReplanificationAutomatiqueService` (enchaînement, bilan RIEN / PROPOSITIONS / ECHEC / REPORTEE, horizon de couverture, signalement GAIN) ; les quatre adaptateurs JDBC ; les mappers et fournisseurs de contraintes des quatre modèles sur des cas minuscules.

## Ce qui n'est PAS verrouillé (limites)

- **Les contraintes du solveur** : un jeu à optimum unique prouve que le solveur place / affecte / déplace, pas son arbitrage entre solutions équivalentes ni le détail des poids (`PoidsOptimisation`), la stabilité, l'objectif `ECONOMIE`, le repos hebdomadaire, le quota d'heures, les compétences (`ExigenceCompetence`), les transporteurs, les schémas de jours (`SchemasJoursService`, patient « à choisir » : lu mais jamais résolu), le créneau préféré, le ratio de poste en série (`PosteSerie`), l'isolement. Ces fichiers (`SchemaJours`, `SchemasJoursService`, `ExigenceCompetence`, `PosteSerie`) ne sont PAS dans `covered.json`.
- Le comportement du solveur pour plusieurs patients / plusieurs générateurs équivalents et les temps de calcul réels (30 s par phase la nuit) : non mesurés.
- `MotifProposition.SOUS_EFFECTIF` et `MAINTENANCE` (signalement de nuit) : seul `GAIN` est observé ; le fuseau de « aujourd'hui » (UTC) n'est pas observable autrement que par `<S0.DIM>`.
- `OptimisationPlanningRecovery` (reprise au démarrage des calculs orphelins) : couverte par son test unitaire existant, pas par ce filet ; l'arrêt d'une exécution `EN_COURS` orpheline (inconnue du moteur) non observé.
- Le verrou en mémoire du lancement avec DEUX instances et la concurrence PostgreSQL (verrous centre -> fiches, ADR 0051) : H2 seulement, mono-JVM ; la concurrence application / fiche vérifie des invariants (jamais 500, tout ou rien), pas un ordre ; application vs roulement P3 non testée.
- Les règles `VerificationDeplacementsService` autres que `GENERATEUR_INCONNU` ; les propositions truquées à la main ne sont pas une entrée réaliste.
- Le contenu du PDF d'impression (seuls statut, type, en-tête et `%PDF`).
- Le frontend (`planning-optimisation-api.service.ts`) n'est pas exercé.

## Lecteurs et écrivains hors module

- Table `deplacement_temporaire` : écrite par `DeplacementTemporaireJdbcAdapter` (application d'une maintenance) ; lue par `OptimisationDonneesJdbcAdapter`, `PlanningDonneesJdbcAdapter` (planning P6), `SeancePlaceJdbcAdapter` (Séance), `contexts/planification/.../legacyoptimisation/DeplacementsTemporairesJdbc` (ACL P6).
- Table `planification_optimisation` et `planification_calendrier_case` : seulement `OptimisationRunJdbcAdapter` et `CalendrierPropositionJdbcAdapter` (+ `PlanningOptimiseReportService` par le service).
- L'application écrit : `patients` (via `PlacementPatientPort`), `infirmier_affectation` (via les dépôts du P3), `infirmier_remplacement`, `deplacement_temporaire`, `planification_optimisation` ; notifications `SEANCES_DEPLACEES`, `OPTIMISATION_PROPOSITION`, `REPLANIFICATION_NOCTURNE` ; lit `gmao_interventions` / `gmao_equipements`, `abords_vasculaires`, `serologies_patient`, `planning_parametres`, `center_holiday`, `center_closure_day`, `centers`.
- Frontend : `frontend/src/app/core/api/planning-optimisation-api.service.ts`.

## Seams

Aucun nécessaire. Le seam du cron (zone de 02:30) a déjà été appliqué à la slice P7 (`seams.md`, 2026-10-28).
