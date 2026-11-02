---
status: proposed
date: 2026-11-02
scope: contexte planification (P1–P9) + revue de la slice P9 (de7fcd20)
sources: check-arch.ts, state.ts show, git (3f481fc2…de7fcd20), ADR 0049–0061/0068/0069, tactical/planification.md, glossaries/planification.md, known-bugs.md, seams.md, covered.json, roadmap.md, verifications-production.md, slices/planification-p*.md
reviewer: relecteur indépendant (lecture seule, aucun build ni test lancé)
---

# Vérification globale : Planification (P1–P9) et revue de P9

## 0. Contrôles automatiques

| Contrôle | Résultat |
|---|---|
| `check-arch.ts` | **0 violation** (0 en baseline, 0 nouvelle) |
| ArchUnit `FrontieresDesContextesTest` (lu, non exécuté) | +2 règles P9 cohérentes avec le code : aucun import de `domain.infirmier` ; consommateurs de `domain.planning` = exactement `PlacementDeLaFiche`, `PlanningParametresDuContexte`, `PatientRestController`, `DirectionCapaciteQueryService` (grep conforme) |
| SQL sur les tables de la Planification hors contexte (grep, `src/main`, majuscules et minuscules) | **0 lecteur** (pas même les 4 fichiers de la liste blanche) ; aucune entité JPA `@Table` sur ces tables |
| `LocalDate.now` / `Instant.now` dans `contexts/planification/.../domain` | 0 (domaine pur) |
| `state.ts show` | P1–P8 `built`, P9 `in-progress`, contexte `architected` |

## 1. Constats

