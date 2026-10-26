---
status: characterized
date: 2026-10-26
sources: .ddd/tactical/planification.md (P1), docs/reference/regles-de-gestion/05-planning-placement.md (RG-PLN-001..006, 093, 097, 098), ADR 0049 à 0054
---

# Filet de caractérisation — planification-p1-parametrage

Refactoring sur place (ADR 0024) : pas de Strangler. Legacy figé ; **aucun seam, aucune modification de production**.

## Périmètre (9 routes)
| Route | Rôles (méthode) | Tables |
|---|---|---|
| `GET\|PUT /api/v1/planning/parametres` | lecture ADMIN, SECRETAIRE, MEDECIN, INFIRMIER ; écriture ADMIN | `planning_parametres`, `salle.isolement` (écrite par le PUT) |
| `GET\|PUT /api/v1/planning/preferences/reglages` | classe ADMIN, SECRETAIRE ; PUT ADMIN | `planification_reglages` |
| `GET /preferences/patients`, `PUT /preferences/patients/{id}` | ADMIN, SECRETAIRE | `planning_preference_patient` (lit `patients`, `position_creneau`) |
| `GET /preferences/infirmiers`, `PUT /preferences/infirmiers/{id}` | ADMIN, SECRETAIRE | `infirmier_profil_planning` (lit `infirmier`) |

Fichiers protégés : voir `covered.json` (20 fichiers : 2 contrôleurs, 2 services, 4 adaptateurs/entité/repository, 12 modèles et ports de `bc-seance`).

## Point d'observation
HTTP (MockMvc, contexte Spring complet, sécurité réelle + `RoleScopeFilter`, H2 `test`) + état SQL normalisé des 4 tables et de `salle`. Aucun mock. Support réutilisable P2–P9 :
`backend/platform/src/test/java/com/hemodialyse/backend/characterization/planification/PlanificationCharacterizationSupport.java` (11 profils d'accès, 2 centres, fixtures salle/créneau/patient/infirmier avec alias nommés, rendu normalisé UUID/horodatage/clés triées, goldens JSON via `gmao.plans.GoldenMaster`).
Tests : `.../characterization/planification/parametrage/` (5 classes, 50 scénarios) ; goldens : `backend/platform/src/test/resources/characterization/planification/p1-parametrage/{parametres,reglages,preferences-patients,profils-infirmiers,autorisations}/` (≈ 225 fichiers).
Commande : `./mvnw -o -q -pl platform -am test -Dtest='com.hemodialyse.backend.characterization.planification.parametrage.*IntegrationTest' -Dsurefire.failIfNoSpecifiedTests=false` (`-Dcharacterization.approve=true` pour régénérer après revue).

## Scénarios (par comportement)
- **Paramètres** : défauts sans ligne (7 jours, ratio 4, 3 par poste, rien créé en lecture) ; salles d'isolement lues depuis `salle.isolement` triées ; ligne ancienne aux colonnes nulles ou sans jours ; création + remplacement des salles (reset `NON` puis `OUI`, limité au centre) ; défauts des champs absents ; idempotence et dernière écriture gagnante ; indépendance des centres ; bornes ratio 1-20 et poste 1-10 (0, 1, 10, 11, négatif) ; jours requis ; salle étrangère/inconnue sans écriture ; ordre des validations (jours, ratio, poste, salles) ; corps illisibles (500) ; décimaux tronqués ; champ inconnu ignoré ; garde de centre (403, centre explicite, centre illisible, SUPERADMIN).
- **Réglages** : défauts ; création à la volée puis mise à jour (1 ligne) ; indépendance des centres ; bornes 1-12, 10-60, 0-6 ; corps partiel/illisible ; matrice 11 profils + anonyme (GET/PUT) ; garde de centre.
- **Préférences patients** : liste (non sortis, tri nom/prénom, préférence par défaut, centre), pagination ; PUT création/remplacement/idempotence/effacement ; séances 1-7 et jours à choisir ; créneau étranger avant patient ; patient introuvable ; patient sorti accepté ; garde de centre.
- **Profils infirmiers** : liste (actifs, tri, défaut 100 %), compétences stockées atypiques ; PUT création/remplacement/compétences nulles ; taux 10-100 ; compétences inconnues ; introuvable ; inactif accepté ; garde de centre.
- **Autorisations** : matrice 9 routes x (11 profils + anonyme) = statut/code + « a écrit ? » ; formes de refus.

## Couverture et mutation
- Stabilité : 3 exécutions (dont 2 en ordre aléatoire des méthodes) vertes, 0 flaky, aucun `*.received.json` résiduel. Aucune date : pas de dépendance au jour ni à minuit.
- PIT 1.30.0 (`backend/scripts/pit-p1-parametrage.sh`, classpath via `test-compile dependency:build-classpath` dans le même appel Maven) : 126 mutants, 106 tués (84 %), 11 sans couverture, force des tests 92 %, lignes 91 % (203/224) sur les 12 classes cibles. Hors périmètre exercé : les lectures de l'optimisation (`PreferencePatientJdbcAdapter.preferences`, `ProfilInfirmierJdbcAdapter.profils`, `ReglagesOptimisationJdbcAdapter.centresEnReplanificationAuto`, `ProfilInfirmier.quotaHeures/parDefaut`, `PreferencePatient.aucune`) = P7/P8. Survivants restants : gardes `null` équivalentes (`existe == null`, `total == null`, prénom null, `patientId == null` inatteignable par HTTP).

## Seams
Aucun demandé, aucun appliqué (`seamAllowed` vide).

## Limites (NON verrouillé)
- Consommateurs des paramètres : présence (`PresenceDonneesJdbcAdapter` lit le ratio), capacité de la direction, optimisation (lisent préférences, profils, réglages, `centresEnReplanificationAuto`) : leur effet est verrouillé par leurs propres slices (P2–P9) ; P1 ne vérifie que la relecture via les GET.
- Audit : `AuditRequestFilter` absent de la chaîne MockMvc (voir KB-PLN-11) ; aucune diffusion temps réel ni notification n'existe sur ces routes.
- Concurrence : écritures séquentielles seulement (dernière écriture gagnante) ; pas de test multithread (non déterministe, KB-PLN-10).
- Messages d'erreur 500 non verrouillés (`<message technique…>`) ; migrations Flyway/PostgreSQL non rejouées (H2 uniquement ; script de rejeu PG16 non créé pour P1).
- `RoleScopeFilter` n'est protégé que pour ces 9 routes (le fichier n'est pas dans `covered`).

## Bugs connus
KB-PLN-01 à KB-PLN-11 dans `.ddd/safety-net/known-bugs.md` (500 sur corps illisibles et réglages hors bornes, écart RG-PLN-005 / `RoleScopeFilter`, SUPERADMIN refusé, décimaux tronqués, PUT à remplacement total, cibles sorties/inactives acceptées, pagination négative en 500, ordre des compétences non déterministe, aucun verrou, audit non couvert).
