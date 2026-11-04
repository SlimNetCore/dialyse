---
status: proposed
date: 2026-11-04
scope: slice patient-couverture-pc5-lectures-publiees + contexte patient-couverture (PC1–PC5)
sources: [git 945b872c..HEAD, d441f669, 692091b8 (+ 692091b8^ pour les lecteurs supprimés), check-arch, state.ts show, scan --incremental, ADR 0064/0073/0076–0079, .ddd/safety-net/*, .ddd/migration/verifications-production.md §38/§41–§44, TableDuPatientCouvertureTest, FrontieresDesContextesTest]
reviewer: relecteur indépendant (lecture seule ; aucun build ni test lancé)
---

# Vérification n° 2 — Patient et couverture (PC5 + contexte)

## Contrôles automatiques

- `check-arch` : **0 violation** (0 en baseline, 0 nouvelle).
- `scan --incremental` : **0 cycle** entre modules (summary.md:18).
- `state.ts show` : PC1–PC5 `built` ; contexte `patient-couverture: architected`.
- Goldens : `git diff d441f669^..692091b8 -- characterization` est **vide** (aucun golden créé, modifié ou supprimé par
  PC5). Entre 945b872c et HEAD : 25 ajouts (PC4) et 21 renommages R100 `approved → _ecarts-legacy/*.legacy.json`
  (f0f99e25, PC4, contenu identique) ; aucun golden modifié (M).

## Parité PC5 (vérifiée ligne à ligne contre `692091b8^`)

| Lecteur                                                                      | Verdict                                                                                                                                                   | Preuve                                                             |
|------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------|
| Absences `PatientsDuCentreJdbc` → `FichesDesPatients.existe`                 | identique (même SQL, mêmes paramètres)                                                                                                                    | `FichesPublieesJdbc.java:30`                                       |
| Absences `NomsDesPatientsJdbc` → `noms` + `NomDuPatient.complet`, nul ignoré | identique (IN, distinct, filtre centre, nom nul écarté côté ACL)                                                                                          | `FichesPublieesJdbc.java:35-47`, `NomsDesPatientsDeLaFiche`        |
| Absences `ForfaitsDesPatientsJdbc` → `forfaitApplicable`                     | identique (SELECT, ORDER BY, LIMIT 1, repli sans date)                                                                                                    | `CouvertureDesPatientsJdbc.java:22-26,69-82`                       |
| Facturation `SeancesFacturablesJdbc` (partie PEC)                            | identique (même SELECT/INNER JOIN, mapping des NULL, `created_at` → Instant)                                                                              | `CouvertureDesPatientsJdbc.java:85-103`                            |
| Séance `SeanceBillingEligibilityJdbcAdapter` → `causeDeNonFacturation`       | identique (3 étages, même ordre, 4/4/6 paramètres, correspondance 1-1 des causes) ; `SeanceBillingEligibilityPort` **inchangé** (dernier commit 361f677a) | `CouvertureDesPatientsJdbc.java:34-56,106-118`                     |
| `DashboardRestController` (6 compteurs)                                      | identiques ; prédicat d'effectif = copie littérale d'`EffectifSql.presentSur` (états triés, 4 paramètres fin/début×3 = même jour)                         | `CompteursDeCouvertureJdbc.java:31-85` vs `EffectifSql.java:21-55` |

Tests d'ACL conservés au-dessus des adapters réels, assertions non affaiblies (diffs de `ForfaitsDesPatientsJdbcTest`,
`LecturesDuSuiviJdbcContrat`, `SeancesJdbcContrat` : câblage seul).

Dépendances : Absences et Facturation → `contexts-patient-couverture` ; `patient-couverture` → `shared-kernel`,
`planification` uniquement (`patient-couverture/pom.xml:79,85`) ; `planification` → `gmao`, `shared-kernel` : **pas de
cycle**. ArchUnit `le_legacy_n_utilise_le_patient_couverture_que_par_son_langage_publie`
(`FrontieresDesContextesTest.java:176-182`). Langage publié : UUID / java.time / BigDecimal, records imbriqués, aucune
entité de domaine exposée, centre en premier paramètre partout.

## Constats

| Sév.  | Domaine          | Constat                                                                                                                                                                                                                                                                                                                                                                                       | Preuve                                                                                                                                                                                                                                    | Correction recommandée                                                                                                                    |
|-------|------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------|
| major | legacy           | **La garde et l'« inventaire exhaustif » sont aveugles au SQL dynamique et au JPA** : au moins 6 accès hors module aux tables du patient ne sont ni dans la liste blanche ni détectables par l'expression régulière (`FROM                                                                                                                                                                    | JOIN                                                                                                                                                                                                                                      | INTO                                                                                                                                      |UPDATE|TABLE` suivi d'un identifiant littéral). `ReferentialAdminJdbcAdapter`/`ReferentialAdminTables` (COUNT sur `prise_en_charge`, `patients`) n'apparaît **nulle part** dans la doc PC5. L'affirmation « plus aucun source hors du contexte » est fausse. | `HistoricalRecordJdbcAdapter.java:30,32,118` (INSERT `"INSERT INTO " + table.name()`) ; `ReferentialAdminJdbcAdapter.java:201` + `ReferentialAdminTables.java:22,29,37` ; `SeanceRepositoryAdapter.java:84,91` (Criteria sur `PatientJpaEntity`) ; `PatientJpaEntity.java:13`, `AssureJpaEntity.java:13`, `AssurePatientJpaEntity.java:10` (`@Table`) via `AssureMigrator`/`AffectationMigrator`/`PatientMigrator` ; affirmations : `slices/patient-couverture-pc5-lectures-publiees.md:24`, `TableDuPatientCouvertureTest.java:21-23`, ADR 0079:25 | Ajouter à la garde une deuxième détection (`@Table(name = "<t>")` et littéraux `"<t>"`), inscrire ces sources comme jalons datés (raison : migration / référentiel / JPA Séance), corriger l'ADR 0079 et la fiche (« inventaire des accès SQL littéraux », plus « exhaustif ») |
| major | tactique/langage | **Module déclaré « clos » mais 16 sources gelées non compilées restent dans `domain`, `application`, `interfaces/http/dto` et `publishedlanguage`**. Ce sont des synonymes du modèle vivant (`EtatPatient`/`EtatDuPatient`, `MouvementType` avec `SORTIE` hors vocabulaire/`TypeDeMouvement`, `MouvementPatient`/`MouvementDuPatient`), et un type publié (`PatientAdmis`) n'est pas compilé. | `contexts/patient-couverture/pom.xml:99-107` (excludes) ; `domain/EtatPatient.java`, `domain/MouvementType.java`, `application/PatientService.java` ; `ReglesDeGestionDocumentationTest.java:78-80` ; `GEL.md:3` (« entièrement repris ») | Supprimer les 16 fichiers et leurs `excludes` (code neuf, pas de seam requis) ; retirer l'exclusion de `ReglesDeGestionDocumentationTest` |
| minor | docs             | ADR 0076 (PC1) encore `proposed` alors que 0077–0079 sont `accepted` et PC1 est `built`                                                                                                                                                                                                                                                                                                       | `adr/0076-…md:2`                                                                                                                                                                                                                          | Faire accepter (ou motiver) par l'utilisateur                                                                                             |
| minor | docs             | En-têtes périmés : `tactical/patient-couverture.md` `proposed` ; filets PC1–PC4 « characterized (… rien de commité) » alors que tout est commité ; `GEL.md:22` dit encore la garde `@Disabled`                                                                                                                                                                                                | `safety-net/patient-couverture-pc1..pc4-*.md:2`, `GEL.md:22`                                                                                                                                                                              | Mettre à jour les en-têtes                                                                                                                |
| minor | stratégique      | Glossaire `validated` mais 23 lignes `proposed`, y compris les termes cœur (Patient, Admission, Assuré, PEC, Forfait de PEC) et ceux de PC5                                                                                                                                                                                                                                                   | `strategic/glossaries/patient-couverture.md:11-18,44-48`                                                                                                                                                                                  | Les faire valider par les experts métier                                                                                                  |
| minor | tactique         | Prédicat d'effectif et états de sortie **dupliqués en littéral** : risque de dérive avec `EffectifSql`/`FinOccupation`, qui restent utilisés par les jalons Direction                                                                                                                                                                                                                         | `CompteursDeCouvertureJdbc.java:17-18,73-81` ; `FichesPublieesJdbc.java:20` ; `FinOccupation.java:28,33`                                                                                                                                  | (suggestion) test d'égalité des deux prédicats ou constante unique côté module                                                            |
| minor | tactique         | Langage publié : `String statut` brut (primitive obsession) ; `situation` et `pecValidee` publiés sans consommateur                                                                                                                                                                                                                                                                           | `publishedlanguage/CompteursDeCouverture.java` (`prisesEnCharge`) ; `FichesDesPatients.situation`, `CouvertureDesPatients.pecValidee`                                                                                                     | (suggestion) enum `StatutPec` publié ; garder les méthodes, mais tracer le consommateur attendu                                           |
| minor | métier           | Le tableau de bord utilise deux « aujourd'hui » dans une même réponse : le seuil est au jour du centre, l'effectif au jour d'UTC (reproduit, ADR 0079 §2)                                                                                                                                                                                                                                     | `DashboardRestController.java:38,67` vs `:130`                                                                                                                                                                                            | À confirmer avec les métiers                                                                                                              |
| minor | prod             | §44-3 annonce un comptage « par étage de refus au jour » mais ne filtre sur aucune date ; doublons §42-11 = §43-7 et §42-12 = §43-8 ; numérotation de §42 désordonnée (11, 12, 10) ; le fuseau, « premier contrôle », est placé après §41–§42 ; pas de liste consolidée                                                                                                                       | `verifications-production.md:1807-1811,1757-1761,1768`                                                                                                                                                                                    | Adopter la liste ordonnée ci-dessous                                                                                                      |
| minor | tests            | Tests de caractérisation qui dépendent de l'horloge murale (sautés par `assumeTrue` vers minuit) ; `OffsetDateTime.now` dans les doubles et dans un adapter                                                                                                                                                                                                                                   | `AssurePrincipalCharacterizationIntegrationTest.java:112,138-142` ; `CouvertureCharacterizationSupport.java:156` ; `JourDuCentreCharacterizationIntegrationTest.java:40-55` ; `Doubles.java:279,325` ; `AssuresJdbc.java:100,133,150`     | (suggestion) injecter `Clock` dans `AssuresJdbc` ; compter les tests sautés en CI                                                         |
| minor | tests            | Les tests d'Absences et de Facturation instancient l'adapter **interne** `CouvertureDesPatientsJdbc`/`FichesPublieesJdbc` d'un autre contexte (couplage de test, choisi pour prouver la parité de bout en bout)                                                                                                                                                                               | `ForfaitsDesPatientsJdbcTest` ; `LecturesDuSuiviJdbcContrat` ; `SeancesJdbcContrat`                                                                                                                                                       | Accepté ; à remplacer par le double publié après la période d'observation                                                                 |
| nit   | docs             | KB-PAT-46 cite encore `SeanceBillingEligibilityJdbcAdapter` (supprimé)                                                                                                                                                                                                                                                                                                                        | `known-bugs.md:1428`                                                                                                                                                                                                                      | Renvoyer à `SeanceBillingEligibilityParLaCouverture`                                                                                      |
| nit   | hygiène          | Dossiers ignorés laissés sur disque : `backend/bc-insurance/`, `backend/bc-pec/` (target), et un fichier de cookie d'authentification `backend/tmp-auth-cookie.txt` (ignoré, `.gitignore:28`)                                                                                                                                                                                                 | `git status --ignored`                                                                                                                                                                                                                    | Supprimer localement (le cookie surtout)                                                                                                  |
| nit   | archi            | Paramètre en nom pleinement qualifié dans `FacturationContexteConfiguration.java:128-129` ; imports désordonnés dans `AbsencesPatientsContexteConfiguration`                                                                                                                                                                                                                                  | —                                                                                                                                                                                                                                         | Cosmétique                                                                                                                                |

Points vérifiés sans constat : `seamAllowed` vide, 103 entrées `covered`, 0 entrée morte, seams PC1–PC5 fermés ; aucun
`.received`/`.bak`/`.tmp` suivi par git ni sous `characterization/patientcouverture` ; domaine sans Spring/ORM/SQL, sans
horloge ni aléa (`HorlogeDesCentres` et identifiants injectés) ; liste blanche : 18 entrées, toutes existantes, datées
2026-11-04, motivées, avec échéance ; un nouveau lecteur SQL littéral ou une entrée morte fait échouer le test
(`TableDuPatientCouvertureTest.java:131-152`, testé sur deux lignes `:163-194`) ; documentation client régénérée dans
692091b8 (fichiers binaires non relus).

## Dette restante (fichier:ligne)

- Jalons de la liste blanche (18) : `TableDuPatientCouvertureTest.java:47-68`. Hors liste (voir le constat major 1) :
  `HistoricalRecordJdbcAdapter.java:30-32,102,118` (écrit hors verrou du centre, horloge JVM),
  `ReferentialAdminJdbcAdapter.java:201`, `SeanceRepositoryAdapter.java:84,91`, migrateurs JPA → `bc-assure`
  (`AssureMigrator`, `AffectationMigrator`, `PatientMigrator`, `MigrationConfig`).
- Types historiques `bc-patient` (`Patient`, `FinOccupation`, VO), `PatientJpaEntity`, `PatientMapper`,
  `PatientRepositoryAdapter` : dans `covered.json:66-86`.
- `JourDuCentreLegacy.java:24` (plateforme ; `organisation.v1` ne publie pas le fuseau).
- `LocalDate.now(UTC)` : `DashboardRestController.java:130`. Code mort : les 16 fichiers gelés (`pom.xml:99-107`) ;
  `contexts/seance/.../PecValidationJdbc` (hors réacteur).

## À confirmer avec les métiers (décisions « déléguées à l'assistant », argent reproduit)

1. Deux règles de « PEC valide pour une séance » : création de séance via `SeanceBillingEligibilityPort` (forfait +
   validée + attestation) contre forfait Absences/Facturation sans statut (KB-PAT-25, KB-PAT-26).
2. Forfait applicable lu **sans regarder le statut** de la PEC (KB-PAT-26, KB-ABS-16).
3. Attestation exigée **même pour un vacancier** pour facturer (KB-PAT-46).
4. Aucun contrôle de période, de forfait ni de chevauchement de PEC (KB-PAT-24) ; attestations : bornes « fin avant
   début » seules, doublons et chevauchements acceptés (KB-PAT-41).
5. Compteurs « à échéance » qui incluent les lignes déjà échues (KB-PAT-47) ; deux jours de référence dans le tableau de
   bord.
6. Suppression sous PEC validée (KB-PAT-40 « corrigé en partie ») ; `pieces_jointes_json` (KB-PAT-19) ; assuré tiers
   (KB-PAT-15), formats (KB-PAT-10).
7. Validation des termes `proposed` du glossaire ; acceptation de l'ADR 0076.

## Contrôles de production — liste ORDONNÉE (SELECT seulement, avant la mise en production)

**A. Prérequis bloquants**

1. Flyway : `SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 10;` →
   aucune ligne `success = false`, dernière version égale à celle de la recette (PC1–PC5 n'ajoutent aucune migration) ;
   vérifier aussi §39-1.
2. Fuseaux : §43-0 (`SHOW timezone`, `centers.fuseau_horaire` nul, vide ou illisible, fuseau de la JVM `TZ`/
   `-Duser.timezone`). Lire tous les contrôles en `current_date` avec ce fuseau.
3. Doublons de numéro d'assurance par centre : §41-1.
4. Doubles assurés principaux actifs : §42-1 (= §38-3).
5. États hors liste fermée : §41-2 ; sorties et séjours sans date : §41-3 ; naissance incohérente : §41-4.
6. Statuts de PEC hors cycle : §42-7.
7. Orphelins : PEC (§42-6, 3 requêtes), attestations (§43-1), forfaits absents ou d'un autre centre (§42-9 et §44-4) ;
   périodes d'attestation inversées (§43-2).
8. Centres sans ligne `planning_parametres` : §42-11 (= §43-7).
9. Comptes ADMIN actifs sans centre : §42-12 (= §43-8) → prévenir ces utilisateurs.
10. Procédure : import et rollback de migration (`HistoricalRecordJdbcAdapter`, `MigrationRollbackJdbcAdapter`,
    migrateurs d'assurés) **hors activité du centre**, puis rejouer 4, §42-2, §43-3 et §43-4.

**B. Volumétrie à annoncer aux équipes (non bloquant)**

11. §41-5 à §41-8 ; §42-2 à §42-5 ; §42-8 (PEC validées multiples, chevauchements) ; §42-10 ; §43-3 à §43-6 ; §38-5.

**C. Parité (avant ET après déploiement, même fuseau de session)**

12. §44-1 (compteurs), §44-2 (effectif ; le code prend le jour d'UTC, la requête `current_date` : session en UTC), §44-3
    (ajouter le filtre de date du jour avant de s'y fier), §44-4.

**D. Après déploiement**

13. Recette de la garde de centre : 403 inter-centres sur `/api/v1/patients/**`, `/api/v1/pec/**` et les attestations ;
    tableau de bord d'un centre au fuseau non UTC autour de minuit.

## États proposés (à poser par l'utilisateur, non modifiés ici)

| Slice / contexte                                | État actuel          | Proposé                                                                                                                    |
|-------------------------------------------------|----------------------|----------------------------------------------------------------------------------------------------------------------------|
| PC1 identite-mouvements                         | built                | `verified` après acceptation de l'ADR 0076                                                                                 |
| PC2 assure-principal, PC3 pec, PC4 attestations | built                | `verified` (revues pass-with-actions, parité et seams fermés)                                                              |
| PC5 lectures-publiees                           | built (auto-attesté) | **`built` confirmé** ; `verified` après les majors 1 et 2 (corrections de documentation et de garde ; parité déjà prouvée) |
| contexte patient-couverture                     | architected          | inchangé tant que les 13 contrôles de production et les points métier 1 à 7 ne sont pas faits                              |

## Verdict : **pass-with-actions** (PC5 et contexte). 0 blocker · 2 major · 10 minor · 3 nit.

Top 3 actions :

1. Étendre `TableDuPatientCouvertureTest` au JPA (`@Table`) et aux noms de tables en littéraux ; inscrire
   `HistoricalRecordJdbcAdapter`, `ReferentialAdminJdbcAdapter`, `SeanceRepositoryAdapter` et les adapters JPA
   d'assurés ; corriger « exhaustif » dans l'ADR 0079 et la fiche PC5.
2. Supprimer les 16 sources gelées non compilées et leurs `excludes` (`pom.xml:99-107`) ; mettre à jour `GEL.md:22`, les
   en-têtes des filets et du modèle tactique, et accepter l'ADR 0076.
3. Faire exécuter la liste ordonnée A→D ci-dessus et trancher les 7 points métier (argent) avec les experts.