| # | Sévérité | Domaine | Constat | Preuve | Correction recommandée |
|---|---|---|---|---|---|
| M1 | major | tactique / legacy | **Le « calcul de présence unique » n'est pas unique** (violation de la cible, et texte inexact) : dans « Mon planning », les occupants viennent de `SemaineDuPlanning` (le sommeil est exclu), mais le nombre `patients` et le `requis` viennent de la présence P4 alimentée par `PlanningDesPlacesDuCentreJdbc` (le sommeil garde sa place). Résultat : `patients: 2` pour 1 occupant. La javadoc du test, la RG et KB-PLN-106 affirment que « la règle de présence de P4 l'en retire, comme les alertes ». C'est faux : KB-PLN-50 dit « patient en sommeil toujours compté (non décidé) » dans les alertes de sureffectif. La décision du 2026-11-02 repose donc sur une prémisse inexacte | `SemaineDeLInfirmierDuModule.java:23-25,86` ; `LecturesDeLaPresence.java:32` ; `PlanningDesPlacesDuCentreJdbc.java:21` ; `SemaineDuPlanning.java:122` ; `MonPlanningCharacterizationIntegrationTest.java:66-67` ; `05-planning-placement.md:553` ; `known-bugs.md:1136` vs `:1298` ; golden `planning_patient_en_sommeil_base_nouveau_code.approved.json` (`patients: 2`, 1 occupant) | Faire **re-valider par l'utilisateur** KB-PLN-106 et KB-PLN-50 ensemble : (a) le sommeil compte dans l'effectif requis (texte à corriger partout, écart assumé) ou (b) le retirer de la présence P4 (nouvel écart, nouveaux goldens P4). Corriger la javadoc, la RG et KB-PLN-106 dans tous les cas |
| M2 | major | archi / tests | **La liste blanche de `TableDeLaPlanificationTest` est entièrement morte** : aucune des 4 entrées ne lit une table de la Planification, et `ReferentialRepositoryAdapter.java` n'existe pas (le fichier réel est `ReferentialAdminJdbcAdapter.java`). La dette réelle de ces fichiers porte sur les colonnes de place de `patients` (Patient) et sur `domain.planning` (Java), hors du périmètre de la garde. La liste ouvre donc une porte : ces 4 noms pourraient lire `infirmier_*` sans échec. La liste est aussi indexée par nom de fichier, pas par chemin (homonymes possibles) | `TableDeLaPlanificationTest.java:45-51` ; grep : `SeancePlanningJdbcAdapter.java:46-47` lit `patients`, `center_holiday` ; `ReferentialAdminTables.java:29,37` lit `patients` ; `DirectionCapaciteQueryService.java:6-8` importe des classes Java | Vider `LISTE_BLANCHE` (la garde passe sans elle), ou ajouter un test « chaque entrée de la liste blanche est un vrai lecteur ». Corriger ADR 0069 §5, la fiche P9 et le roadmap (« Dette de P9 ») pour décrire la dette réelle |
| M3 | major | legacy / état | **`state.json` contredit le roadmap et les fiches** : `organisation-o1..o4`, `patient-couverture-pc1..pc5`, `seance-se1..se7`, `dossier-medical-dm1..dm8`, `pilotage-direction-pd1..pd5` sont `built` (posés par le commit 2978e639 « feat(pec) »), alors que le roadmap les dit `planned` et que les fiches sont en `characterized`. `/ddd-run` et `/ddd-status` en seront faussés (hors périmètre Planification, mais bloque le point (6)) | `.ddd/state.json` (diff de 2978e639) ; `roadmap.md:275-327` ; `slices/organisation-o1-societes-centres.md:3`, `seance-se2-validation-signature.md:3` | L'utilisateur fixe l'état réel, puis `state.ts set slices.<id> characterized` pour chaque slice concernée (voir §6) |
| m1 | minor | tests | **Golden `planning_patient_en_sommeil_base` : fidélité plausible mais non prouvable par git.** Le fichier est **ajouté** par de7fcd20 (aucune version antérieure : `git log --all` ne donne que ce commit), donc on ne peut pas le comparer à l'historique. Contrôle croisé : le diff avec `_nouveau_code` porte **seulement** sur le bloc de 9 lignes de l'occupant `PAT_SOMMEIL` (l. 90-98). C'est cohérent avec le legacy supprimé : le sommeil garde sa place (`PlanningDonneesJdbcAdapter@de7fcd20~1:39`), les occupants sont triés par nom (`PlanningSemaineService@~1:121`), et `patients`/`requis` viennent de la présence. Aucun test ne lit ce `_base` : le résolveur préfère `_nouveau_code` (`RoulementCharacterizationSupport.java:387`) | fichiers du golden ; `git show de7fcd20~1:…SemaineDeLInfirmierLegacy.java` | Pour le certifier : rejouer le scénario seul sur un worktree `de7fcd20~1` (cherry-pick du seul test) et comparer octet à octet. Sinon, noter dans KB-PLN-106 « reconstruit, non rejoué ». Renommer en `planning_patient_en_sommeil` et archiver l'ancien comportement dans `_ecarts-legacy/` (convention de P1, `PlanificationCharacterizationSupport.java:256`) |
| m2 | minor | tests | `MouvementsPatientsIntegrationTest` est **affaibli** : le port publié `Occupation` n'a pas de `patientId`. L'exclusion du décédé et l'isolement par centre ne se prouvent plus que par un compte (`== 2`), et le « futur » est pris au premier `dernierJour != null` | `MouvementsPatientsIntegrationTest.java:140-141` ; `PlanningDesPlaces.java:23` | Identifier les patients par leur salle/créneau distincts dans le jeu de données, ou garder l'assertion par patient au niveau du contrat JDBC du contexte |
| m3 | minor | archi | 3 interfaces de `publishedlanguage` n'ont plus aucun consommateur ni fournisseur externe : `SemaineDeLInfirmier`, `PlanningDesPlaces`, `CalendrierDuCentre`. Ce sont des ports internes, mais seul le premier est noté en dette | grep des imports hors `contexts/planification` : 0 ; fiche P9 « Reste à faire », dernière ligne | Les déplacer vers `application` (session 2 de P9) |
| m4 | minor | tests | La suite GMAO injecte un port **interne** de la Planification (`application.SalleDuCentre`) | `EquipementHttpCharacterizationIntegrationTest.java:109` | Passer par le port publié `CapaciteDesSalles` ou par une lecture HTTP |
| m5 | minor | legacy | `covered.json` : **361 entrées mortes sur 492** (fichiers supprimés), dont environ 95 de la Planification (tout `domain.infirmier`, `PlanningSemaine*`, `SalleVue*`, `*Legacy` ACL, Timefold legacy…). Si un fichier du même chemin était recréé, le hook le laisserait passer sans filet | script node sur `covered.json` (liste dans la sortie de vérification) | Purger les chemins absents (via un outil, après accord), ou faire ignorer ces chemins par `guard-legacy.ts` |
| m6 | minor | tactique | Règles divisées entre le use case et le domaine : gardes statiques à paramètres booléens calculés par l'appelant (`Remplacement.exigerUnRemplaceConcerne(boolean, boolean)`, `ExecutionDOptimisation.exigerAucuneEnCours(boolean)`). Obsession des primitives dans `ExecutionDOptimisation` (`String phase/score/lancePar`) et dans `CapaciteDeLaSalle` (`Integer`, `int`) | `Remplacement.java:44-65` ; `ExecutionDOptimisation.java:50,78` ; `CapaciteDeLaSalle.java` | Suggestion : un VO `Score`/`Phase` ; passer au domaine les faits (absences du jour, roulement) plutôt que des booléens |
| m7 | minor | tactique | `ControlerLaCapaciteDeLaSalle` lit puis décide sans verrou : deux affectations concurrentes dans la même salle peuvent toutes deux passer. Le legacy faisait de même (parité) | `ControlerLaCapaciteDeLaSalle.java:21-28` | Suggestion : documenter le cas dans ADR 0069 ou verrouiller la salle côté GMAO |
| m8 | minor | tests | Tests qui dépendent de la date : (a) attente active jusqu'à minuit (`Thread.sleep`) si l'on est à moins de 2 min de minuit ; (b) `assumeTrue(UTC == jour du centre)` saute **silencieusement** les tests entre 23:00 et 24:00 UTC. Les deux sont **obsolètes** depuis le retrait du legacy, puisque seule l'horloge du centre compte ; (c) bombe à retardement : `LocalDate.of(2028,1,1)` est utilisé comme date future pour une déclaration | `RoulementCharacterizationSupport.java:146-152, 255-259` ; `AbsencesDesInfirmiersCharacterizationIntegrationTest.java:116-122` (et 2040 aux l. 171, `RemplacementsCharacterizationIntegrationTest.java:135`) | Injecter l'horloge (`Clock` fixe) dans les filets, et remplacer 2028 par `aujourdhui().plusYears(1)` |
| m9 | minor | legacy | Adapter avec horloge système en dur, sans `Clock` injectée | `DeplacementsTemporairesJdbc.java:36` | Injecter `Clock` (comme `ParametresDuPlanningJdbc`) |
| n1 | nit | docs | `seams.md:97` titre encore « P5 … : OUVERT », alors que `seamAllowed = []`. Le titre P4 (`:70`) n'a pas de statut | `seams.md:70,97` | Marquer « FERMÉ » |
| n2 | nit | docs | `verifications-production.md:1458` dit « à décider » pour les patients sortis et en sommeil, alors que c'est décidé (« VOULU, 28/10 ») | `known-bugs.md:1243` | Aligner |
| n3 | nit | docs | `roadmap.md` : 174 occurrences de mojibake (`Ã©`, `â€`), introduites avant P9 (2978e639) | `roadmap.md:190-231` | Ré-encoder en UTF-8 |
| n4 | nit | tests | Imports de `domain.planning.model.JourSemaine` dans les filets (hors du périmètre ArchUnit, tests exclus) : à migrer quand `JourSemaine` sera retiré | `DeplacementsEtSeancesRealiseesCharacterizationIntegrationTest.java:13` | Jalon Patient et couverture |

