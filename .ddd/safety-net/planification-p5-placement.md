---
status: characterized (filet vert sur le LEGACY, rien de commité)
date: 2026-10-27
sources: .ddd/tactical/planification.md (P5, B1/B2/B8/B9/B10/B11), .ddd/migration/roadmap.md (§1 sexies), ADR 0049 à 0058, docs/reference/regles-de-gestion/05-planning-placement.md, known-bugs.md KB-PLN-52 à 64
---

# Filet de caractérisation — planification-p5-placement

Refactoring sur place (ADR 0024) : pas de Strangler. **Aucun seam, aucune modification de production** (`seamAllowed` reste vide).
Il n'existe **aucune route HTTP dédiée** de placement, de déplacement, de changement de jours, de désaffectation ni de déplacement
temporaire : ces opérations passent par `POST`/`PUT /api/v1/patients` (contrôle `PlacementPatientService.verifier`, écriture par la fiche
legacy) ; le déplacement temporaire est écrit par l'application d'une optimisation (P8) et lu par le planning de la semaine (P6) : **hors de ce filet**.

## Périmètre

| Point d'entrée | Rôles observés | Lit / écrit |
|---|---|---|
| `GET /api/v1/planning/affectations` (propositions, grille, alertes) | 11 profils + anonyme | lecture (`patients`, `salle`, `position_creneau`, `gmao_equipements`, sérologies, fermetures, `planning_parametres`) |
| `GET /api/v1/planning/salles` | idem | lecture (`salle`, `gmao_equipements`) |
| `POST`/`PUT /api/v1/patients` (placement) | idem | colonnes de place de `patients`, `attestation_droit`, `mouvement_patient`, diffusion `PATIENT_*`, audit |
| `POST`/`PUT`/`DELETE /api/v1/patients/{id}/serologies` | idem | `serologies_patient` + réaffectation en isolement, `notification_evenement`, diffusions |
| `GmaoEvenementsPlanificationListener` (`EquipementMisHorsService`, `EquipementReforme`) | via routes GMAO réelles | `notification_evenement` `GENERATEUR_INDISPONIBLE` |

## Point d'observation

HTTP (MockMvc, contexte Spring complet, sécurité réelle + `RoleScopeFilter`, H2 `test`), état SQL des colonnes de place, notifications durables,
diffusions du courtier, audit. Aucun mock. Harnais : `…/characterization/planification/PlacementCharacterizationSupport.java` (au-dessus de P3, P2, P1).
Commande : `./mvnw -o -q -pl platform -am test -Dtest='com.hemodialyse.backend.characterization.planification.placement.*IntegrationTest' -Dsurefire.failIfNoSpecifiedTests=false`
(`-Dcharacterization.approve=true` pour régénérer après revue ; `-Dsurefire.runOrder=random`). Goldens : `backend/platform/src/test/resources/characterization/planification/p5-placement/` (123 fichiers).

## Scénarios (76, 6 classes)

| Classe | Nb | Verrouille |
|---|---|---|
| PropositionsDePlace | 25 | forme complète, défauts (3 séances, limite 12), 1 à 7 séances, bornes (400/500), jours imposés, limite 1-30, deux par salle et créneau, créneau/salle souhaités (score 0-100, raisons), générateur occupé, salle pleine, sans générateur, générateur d'une autre salle/hors service, générateurs écartés, patient libéré (`patientId`), patients sortis, autre centre, risque forcé/déduit (4 marqueurs, dernière sérologie), aucune salle d'isolement, pas de mélange risque/sans risque, jours d'ouverture et grille fermée, paramètres absents/vides, fermetures datées (horizon 90 j), garde de centre |
| PlacementSurLaFiche | 22 | création avec/sans placement, 8 règles RG-PLN-050 + premier manquement + manquements multiples, jour fermé, placement inchangé jamais contrôlé, déplacement/changement de jours/désaffectation, places partagées et libérées, 7 jours, isolement à l'enregistrement, requêtes invalides, autre centre, absence de ligne de paramètres, course sur la même place, effets sur grille/propositions, refus sans effet, diffusions, audit |
| ReaffectationEnIsolement | 13 | déplacement vers l'isolement (jours/créneau conservés), déjà conforme/non placé/sans jour, résultat négatif et marqueur hors risque, 4 marqueurs, créneau changé ou impossible, générateur d'isolement pris par un sain, sans salle d'isolement, placement hérité incomplet, modification/suppression de sérologie, partage des places, périmètre, audit/diffusions |
| SallesEtCapacite | 7 | forme complète, tri par code, capacité/places restantes/dépassement, générateurs comptés, pagination, isolation de centre, statut courant sans cache |
| AlerteGenerateurIndisponible | 8 | hors service + patients cités sans déplacement, 5 patients au plus, patients sortis, sans patient, réforme, cas sans alerte, effets sur propositions/fiche, rejeu |
| AutorisationsDuPlacement | 1 | matrice 4 routes x 11 profils + anonyme |

