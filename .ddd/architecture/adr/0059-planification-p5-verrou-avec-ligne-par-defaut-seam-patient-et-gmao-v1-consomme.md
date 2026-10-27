---
status: proposed
date: 2026-10-27
decision-makers: [utilisateur (décisions du 2026-10-27, communiquées pour la slice P5), architecte]
context-scope: planification
related: ADR 0024, 0049, 0052, 0053, 0054, 0055 ; `.ddd/tactical/planification.md` P5 ; KB-PLN-52 à 64 ; `.ddd/safety-net/seams.md`
---

# 0059 — Planification P5 : verrou avec ligne par défaut, seams Patient et Sérologie, `gmao.v1` consommé, codes `PLACEMENT_*`

## Contexte et problème

La slice P5 déplace dans `contexts/planification` le placement des patients : propositions de place, contrôle et écriture d'un
placement sur la fiche, réaffectation en isolement après une sérologie, capacité des salles, alerte « générateur indisponible ».
Le filet (76 scénarios, KB-PLN-52 à 64) a montré des écritures de place sans exclusion mutuelle, sans garde de centre, des 500
sur entrées illisibles et une alerte rejouée à chaque événement.

## Décisions

1. **Verrou du centre avec création de la ligne par défaut** (variante inverse de l'ADR 0055, qui reste valable pour le seul
   paramétrage). `VerrouDesPlacesJdbc` : `SELECT … FOR UPDATE` de la ligne `planning_parametres` du centre ; si elle manque, elle
   est **créée avec ses valeurs par défaut** puis verrouillée. La perdante d'une création simultanée est refusée en 409
   `PLANIFICATION_ECRITURE_CONCURRENTE`. Délai 5 s. Effet observable : un centre jamais paramétré possède une ligne après son
   premier contrôle de placement (KB-PLN-53). Ordre des verrous inchangé (centre d'abord).
2. **Une transaction pour le contrôle ET l'écriture de la fiche** (seam Patient). `PatientRestController.verifierPlacement` appelle
   l'ACL `PlacementDeLaFiche` (port publié `PlacementDesPatients`) : garde de centre (403), jours traduits, contrôle + écriture dans
   une même transaction, rôles `ADMIN`/`SECRETAIRE`/`INFIRMIER` seulement (KB-PLN-52). La fiche écrit encore elle-même les colonnes
   de place (ADR 0052) ; `Patient.libererPlacement` relève de P7 et n'est pas touché.
3. **Seam Sérologie** (extension **ratifiée par l'utilisateur par écrit le 2026-10-27**, y compris `AuditActionResolver` / libellés `SEROLOGIE_*`). `SerologieRestController` appelle l'ACL `ReaffectationEnIsolementDeLaSerologie` : la
   sérologie et la réaffectation (port publié `ReaffectationEnIsolement`) réussissent ou échouent ensemble ; sérologie d'un patient
   hors centre refusée (422 `PLACEMENT_PATIENT_INTROUVABLE`) ; `ISOLEMENT_IMPOSSIBLE` émise une fois par patient et par jour du
   centre ; un patient redevenu sans risque encore en salle d'isolement reçoit une **proposition** de retour en salle ordinaire
   (alerte `RETOUR_SALLE_ORDINAIRE_PROPOSE`), jamais un déplacement. L'audit utilise des libellés métier (`SEROLOGIE_ENREGISTREE`,
   `SEROLOGIE_MODIFIEE`, `SEROLOGIE_SUPPRIMEE`, KB-PLN-62). L'ACL vide le cache JPA (`flush`) avant l'appel, car la Planification
   lit les sérologies en SQL.
4. **`gmao.v1` consommé.** `GenerateurDevenuIndisponibleEcouteur` (`@ApplicationModuleListener`) remplace l'écouteur de la
   plateforme. Alerte **idempotente** par générateur et par jour du centre (identifiant déterministe, UUID v3) : une panne puis une
   réforme le même jour = 1 alerte (KB-PLN-63). L'exception remonte (l'événement est rejoué par le registre) au lieu d'être avalée.
   Aucun déplacement automatique.