**Vérifié sans constat** :
- **Suppression de `PlanningSeancesRealiseesIntegrationTest` : justifiée.** Son unique scénario est couvert par le filet P6. « La validation fige la place » est dans `LecturesDePlaceConsommeesParLaSeanceCharacterizationIntegrationTest.java:49,110`, par le même point d'observation `SeanceUseCase.validate`. « Réalisée hors planning, déjà réalisée le, aucun conflit, autre centre ignoré » est dans `DeplacementsEtSeancesRealiseesCharacterizationIntegrationTest.java:110,118,166,180,192`.
- **Les 4 classes de test legacy supprimées sont cohérentes avec la suppression des classes qu'elles testaient** (`PresenceInfirmierServiceTest` et sa donnée `PresenceTestData`, `PlanningSemaineServiceTest`, `CapaciteSalleRegleTest`). Elles sont remplacées par `ControlerLaCapaciteDeLaSalleTest` (5 cas) et par les tests de domaine P4/P6.
- **Pas d'édition de production legacy en P9.** Seules les suppressions (règle 2c) et les nouveaux fichiers du contexte. La capacité des salles est à parité : même filtre `REFORME`/`DESACTIF` (`VueDesSallesLegacy.java:27` vs `SalleVueJdbcAdapter@~1:26`).
- **Domaine pur** : ni Spring, ni JPA, ni horloge dans `contexts/planification/domain`. Le câblage est dans `configuration/` (composition root).

