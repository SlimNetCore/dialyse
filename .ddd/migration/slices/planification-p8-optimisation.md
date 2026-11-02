---
status:
  built (S1+S2+S3 le 2026-10-28: 4 classes de caractérisation vertes sur le nouveau code ; retrait complet du legacy d'optimisation effectué)
date: 2026-10-28
sources: .ddd/safety-net/planification-p8-optimisation.md, .ddd/tactical/planification.md (P20 à P25), ADR 0024, 0049 à 0061, known-bugs.md KB-PLN-89 à 105
---

# Slice planification-p8-optimisation (refactoring sur place, ADR 0024)

Cible : `backend/contexts/planification` (agrégats `ExecutionDOptimisation`, `DeplacementTemporaire`, port `Optimiseur`). Filet : 39 scénarios, 103 goldens (commit 77aca8dd), vert sur le legacy. Aucune façade ni flag (ADR 0024). Seams ouverts : aucun à ce jour.

## Décisions de l'utilisateur (28/10) appliquées

409 `DEJA_EN_COURS` / `DEJA_APPLIQUEE` / `PERIMEE`, 404 introuvable, 400 entrée illisible, pagination ≤ 100 ; verrou du centre EN BASE pour « un seul calcul par centre » ; application par les agrégats P3 en UNE transaction ; remplacé relié ; infirmiers notifiés ; empreinte élargie ; nuit sur centres actifs au jour du centre ; un déplacement temporaire par patient et date, cible incohérente refusée ; `Optimiseur` injectable ; contraintes Timefold reproduites à l'identique.

## S1 (domaine + application + ports + doubles + tests) : FAIT

### Domaine (`…/planification/domain`)
`ExecutionDOptimisation` (cycle EN_COURS / TERMINEE / ECHEC, `exigerAucuneEnCours`, `appliquer(empreinte, instant)`, `exigerSupprimable`), `ParametresDOptimisation` (P21), `Perimetre`, `ObjectifDesInfirmiers`, `Empreinte` (SHA-256, élargie aux séances réalisées et préférences), `ResultatDOptimisation` (+ `Indicateurs`), `Poste`, `DonneesDOptimisation` (la présence en dérive : UNE règle, celle de P4, `Presence`/`PresenceDeLaSemaine` : le double calcul du legacy n'est pas porté), `IndicateursDOptimisation` (P24), `CalendrierDeLaProposition` (P24), `VerificationDesDeplacements`, `MotifDeProposition`, `DeplacementTemporaire` (P25, cible cohérente exigée), ports `ExecutionsRepository`, `DeplacementsTemporairesRepository`. Ajouts aux agrégats existants : `PlacesDuCentre.replacerLesPatients` / `violationsDe` (tout ou rien, jugé sur l'état final), `RoulementDeLInfirmier.remplacerPar` (jugé : `AFFECTATION_CHEVAUCHEMENT`), identifiants `ExecutionId`, `DeplacementTemporaireId`.

### Application (`…/planification/application`)
`LancerUneOptimisation` (verrou du centre en base via `VerrouDesPlaces`, réglages figés, historique 20, solveur lancé APRÈS commit, suivi transitionnel), `ConsulterLesOptimisations`, `ArreterUneOptimisation`, `SupprimerUneExecution`, `ReprendreLesExecutionsInterrompues`, `AppliquerUneProposition` (centre puis fiches par identifiant croissant, empreinte relue sous verrou, places / roulement / couverture avec remplacé / déplacements temporaires, notifications), `ReplanifierLaNuit` (centres actifs, jour du centre, bilan toujours envoyé). Ports : `Optimiseur`, `DonneesDeLOptimisation`, `CalendriersDesPropositions`, `NotificationsDeLOptimisation`, `NouveauxIdentifiantsDeLOptimisation`.

### Tests (module, tous verts : 631 au total, ArchUnit 11, `check-arch.ts` 0 violation)
Domaine : `ExecutionDOptimisationTest`, `ParametresDOptimisationTest`, `ReplacementDesPatientsDUnePropositionTest`, `RoulementVouluParUnePropositionTest`, `DeplacementTemporaireTest`, `EmpreinteEtIndicateursDOptimisationTest`, `MotifEtCalendrierDeLaPropositionTest`. Application : `LancementEtSuiviDesOptimisationsApplicationTest`, `ApplicationDUnePropositionApplicationTest` (32), `ReplanificationDeNuitApplicationTest`. Doubles : `OptimisationEnMemoire` (dont fausse implémentation de `Optimiseur` pilotée par le test), `MondeDOptimisation`.

## Reste à faire

### S2 (adapters)
JDBC `ExecutionsRepository` (+ calendrier `planification_calendrier_case`), `DeplacementsTemporairesRepository` (DELETE + INSERT dans l'ordre, propriétaire unique de la table), `DonneesDeLOptimisation` (lecture des données : ressources, personnel P4, patients, préférences, profils, indisponibilités GMAO via `IndisponibilitesDesGenerateurs`, séances réalisées), `NotificationsDeLOptimisation` (`SEANCES_DEPLACEES`, `OPTIMISATION_PROPOSITION`, `REPLANIFICATION_NOCTURNE`, nouvelles notifications des infirmiers à clé déterministe), `NouveauxIdentifiantsDeLOptimisation`, Timefold 2.7.0 derrière `Optimiseur` dans `infrastructure/optimisation/timefold` (déplacer tels quels les mappers et `*ConstraintProvider` en adaptant les types : `SchemasJoursService`, `MaintenanceGenerateursService`, `retirerInvalides` à porter avec eux), dépendance Timefold dans le pom du module, advice HTTP (409 / 404 / 400, codes `OPTIMISATION_*`), contrôleurs `/api/v1/planning/optimisations` (403 avant `@Valid`), job 02:30 (`COALESCE(actif, TRUE)`, jour du centre), reprise au démarrage, `PlanningOptimiseReportService` (impression), contrats double / H2 / PG16 (`backend/scripts/planification-p8-pg16-rejeu.sh`), éviction des caches patients après commit, diffusion temps réel.

### S3 (parité et retrait)
Goldens `_nouveau_code` (archivage préalable des anciens dans `_ecarts-legacy/**/*.legacy.json` depuis `git show HEAD:`), retrait du legacy d'optimisation, suppression du double calcul de présence (`PlanningDonneesJdbcAdapter`, `PresenceDonnees*`, `PresenceInfirmierService`, `SemaineDeLInfirmierLegacy`) après accord seam par seam (`seams.md` + `covered.json`), `SeancePlaceJdbcAdapter` et ACL P6 `DeplacementsTemporairesJdbc` lisent via le port publié, PIT domaine + application (solveur exclu), `./mvnw -o test` complet, frontend si touché, ADR suivant (proposed), known-bugs, glossaire (proposed), référentiel RG + test garde, `verifications-production.md`, `roadmap.md`, état `built`.

## Écarts décidés (tracés dans l'ADR 0068, proposed, à valider ; revue du 2026-10-29 : voir `.ddd/verify/review-planification-p8.md`)

Corrections de la revue (2026-11-02) : B1 (transitions gardées, UPDATE conditionnels, reprise par instance, V48), B2 (ADR 0068, statuts
KB-PLN-89 à 105), M1 (3 goldens supprimés + garde), M2 (alertes personnelles, V49, front), M3 (3 tests restaurés, encodage), M4 (reporté P9,
roadmap), M5 (`CouvertureDesAbsences`), M6 (docs), m1 à m6, m8, n1, n2.

| Sujet | Décision prise en S1 | Pourquoi |
|---|---|---|
| Code d'un roulement qui chevauche | `AFFECTATION_CHEVAUCHEMENT` (422) | code du P3, pas de code propre à l'optimisation |
| Couverture | remplaçant jugé par `RemplacantEligible.prouver` (situation honnête : actif, habilité, absent, déjà prévu) | « passer par les agrégats P3 » ; peut refuser (`REMPLACEMENT_INELIGIBLE`) une proposition que le legacy appliquait : à confronter au filet en S3 |
| Remplacé relié | l'absent du jour, prévu sur la case au roulement, pas déjà remplacé, par identifiant croissant | KB-PLN-97 |
| Verrou | pris pour TOUS les périmètres à l'application (le legacy ne le prenait que pour les places) | ADR 0051 : double application impossible |
| Fiches | verrouillées avant la relecture de l'état (lecture supplémentaire des données pour connaître les infirmiers) | empreinte jugée sous verrou |
| Premier refus de place | ordre des déplacements de la proposition (le legacy dépendait de l'ordre d'une `HashMap`) | déterminisme |
| Empreinte | une proposition calculée avant la montée de version est périmée | élargissement voulu |

## État des lieux du 2026-10-29 (reprise)

- **S2 : fait et commité** (e5f1cfbd, a6fbbe09, 8fb1821f) : adapters JDBC (`ExecutionsJdbc`, `CalendriersDesPropositionsJdbc`, `DeplacementsTemporairesJdbc`), lecture des données (`LireLesDonneesDeLOptimisation` + `ContraintesDesPatientsJdbc`, GMAO), notifications à clé déterministe, Timefold 2.7.0 dans `infrastructure/optimisation/timefold` (mappers et contraintes déplacés, types adaptés), `OptimisationsControleur` + advices (404 / 409 / 422 / 400, pagination <= 100, 403 avant lecture du corps), jobs 02:30 et reprise au démarrage, contrats double / H2 / PG16 + course « un seul calcul par centre » tenue en base. Module : 704 tests verts (ArchUnit 11), `check-arch.ts` 0 violation, PG16 jetable (`planification-p8-pg16-rejeu.sh 55468`) vert : 18 tests, 12 requêtes SELECT-only exécutables.
- Les sections « Reste à faire » ci-dessus sont historiques (S1) : tout a été livré. Seam P8 FERMÉ le 2026-10-29 (`seamAllowed` vide, les 2 ACL neuves dans `covered.json`).
- **Non constaté dans le dépôt, à confirmer ou faire** : ADR P8 (le dernier est le 0061), entrées `known-bugs.md` / glossaire / `verifications-production.md` pour P8, PIT du domaine et de l'application (solveur exclu), `./mvnw -o test` complet, tests frontend.
