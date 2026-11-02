---
status: built (session 2 terminée le 2026-11-02 : `./mvnw -o test` complet vert, 2469 tests de la plateforme, `check-arch.ts` 0 violation ; reste `verified` après revue et validations de l'utilisateur)
date: 2026-11-02
sources: roadmap « Dette de P6 / P8 / P9 », ADR 0069, KB-PLN-106, `.ddd/safety-net/seams.md`
---

# Slice planification-p9-retrait-du-legacy

Refactoring sur place (ADR 0024) : ni façade, ni flag, ni routage. Aucun fichier legacy édité (seam non utilisé) ; fichiers supprimés par `rm`.

## Fait (session 1, 2026-11-02)

| Domaine | Fait |
|---|---|
| Mon planning | `SemaineDeLInfirmierDuModule` (compose `ConsulterLaPresence` + `ConsulterLaSemaine`) ; ACL `SemaineDeLInfirmierLegacy` supprimée ; 1 scénario ajouté, 2 goldens (`_base` = ancien comportement archivé depuis le legacy, `_nouveau_code`) ; les 17 autres goldens inchangés |
| Places et calendrier | `PlanningDesPlacesDuCentreJdbc`, `CalendrierDuCentreDuModule` ; ACL `PlanningDesPlacesLegacy`, `CalendrierDuCentreLegacy`, `PlanningDonneesJdbcAdapter`, `PlanningDonneesPort` supprimés ; `MouvementsPatientsIntegrationTest` relu par le port publié |
| Capacité des salles | port publié `CapaciteDesSalles`, `ControlerLaCapaciteDeLaSalle`, `CapaciteDeLaSalle` (+ 5 tests), port `SalleDuCentre` ; ACL GMAO `CapaciteDesSallesDePlanification` ; `SalleVueJdbcAdapter`, `SalleVuePort`, `SalleGenerateursService`, `CapaciteSalleRegle` supprimés |
| Code mort retiré | `PresenceInfirmierService`, `PresenceDonnees*`, `PlanningSemaine*` (service, requête, port, modèle), tout `domain.infirmier` (modèle, ports, adapters `AffectationInfirmierJdbcAdapter`, `RemplacementInfirmierJdbcAdapter`), `domain.planning.model.Planning`, `DeplacementTemporaire`, `DeplacementTemporairePort`, 4 classes de test legacy et `PlanningSeancesRealiseesIntegrationTest` (couvert par le filet P6) |
| Gardes | `FrontieresDesContextesTest` (+2 règles), `TableDeLaPlanificationTest` (3 tests dont le négatif), test RG `mon_planning_documente_que_le_sommeil...` |

## Reste à faire (jalons explicites, rien forcé)

| Code | Raison | Jalon |
|---|---|---|
| `SeanceRestController` (jours de dialyse du tableau de bord) | 1200+ lignes, effectif de Patient | contexte Séance |
| `SeancePlanningJdbcAdapter` (règle de présence recopiée) | exige le port `attenduLe` et retire la règle côté Séance | contexte Séance |
| `ReferentialRepositoryAdapter`, `ReferentialAdminTables` | port « utilisé par un patient ? » (`UtilisePourUnPatient`) à publier par Organisation | contexte Organisation |
| `MouvementPatientJdbcAdapter`, `Patient.libererPlacement` | écrivains admission / changement d'état | contexte Patient et couverture |
| `JourSemaine`, `PlanningParametres(+Port)`, `PlanningParametresDuContexte`, `PlacementDeLaFiche`, `PatientRestController` | fiche patient | contexte Patient et couverture |
| `CapaciteTheoriqueCalculator` + `DirectionCapaciteQueryService` | tableau de bord Direction | Pilotage de la direction |
| Le port `SemaineDeLInfirmier` reste dans `publishedlanguage` alors qu'il est désormais interne | déplacer vers `application` | prochaine session P9 |

## Fait (session 2, 2026-11-02 : corrections de la vérification `.ddd/verify/verify-planification.md`)

| Constat | Correction |
|---|---|
| M1 (décision de l'utilisateur) | patient en sommeil **exclu partout** : `PlanningDesPlacesDuCentreJdbc` ne le lit plus (nombre `patients`, `requis`, alertes de sous-effectif et de sur-effectif, charge, « Mon planning » : un seul calcul, `SejourDuPatient`). 2 goldens en `_nouveau_code`, ancien comportement archivé : P9 `planning_patient_en_sommeil` (legacy **rejoué** sur un worktree jetable de `de7fcd20~1`, identique octet à octet) et P4 `patients_pris_en_compte`. RG-PLN-045/046 et RG « Mon planning », javadocs, KB-PLN-50 et KB-PLN-106, glossaire, ADR 0069 (reste `proposed`) corrigés ; test garde RG mis à jour |
| M2 | liste blanche de `TableDeLaPlanificationTest` vidée (chemins complets) ; un test échoue si une entrée ne correspond à aucun lecteur réel |
| M3 | **non corrigé, à décider par l'utilisateur** : `state.json` marque `built` les slices O1-O4, PC1-PC5, SE1-SE7, DM1-DM8, PD1-PD5 alors que le roadmap les dit `planned` et leurs fiches `characterized` (voir `roadmap.md`) |
| m2 | `MouvementsPatientsIntegrationTest` : chaque patient a sa place propre (salle et créneau), l'identification par patient est restaurée |
| m5 | `covered.json` : 363 lignes mortes retirées (dont 2 globs et ~95 de la Planification), 5 doublons fusionnés ; 129 entrées vivantes conservées, aucune supprimée à tort (comparaison avec les fichiers existants) |
| m8 | filets P3 : plus de double horloge UTC ni de scénario ignoré entre 23:00 et 24:00 UTC ; attente de minuit limitée au fuseau du centre ; année bissextile et dates lointaines relatives à la date d'exécution (goldens inchangés, dates nommées par alias) |
| n1, n2 | `seams.md` : titres P4 et P5 « FERMÉ » ; `verifications-production.md` : « à décider » remplacé par « VOULU, 28/10 » |
| §35 | `verifications-production.md` §35 : tableau ordonné des contrôles de production de la Planification (prérequis bloquants en tête), décisions « à décider » restantes (KB-PLN-04, 06, 14) en tête, non tranchées ; requêtes P9-3 et P9-4 (impact du sommeil sur le requis) exécutées sans erreur sur un PostgreSQL 16 jetable |

## Reste à faire

Voir le tableau « Reste à faire » de la session 1 (jalons Séance, Organisation, Patient et couverture, Pilotage : dettes de ces contextes). En plus : ratification écrite de l'audit P3 (`seams.md`, extension `AuditActionResolver`) à demander à l'utilisateur ; trois interfaces publiées sans consommateur externe (`SemaineDeLInfirmier`, `PlanningDesPlaces`, `CalendrierDuCentre`) à déplacer vers `application` (m3, non fait) ; m4 (le filet GMAO injecte `application.SalleDuCentre`), m6, m7, m9 (suggestions de la vérification) non traités ; flake connu hors périmètre : l'ordre des lignes de `ReplanificationDeNuitCharacterizationIntegrationTest.un_centre_sans_rien_a_calculer...` (tri sur `cree_le` sans départage) a échoué une fois sur deux exécutions groupées.

## Écarts de parité

2 écarts décidés, un seul changement de règle (le patient en sommeil exclu de la présence : KB-PLN-50 et KB-PLN-106, goldens `_nouveau_code` : P9 `planning_patient_en_sommeil`, P4 `patients_pris_en_compte`). Aucun autre.

## Plan de bascule

Sans objet (sur place) : même table, mêmes URL. Mesure en recette (`verifications-production.md`, §35 contrôle 12 et section P9, requêtes 1, 3 et 4) : SELECT seul, patients en sommeil placés par case et impact sur le requis et les alertes, avant livraison, pour annoncer l'effet aux infirmiers.