## 2. Cohérence des artefacts (validations utilisateur en attente)

- **ADR `proposed` (15)** : 0049, 0050, 0051, 0052, 0053, 0054, 0055, 0056, 0057, 0058, 0059, 0060, 0061, 0068, 0069.
- **Glossaire : 71 termes `proposed`** (`glossaries/planification.md:50-156`).
  - P1 : Paramètres du planning, Ratio de sécurité, Patients par poste et par série, Jours d'ouverture, Salle d'isolement, Compétence, Taux d'activité, Verrou du centre.
  - P2 : Infirmier (fiche), Matricule, Qualification, Habilité à l'isolement, Compte lié, Compte liable, Identifiant de connexion, Mot de passe temporaire, Verrou du référentiel des infirmiers, État du compte.
  - P3 : Roulement, Affectation, Absence d'un infirmier, Période d'absence, Remplacement, Remplaçant éligible, Remplacé, Sur-effectif, Mon planning, Jour du centre, Verrou de la fiche, Absence sans chevauchement, Auteur de l'absence.
  - P4 : Présence de la semaine, Case, Effectif requis, Ratio lu, Alerte de sous-effectif, Alerte de sur-effectif, Double affectation, Candidat remplaçant, Charge mensuelle, Ouverture du centre, Contrôle de la présence, Impression de la présence.
  - P5 : Places du centre, Place, Proposition de place, Salle d'isolement réservée, Retour en salle ordinaire proposé, Isolement impossible, Alerte de générateur indisponible, Verrou des places.
  - P6 : Séjour du patient, Patient présent, Patient attendu, File active, Semaine du planning, Centre masqué, Base de la capacité théorique.
  - P7 : Place tenue, Place quittée, Place libérée, Libération des places, Échéance jugée à la veille, Patient sorti, Libération en échec.
  - P8 : Calcul orphelin, Battement de cœur, Transition gardée, Couverture des absences, Alerte personnelle, Générateurs de secours.
  - P9 : Capacité de la salle, Semaine de l'infirmier. Ce dernier terme a une définition à corriger après M1.
- **Known bugs non tranchés** : KB-PLN-04 (décimaux tronqués), KB-PLN-06 (salle inconnue = autre centre, `salles_isolement` remise à NULL), KB-PLN-14 (noms fiche/compte non synchronisés), KB-PLN-50 (sommeil compté dans les alertes, « non décidé »), KB-PLN-106 (« nuance à valider »).
- **Seams** : `seamAllowed = []`, donc aucun seam ouvert dans les faits. Reste en attente la **ratification écrite** de l'extension `AuditActionResolver` de P3 (`seams.md:60`, « à confirmer par écrit »).
- **Modèle tactique** (`approved`, 2026-10-26) : ne mentionne ni `CapaciteDeLaSalle`/`CapaciteDesSalles` ni `SemaineDeLInfirmierDuModule`. La ligne `:526` (« retrait du double calcul de présence reporté à P9 ») reste ouverte (voir M1). Un amendement est à proposer à la fin de P9.
- **Fiches de slice** : P1–P7 `built`, P8 `built` (statut multi-ligne), P9 `in-progress` (session 1 sur 2-3).