5. **Codes `PLACEMENT_*`** (422) conservés dans leur ordre (KB-PLN-54), avec deux codes **nouveaux** : `PLACEMENT_ISOLEMENT_RESERVE`
   (patient sans risque refusé en salle d'isolement, KB-PLN-55) et `PLACEMENT_PATIENT_INTROUVABLE` (patient inconnu ou d'un autre
   centre, remplace le 400). Entrées illisibles : 400 `BAD_REQUEST` (KB-PLN-56) ; `GET /planning/salles` : `size` plafonnée à 100,
   négatifs refusés (KB-PLN-64).
6. **Nom affiché « nom prénom »** dans toutes les alertes de placement (écart accepté ; le legacy mélangeait « prénom nom » et
   « nom prénom »).
7. **Fuseau du centre** (ADR 0054) pour « aujourd'hui » : fermetures, patients sortis, alertes (KB-PLN-58).

8. **Délai de verrou posé quelle que soit l'origine de la transaction** (revue P5, constat 1). Les ACL de la fiche et de la sérologie
   (et le `@Transactional` de l'optimisation) ouvrent la transaction ; l'unité de travail du contexte la rejoignait sans poser
   `SET LOCAL lock_timeout`, donc, sous PostgreSQL, le `FOR UPDATE` attendait sans limite. `UniteDeTravailSpring` pose désormais le
   délai (5 s) au PREMIER passage de la transaction par une unité de travail (marqueur lié à la transaction, retiré à sa fin), puis
   409 `PLANIFICATION_ECRITURE_CONCURRENTE`. Prouvé sur PG16 par 3 tests de contrat (fiche, sérologie, optimisation) qui échouent avec
   l'ancienne logique et passent avec la nouvelle (`planification-p5-pg16-rejeu.sh`).
9. **Seam Optimisation** (autorisé par l'utilisateur le 2026-10-27, fermé ensuite). `OptimisationApplicationService.appliquer` écrivait
   les places (`deplacer` / `definirJours`) SANS verrou : une fiche enregistrée en même temps pouvait doubler un générateur. Elle
   appelle désormais le port publié `VerrouDesPlacesDuCentre.verrouiller(centre)` (périmètres qui placent des patients), AVANT de relire
   l'état (empreinte) ; ordre global centre -> fiches des infirmiers. Test de course fiche contre optimisation sur H2 et PG16 : la
   fiche attend l'optimisation puis est refusée (`PLACEMENT_GENERATEUR_OCCUPE`). Retrait avec P8.
10. **Ordre des verrous de la sérologie (exception documentée, constat 7).** Le chemin sérologie écrit la ligne médicale (flush JPA)
    AVANT de prendre le verrou du centre, à l'inverse de l'ordre global « centre d'abord ». Prendre le verrou avant l'enregistrement
    exigerait un second appel publié et deux transactions croisées dans le legacy ; aucun chemin inverse n'existe (la fiche et
    l'optimisation n'écrivent jamais `serologies_patient` ni ne la verrouillent), donc aucun interblocage possible, et l'attente est
    bornée à 5 s (décision 8). Exception acceptée, à reprendre en P7 si une écriture de sérologie entre dans le contexte.
11. **Temps réel après commit** : les alertes à identifiant imposé (P3 à P5) sont diffusées en `afterCommit` quand une transaction est
    active, jamais avant (pas d'alerte fantôme si la fiche ou la sérologie est annulée par un 409/422).
12. **Alerte du générateur** : clé d'idempotence = générateur + jour du centre de l'instant `survenuLe` de l'événement (un rejeu après
    minuit ne crée pas de seconde alerte) ; `nbPatients` compte tous les patients du générateur (cohérent avec le legacy, KB-PLN-63).

## Conséquences

+ Plus de double occupation d'un générateur ; sérologie et réaffectation atomiques ; alertes sans doublon ; garde de centre sur la fiche.
+ Le seam Patient et son extension Sérologie sont fermés à la fin de la slice (`seamAllowed` vide).
− Un centre jamais paramétré reçoit une ligne `planning_parametres` (valeurs par défaut) : contrôle SELECT dans `verifications-production.md`.
− La fiche écrit encore les colonnes de place (P7 : `Patient.libererPlacement`) ; l'ACL `PlacementDeLaFiche` est temporaire.
− `PlacementPatientPort`/`PlacementPatientJdbcAdapter` et `PlanificationAffectationService` restent pour l'optimisation (P8).