## Non-déterminisme

Dates par alias (`<J+3>`, jamais mélangés avec la famille `<S1.LUN>`), jour de semaine masqué (`<JOUR_DE_LA_DATE>`) quand il dérive d'une date relative,
attente de minuit (UTC et Alger) et scénarios ignorés entre 23:00 et 24:00 UTC (hérité de P3). Sérologies datées à des dates fixes jamais écrites dans un golden.
Livraison asynchrone des événements GMAO : attente que `event_publication` soit vide. Code patient aléatoire (`PAT-xxxxxxxx`) masqué.
Course de placement : seules des bornes sont figées (au moins un accepté, aucun autre statut que 200/422, occupants cohérents avec les réponses) : le résultat de la course n'est pas déterministe.

## Stabilité

Trois exécutions de la suite des 76 scénarios : ordre par défaut, puis deux ordres aléatoires (`-Dsurefire.runOrder=random`) : 0 échec, 0 flaky.

## Couverture et mutation

Pas de JaCoCo dans la chaîne Maven hors ligne (aucune mesure de branches). PIT non lancé et pas de script `pit-p5-*.sh` : le domaine
(`PlanificationAffectationService`, bc-seance) est exercé par le filet HTTP (lent, non adapté à PIT) et ses tests unitaires historiques ;
à reprendre après la construction sur le module `contexts/planification`. Couverture raisonnée par chemin : les huit violations, l'index d'occupation
(générateur/salle sans générateur/risque), les cinq issues de réaffectation (AUCUN_RISQUE, NON_PLACE, DEJA_CONFORME, DEPLACE, A_REPLANIFIER).
Les 19 fichiers ajoutés à `covered.json` sont exercés par au moins un scénario ; `PatientRestController.java` et `PlacementPatientJdbcAdapter.java` ne le sont
que **partiellement** (POST/PUT de fiche ; `deplacer` mais pas `definirJours`, appelé par l'optimisation P8) : toute modification hors de ces routes exige d'abord son filet.

## Limites (NON verrouillé)

- Déplacement temporaire (un par patient et par date), application d'optimisation, planning de la semaine (P6), libération des places (P7) : filets P6/P7/P8.
- Lecteurs hors module : Séance (`SeancePlaceJdbcAdapter`), Absences (`PatientsAttendusJdbc`), Direction (`DirectionCapaciteQueryService`) : non rejoués ici
  (filets Absences A3, `DirectionCapaciteIntegrationTest`) ; effet observé seulement sur la grille, les propositions et la vue des salles.
- Échec de la réaffectation après enregistrement de la sérologie (B10) : non provocable sans seam.
- Règle `SALLE_CAPACITE_DEPASSEE` à l'affectation d'un générateur : filet GMAO G3 ; état constaté d'une intervention (autre source d'événements) : G4.
- Fuseau : fenêtre 23:00-24:00 UTC non rejouée ; PostgreSQL (H2 seulement) ; vraie concurrence (verrous, `lock_timeout`) ; volumétrie (~300 places).
- Toute autre route de `PatientRestController` / `Patient.java` (seam B2/Q1 à venir).

## Questions de décision pour l'utilisateur

1. Verrou : créer la ligne `planning_parametres` si absente ou verrouiller le centre autrement ; 409 + `lock_timeout` 5 s (KB-PLN-53).
2. Codes HTTP : 422 `PLACEMENT_*` conservé ? paramètres illisibles 500 → 400 (KB-PLN-56) ? garde de centre et rôles de l'écriture de fiche (KB-PLN-52).
3. Fuseau du centre pour l'horizon, les patients sortis et l'alerte (KB-PLN-58).
4. Seam Patient (`PatientRestController.verifierPlacement`, `Patient.libererPlacement`) : accord requis avant toute édition ; `seamAllowed` vide à ce jour.
5. Patient sain en salle d'isolement : refuser (KB-PLN-55) ? Retour en salle ordinaire après négativation (KB-PLN-61) ?
6. Sérologie d'un patient hors centre ; transaction commune sérologie + réaffectation ; alerte `ISOLEMENT_IMPOSSIBLE` dédoublonnée (KB-PLN-59, 60).
7. GMAO : port `IndisponibilitesDesGenerateurs` et événements `gmao.v1` doivent précéder P5/P8 ; alerte sans déplacement automatique confirmée (KB-PLN-63).
