---
status: proposed
date: 2026-10-27
sources: [commit e725e7a1 (lot P7, commité pendant la revue) vs 0e2ef302, ADR 0061, fiche planification-p7-liberation-places, KB-PLN-78..88]
périmètre: slice planification-p7-liberation-places (risque élevé), lecture seule, aucun build ni test lancé
---

# Revue indépendante — P7 libération des places

Contrôles automatiques : `check-arch.ts` 0 violation (0 en baseline, 0 nouvelle). Le lot a été commité pendant la revue (`e725e7a1`) ; la revue compare `0e2ef302..e725e7a1`.

## Vérifications faites (OK)

- **Archives `_ecarts-legacy`** : 23/23 **identiques octet pour octet** à `git show 0e2ef302:<…>.approved.json` (sha1 de HEAD, de l'index et du disque comparés).
- **Écarts des 23 goldens** (diff legacy → nouveau) : seuls les écarts décidés changent. 19 `*_patients` : `en_sommeil` passe de `"false"` (legacy) à `null`. Le nouveau code ne réécrit donc plus la fiche entière (KB-PLN-84). Les autres écarts : zone du cron, centre inactif, transaction par patient (`passages_*`), verrou (`verrou_de_placement` 0→1), `date_effet` J-1→J, pas de second mouvement. Aucun autre champ ne change.
- **Effet de `en_sommeil` NULL** : sans effet. Tous les lecteurs ramènent NULL à faux : `SejoursDesPatientsJdbc.java:51`, `SeancePlanningJdbcAdapter.java:46`, `PatientMapper.java:32`, `SeanceRestController.java:895`.
- **Legacy édité** : chaque fichier `src/main` legacy modifié figure dans `covered.json`, y compris `PatientRepositoryPort`, `PatientRepositoryAdapter` et `PatientJpaRepository`. `seamAllowed` est vide (seam fermé). Plus aucune référence à `findWithAssignment`, `LiberationPlacesService` ou `placeAReprendre` dans le code (un seul reste : `docs/client/documentation-fonctionnelle.html:5272`, à régénérer).
- **Idempotence et multi-instance** : le verrou du centre et la relecture sous `FOR UPDATE` (`LibererUnePlace.java:47-50`) empêchent une double libération. L'alerte a une clé déterministe UUID v3 ; un doublon concurrent est absorbé (`NotificationsDeLaLiberationLegacy.java:39-50`). Les caches sont vidés après validation de la transaction (`CachesDesPatientsLegacy.java:28-35`).
- **Concurrence** : `lock_timeout` est posé au premier passage, même dans une transaction rejointe (`UniteDeTravailSpring.java:43-45`). L'interblocage fiche/nuit est borné : PostgreSQL le détecte (40P01) ou le délai expire (55P03). Côté fiche, le refus devient un 409 (`RefusDeVerrou.java:23-31`) ; côté nuit, c'est un incident par patient (`LibererLesPlacesEchues.java:82-84`).
- **Fichiers** : aucun BOM ni mojibake introduit. Les 2 occurrences de mojibake de `known-bugs.md` existaient déjà en 0e2ef302. Les diffs de roadmap et des docs 15/16 sont surtout un réalignement des tableaux (`git diff -w` : ±12 lignes).
- **Recette** : `verifications-production.md` P7 ne contient que des SELECT. La requête 1 couvre `actif IS NOT TRUE` (NULL compris).

## Constats

| Sév. | Domaine | Constat | Preuve | Correction recommandée |
|---|---|---|---|---|
| major | tests | **Scénario `fuseau` dépendant de l'heure (violation)**. Il n'appelle pas `nommerLesJours()`, donc n'est pas ignoré entre 23:00 et 24:00 UTC. Il fige les dates sur `LocalDate.now(UTC)`. Or le nouveau job juge au jour du centre (Algiers = UTC+1). Dans cette fenêtre, `TRANSFERE_UTC` et `DECEDE_UTC+1` seraient libérés et le golden `fuseau_patients_nouveau_code` échouerait (1 h par jour). Le nom du test (« …fuseau_utc ») et le commentaire de la ligne 138 sont devenus faux. | `CentresEtEchecsDeLaLiberationCharacterizationIntegrationTest.java:136-149` ; `RoulementCharacterizationSupport.java:255-258` (garde non appelée) ; `HorlogeDesCentresLegacy.java` (`aujourdhui`) | Appeler `aujourdhui()`/`nommerLesJours()` (garde 23-24 UTC), ou mieux une horloge fixe ; renommer le scénario « jour du centre ». |
| major | legacy/argent | **Définition de « centre actif » divergente (violation de cohérence)**. Les Absences considèrent `actif` NULL comme actif (`COALESCE(actif, TRUE)`). La libération le considère comme inactif (`actif = TRUE`), et la colonne `centers.actif` accepte NULL. Dans un centre à `actif` NULL, les sortis gardent leurs jours de dialyse : ils restent « attendus » chaque nuit à 02:30, ce qui peut créer des absences détectées, donc valorisées. Le legacy libérait tous les centres. | `CentresActifsJdbc.java:24` (absences) ; `CentresActifsLegacy.java:22` (planification) ; `V1__baseline_schema.sql` (table centers, `actif boolean` sans NOT NULL) | Aligner sur `COALESCE(actif, TRUE)`, ou décision écrite. La requête 1 de recette doit être exécutée **avant** le déploiement. |
| minor | legacy/concurrence | **Écrasement inverse non couvert**. Si une fiche est chargée avant la validation de la nuit puis écrite après, la sauvegarde JPA écrit toutes les colonnes et remet l'ancienne place (`PatientMapper.java:117-125`). L'échéance étant jugée à la veille, la place reste tenue un jour de plus : le patient est de nouveau attendu, avec un risque d'absence. Le dédoublonnage évite seulement le second mouvement. Le test PG16 ne couvre que l'autre sens (la fiche tient la ligne d'abord). Fenêtre étroite, à 04:00. | `LiberationJdbcPostgresContractIT.java:71-99` ; `LiberationALEnregistrementDeLaFiche.java:26-35` | Consigner dans KB-PLN-84 comme risque résiduel, ou relire la fiche sous verrou dans la transaction de la fiche. |
| minor | tests | **Harnais (b)** : la remise à zéro du centre A entre les passages est **légitime** (sans elle, la contrainte d'échec ne peut plus porter sur le patient suivant), et les compteurs par passage restent observés. Mais `apres_les_echecs_patients` photographie désormais l'état remis par le harnais, plus la sortie du système. `centresAvecPlaces` est calculé par une requête du test, donc s'auto-vérifie. | `CentresEtEchecs…:103-111` ; `LiberationCharacterizationSupport.java` (`centresAvecPlaces`) ; `NuitDeLiberation…:179-184` | Photographier l'état **avant** la remise à zéro ; préciser dans la fiche que `centresAvecPlaces` n'observe plus le système. |
| minor | legacy | **Dates du même enregistrement incohérentes** : le mouvement de changement d'état est daté du jour UTC, `PLACE_LIBEREE` du jour du centre. Entre 00:00 et 01:00 à Alger, `TRANSFERT` est daté J-1 et `PLACE_LIBEREE` J. | `PatientApplicationService.java:105` vs `LibererLaPlaceDeLaFiche.java:33` | Consigner (KB) ; à aligner lors de l'extraction de Patient. |
| minor | archi | La couche `application` legacy dépend d'une classe `infrastructure.acl` (nom pleinement qualifié). `check-arch` ne le voit pas (platform hors règles). | `PatientApplicationService.java:53,61` | Accepté comme seam, mais à tracer ; ou introduire un port côté Patient. |
| minor | legacy | Le dédoublonnage (« dernier mouvement = même état + même place ») efface la trace d'une **vraie** seconde libération quand un sorti est replacé sur la même place, ce que P5 accepte encore (KB-PLN-83). | `MouvementsDesPatientsJdbc.java:26-37` | Lier ce cas à la décision KB-PLN-83 (422). |
| minor | stratégique | **ADR 0061 encore `proposed`** alors que le code est commité. L'écart KB-PLN-84 (écriture limitée aux colonnes de place) ne figure pas dans la liste des décisions utilisateur reçue (§5 de l'ADR). | `0061-…md:2,30` | Faire approuver l'ADR (point 5 compris). |
| minor | tests | L'ordre de nuit repose sur **deux clés** (`app.absences.fuseau-par-defaut` / `app.planification.fuseau-par-defaut`) ; le test ne compare que les valeurs par défaut. | `ControleDeNuitDesAbsences.java:36` ; `LiberationDesPlacesDe0400.java:25` ; `OrdreDeNuitDeLaLiberationTest` | Une seule clé, ou un test sur les valeurs résolues de l'environnement. |
| nit | archi | L'échec de lecture d'un centre (horloge, liste) est seulement journalisé, sans alerte ADMIN. | `LibererLesPlacesEchues.java:73-75` | Signaler aussi. |
| nit | tactique | Sentinelle `LocalDate.MIN` pour « sorti sans date » ; `etat` en `String`. | `PlaceTenue.java:23,27` | VO `DernierJourDePlace` (suggestion). |
| nit | legacy | Les requêtes de recette utilisent `current_date` (fuseau du serveur SQL), pas le jour du centre. | `verifications-production.md` P7, requêtes 1-2 | Mentionner le décalage possible. |

Note (d) : je n'ai trouvé **aucun** chemin qui prenne « ligne patient puis verrou du centre » en dehors de la fiche (exception de l'ADR) et de la sérologie (ADR 0059 §10). L'hypothèse UserRestController et fiches infirmier n'est pas vérifiée : constat non retenu.

## Verdict : **pass-with-actions** (0 blocker, 2 major, 7 minor, 3 nit)

## Suivi des corrections (2026-10-28, voir la fiche de slice « Corrections de la revue P7 »)

| Constat | État |
|---|---|
| major scénario `fuseau` | corrigé (garde 23-24 UTC, renommé) |
| major centre actif | corrigé (`COALESCE(actif, TRUE)`, test, SELECT de recette ; requête 1 = prérequis bloquant) |
| minor écrasement inverse | traité par `PLACEMENT_PATIENT_SORTI` + test H2/PG16 ; risque résiduel consigné (KB-PLN-84) |
| minor `apres_les_echecs_patients` | corrigé (3 goldens avant remise à zéro) |
| minor dates UTC/centre | corrigé (mouvement de changement d'état au jour du centre) |
| minor application → ACL | accepté, tracé (`seams.md`) |
| minor dédoublonnage / replacement | levé (un sorti ne peut plus être replacé) |
| minor deux clés de fuseau | corrigé (clé unique, test sur valeurs résolues) |
| minor ADR `proposed` | maintenu ; KB-PLN-84 en décision à valider |
| nits | non traités (alerte d'échec de lecture d'un centre, VO `DernierJourDePlace`) ; `current_date` signalé dans la recette |

Top 3 actions :
1. Rendre le scénario `fuseau` indépendant de l'heure (garde 23-24 UTC ou horloge fixe) et le renommer.
2. Aligner la définition de « centre actif » (NULL) entre Absences et Planification ; exécuter la requête 1 de recette avant le déploiement.
3. Faire approuver l'ADR 0061 (dont KB-PLN-84) et consigner les risques résiduels : écrasement inverse fiche/nuit, dates UTC/centre, dédoublonnage après replacement.