## 3. Contrôles de production avant mise en service de la Planification (ordre, prérequis)

Toutes les requêtes sont en lecture seule (SELECT), sur une copie ou en lecture sur la production. Aucun `UPDATE`/`DELETE` sans validation écrite.

| Ordre | Contrôle | Réf. | Attendu / blocage |
|---|---|---|---|
| 0 | Prérequis : version Flyway (V47, puis V48, V49 en attente d'application), base de copie disponible | §8, §34, P8 | **Bloquant** |
| 1 | Fuseau des centres absent ou illisible | §34-7 (= P4-5, P5-4) | 0 ligne, sinon fixer le fuseau |
| 2 | Ratio hors 1–20 ; patients par poste hors 1–10 | P4-1, §32-4, P6-5 | **Bloquant** (traitement manuel) |
| 3 | Jours d'ouverture illisibles ou en minuscules | §32-3, P6-4 | **Bloquant** (sinon 400 sur la semaine) |
| 4 | Compétences illisibles ; taux d'activité hors 10–100 ; réglages hors bornes | §32-2, §32-6, §32-5 | **Bloquant** (sinon lecture en erreur) |
| 5 | Comptes liés à des fiches de plusieurs centres | §33-5 | **Bloquant** (délier ou créer un compte par centre) |
| 6 | Doublons de matricule selon la casse ; doublons d'`username` | §33-1, §33-3 | **Bloquant** (régulariser) |
| 7 | Affectations qui se chevauchent ; absences qui se chevauchent | §34-1, §34-2 | 0 ligne attendue, sinon régulariser à l'écran |
| 8 | Centres inactifs (ou sans ligne `centers`) avec des places échues | P7-1 | **PRÉREQUIS BLOQUANT** explicite |
| 9 | Calculs d'optimisation `EN_COURS` / plusieurs par centre, **avant** V48 | P8-2, P8-3 | 0 ligne (sinon la reprise les interrompt) ; le document les place seulement « après déploiement », il faut aussi les lancer avant |
| 10 | Centres sans ligne `planning_parametres` (la ligne par défaut sera créée) | §32-7, P5-1, P7-8 | Informatif ; faire valider les valeurs par défaut |
| 11 | Patients sains en salle d'isolement ; sérologies hors centre | P5-2, P5-3 | Informatif (refus futur) |
| 12 | Patients en sommeil avec une case ; écart entre règles ; sortis encore placés | P6-1, P6-7, P9-1, P7-3, P7-4 | Informatif ; à annoncer aux infirmiers (« Mon planning ») |
| 13 | File active 1–4 (centres masqués) ; générateurs hors `EN_SERVICE` ; fériés à 60 jours | P6-2, P6-3, P6-6 | Informatif |
| 14 | Fiche/compte incohérents ; fiche dont le compte est supprimé ; colonnes à la borne | §33-4, §33-6, §33-7 | Informatif |
| 15 | Remplacements passés, auto-remplacements, sans remplacé ; absences/affectations d'inactifs ; motifs de 255 car. ; absences futures sans auteur | §34-3, §34-8, P8-10, §34-4, §34-5, §34-6 | Informatif |
| 16 | Préférences de patients sortis, profils inactifs ; centres sans infirmier actif ; modèles d'impression ; doublons de notifications | §32-1, P4-2, P4-4, P4-3, P5-5 | Informatif |
| 17 | Capacité des salles dépassée | P9-2 | Informatif |
| 18 | Après déploiement : P7-2 à 0 après la 1re nuit, P7-5 à P7-7, P8-1 à P8-12, journaux `[PRESENCE]` et `[PLACES]`, ordre 02:30 → 04:00 → 07:15 | P7, P8 | Surveillance |

Manque : `verifications-production.md` n'a pas de section consolidée pour la Planification, comme le §31 du Stock. Ajouter un §35 qui reprend ce tableau.

## 4. Dette restante (jalons)

| Fichier:ligne | Nature | Jalon |
|---|---|---|
| `SeancePlanningJdbcAdapter.java:46-47` | règle de présence recopiée (2e calcul hors du contexte) | Séance |
| `SeanceRestController` (jours de dialyse du tableau de bord, voir roadmap « Dette de P9 ») | lecture des jours des patients | Séance |
| `ReferentialAdminTables.java:29,37` ; `ReferentialAdminJdbcAdapter` | « utilisé par un patient ? » | Organisation |
| `PlacementDeLaFiche.java:4`, `PlanningParametresDuContexte.java:4-6`, `PatientRestController.java:5` ; `Patient.java:126` (`libererPlacement`), `LiberationALEnregistrementDeLaFiche.java:54` | `JourSemaine`, `PlanningParametres(+Port)`, écrivain de place | Patient et couverture |
| `DirectionCapaciteQueryService.java:6-8` | `CapaciteTheoriqueCalculator` (bc-seance) | Pilotage direction |
| `bc-seance/.../domain/planning/{model/JourSemaine, model/PlanningParametres, port/PlanningParametresPort, service/CapaciteTheoriqueCalculator}.java` | les 4 dernières classes legacy de bc-seance/planning | idem |
| interne au contexte : présence P4 (sommeil compté) vs semaine P6 (sommeil exclu) | double calcul de présence | décision M1 |

## 5. Sort des filets P1–P8

Recommandation (à valider) : les **conserver comme tests de non-régression HTTP** du contexte. Ils sont la seule couverture de bout en bout des URL inchangées. Actions :
- retirer les mécanismes de double exécution legacy (`assumeTrue` UTC, attente de minuit) ;
- déplacer les goldens `_base` remplacés vers `_ecarts-legacy/` (archives) ;
- renommer `_nouveau_code` en nom simple au moment de la clôture de P9.

Les goldens `_base` sans test qui les lit restent des archives, sans valeur exécutable.

## 6. État proposé (non appliqué)

- **P1–P8** : `state.ts set slices.planification-pN-… verified`, seulement après décision sur M1 et correction de M2 (P4/P6 sont touchés par M1).
- **P9** : rester `in-progress`. Passer `built` à la fin de la session 2, puis `verified` après la revue.
- **Contexte** `planification` : rester `architected`, puis `migrated` (ou l'équivalent utilisé pour Stock) quand P9 sera `verified`.
- **Hors périmètre (M3)** : remettre à `characterized` (ou `planned`) les slices O/PC/SE/DM/PD marquées à tort `built`.

## 7. Verdict

**pass-with-actions**

Top 3 actions :
1. **M1** : faire trancher par l'utilisateur, ensemble, KB-PLN-106 et KB-PLN-50 (le sommeil compte-t-il dans l'effectif requis ?), puis corriger la javadoc, la RG-PLN, KB-PLN-106 et le golden si la décision change.
2. **M2** : vider ou contrôler la liste blanche de `TableDeLaPlanificationTest`, et corriger la « Dette de P9 » (fichier inexistant, dette mal qualifiée).
3. **M3** : réaligner `state.json` sur les fiches et le roadmap pour les contextes restants, puis ajouter le §35 consolidé dans `verifications-production.md` (ordre ci-dessus).

Prochaine commande du pipeline : `/ddd-slice planification-p9-retrait-du-legacy` (session 2 : ports internes, liste blanche, M1). Ensuite, selon le roadmap (Organisation = fondation OHS, O1 `characterized`) : `/ddd-slice organisation-o1-societes-centres`.
