---
status: accepted
date: 2026-11-10
review-by: 2026-12-15
decision-makers: [utilisateur (« à confirmer <= 2026-12-15 »), architecte]
context-scope: platform, bc-patient, bc-assure, patient-couverture
related: ADR 0024, 0073, 0077, 0078, 0079 ; inventaire `.ddd/migration/reviews/inventaire-bc-patient-bc-assure.md`
---

# 0133 - Retrait de bc-patient / bc-assure : plan S1-S6

Refactoring sur place (ADR 0024) : mêmes tables, mêmes URLs, pas de Strangler. Aucun golden modifié.

## Contexte
Les modules `backend/bc-patient` et `backend/bc-assure` sont des reliquats du modèle patient historique. Aucun utilisateur dans `backend/contexts` ; seul `backend/platform` les consomme (migration historique, adapters/mapper JPA, 4 lecteurs de `Patient`). L'ADR 0079 les disait utilisés par Séance, FHIR, KDIGO : périmé.

## Décision
Retirer les deux modules en 6 slices, chacune un comportement, livrée et vérifiée seule.

| # | Slice | Risque | Statut |
|---|---|---|---|
| S1 | Supprimer le calcul des mouvements mort de bc-patient (5 classes, 2 tests) | faible | **faite** (2026-11-10) |
| S2 | Notification de saisie infirmière : nom/prénom via `FichesDesPatients` | faible | **faite** (2026-11-10) |
| S3 | Cahier de dialyse : identité/assurance via le langage publié | moyen | **faite** (2026-11-10) |
| S4 | PDF greffe (`GreffePdfService`) : idem | moyen | **faite** (2026-11-10) |
| S5a | ACL Séance `FichesFournieLegacy` : `parIdentifiant`, `parCode`, `parNumeroDAssurance`, `tous` depuis `FichesDesPatients` | moyen | **faite** (2026-11-10) |
| S5b | ACL Séance `fiche` (carte + médecin traitant + place publiée) ; classe renommée `FichesFourniesParPatientCouverture` | moyen | **faite** (2026-11-11) |
| S6 | Outil de migration historique réécrit dans `patient-couverture`, puis retrait des adapters/entités/mapper JPA et des deux modules | élevé | **faite** (S6a, S6b-1 à 3, S6c : 2026-11-11) ; **plan S1-S6 CLOS** |

S2-S5 sont indépendantes. Critère de retrait final : inventaire §7 (0 import `domain.(patient|assure).`, plus d'entités JPA patient/assuré, poms nettoyés, build complet vert).

## Décision utilisateur
La migration historique reste utilisée en production : S6 est une **réécriture** dans `patient-couverture` (cas d'usage `AdmettreUnPatient` / `GererLesAssures` ou SQL du contexte), pas une suppression. **À confirmer <= 2026-12-15** (verrou du centre ou SQL direct hors activité, ADR 0077 pt 12).

## S1 : preuve
- 0 référence Java hors bc-patient aux 5 classes (seules mentions : commentaires dans `PredicatDEffectif`, tests de caractérisation, script pg16, golden `type_inconnu.legacy.json` : texte, pas de dépendance).
- Équivalents dans `contexts/patient-couverture` : `PatientTest` (date requise l.117-123, avant admission l.125-130, `dernierJourOccupe` l.364-371, type de mouvement par état l.168-173, reprise l.186, séjour l.229).
- `Patient.libererPlacement` (bc-patient) n'a plus d'appelant ; sa libération vit dans `contexts/planification` (P7). À retirer avec F2/S6.

## S2 : preuve
- `SaisieInfirmierNotifier` n'utilisait que `PatientRepositoryPort.findById(patient, centre)` pour `getNom()` / `getPrenom()` ; il lit maintenant `FichesDesPatients.noms(centre, [patient])` (même table, même borne de centre, patient absent ou d'un autre centre = pas de nom, valeurs nulles conservées). Aucun ajout au langage publié, bean `fichesDesPatients` déjà câblé, aucune dépendance Maven nouvelle.
- Test `NotificationServiceSaisieInfirmierTest` adapté d'abord (rouge de compilation vu), puis vert.
- Décision utilisateur : `SaisieInfirmierNotifier.java` inscrit dans `.ddd/safety-net/covered.json` (filet = ce test) pour lever le hook ; `seamAllowed` reste vide.

## S3 : preuve
- `CahierDialyseReportService` n'utilisait que `PatientRepositoryPort.findById(patient, centre)` pour `getPrenom/getNom/getCodePatient/getSexe/getDateNaissance/getGroupeSanguin/getNumeroAssurance/getTelMobile` (ligne d'identité du PDF). Aucun équivalent publié ne portait code, groupe sanguin et mobile : ajout ADDITIF `FichesDesPatients.carte(centre, patient)` -> `CarteDuPatient` (`FichesPublieesJdbc` : une requête sur `patients` bornée au centre ; double en mémoire ; test de contrat commun double + H2). Patient absent ou d'un autre centre : vide, donc `PATIENT_INTROUVABLE` comme avant.
- Paramètres Jasper et jrxml inchangés (`PATIENT_ID`, `PATIENT_LIGNE`, dates, `EDITE_*`) : le PDF est identique. Seul écart de bord : un numéro d'assurance vide en base levait `IllegalArgumentException` au chargement, il est maintenant omis de la ligne.
- Tests : `CahierDialyseReportServiceTest` réécrit d'abord (rouge de compilation vu), puis vert ; le double anonyme de `IsolementDesPatientsParLaPlanificationTest` reçoit la nouvelle méthode.

## S4 : preuve
- `GreffePdfService` n'utilisait que `PatientRepositoryPort.findById(patient, centre)` pour `getNom/getPrenom/getCodePatient/getGroupeSanguin` (ligne « Patient : ... » du PDF). Tous portés par `FichesDesPatients.carte(centre, patient)` (S3) : aucun ajout au langage publié, aucune dépendance Maven nouvelle. Patient absent ou d'un autre centre : vide, ligne omise comme avant (le 404 reste porté par `DossierDeGreffePublie.duPatient`).
- Aucun test n'existait : `GreffePdfServiceTest` ajouté d'abord (rouge de compilation vu : constructeur sur `PatientRepositoryPort`), puis vert ; il capture le HTML décoré (ligne patient, groupe sanguin absent = `-`, carte absente = pas de ligne). HTML et PDF identiques.

## S5 : découpée (décision) et S5a : preuve
- Décision : S5 est découpée en S5a (4 recherches/références) et S5b (`fiche`, qui joint planification et générateur). `FichesFournieLegacy` est hybride jusqu'à S5b : S5a lit le publié, `fiche` garde `PatientRepositoryPort`.
- Sémantique legacy relevée (`PatientRepositoryAdapter` + `PatientJpaRepository`, requêtes dérivées sur `patients`) : bornée au centre ; `parCode` et `parNumeroDAssurance` = égalité EXACTE (casse et espaces comptent ; la clé de cache met le numéro en minuscules mais la requête reste exacte) ; `findAllByCenter` sans ORDER BY (ordre indéfini, `ResoudreLePatientScanne` prend le premier correspondant) ; numéro d'assurance de l'assuré = colonne `assure_numero_assurance` (propre numéro si assuré lui-même, numéro du tiers sinon). Le `CodeScanne` fournit déjà le jeton en majuscules et jamais nul.
- Ajouts ADDITIFS à `FichesDesPatients` : `parCode`, `parNumeroDAssurance`, `references` + record `ReferencesDuPatient` (`FichesPublieesJdbc`, SQL borné au centre, `ORDER BY id` : ordre hexadécimal de l'UUID, premier retenu). `parIdentifiant` réutilise `existe` (aucun ajout). Contrat commun double + H2 (+ PG16 hérité de `LecturesPublieesJdbcContrat`, non rejoué ici : base jetable requise) : rouge de compilation vu d'abord, puis vert (19 tests chacun).
- Test d'ACL `FichesFournieLegacyTest` ajouté d'abord (rouge de compilation vu : constructeur), puis vert ; il vérifie aussi que `fiche` reste lue dans le legacy.
- Écarts de bord, tous sur données hors invariants (le legacy y lève) : (1) deux patients du même centre au même code ou numéro : le legacy lève `IncorrectResultSizeDataAccessException`, le publié rend le premier par identifiant (aucune contrainte unique en base) ; (2) une ligne dont `type_patient` est inconnu ou le numéro d'assurance vide : le legacy lève au mapping (y compris dans `tous`), le publié la lit telle quelle ; (3) `tous` : l'ordre devient déterministe (identifiant croissant) au lieu d'indéfini. Aucun golden modifié.

## S5b : preuve (S5 close)
- Sources par champ de `FichesFournies.Fiche` : code, nom, prénom, sexe, naissance, assurance, groupe sanguin, mobile = `FichesDesPatients.carte` (S3) ; `medecinTraitant` = ajout ADDITIF `FichesDesPatients.medecinTraitant(centre, patient)` -> `Optional<UUID>` (colonne `medecin_traitant_id`, borne de centre, vide si patient absent/autre centre/sans médecin ; `FichesPublieesJdbc`, double en mémoire, contrat commun double + H2 : rouge de compilation vu d'abord, puis 20 tests verts chacun) ; `salle`, `position`, `generateur` = `PlacesDesPatients.deLaFiche` de la Planification (propriétaire des colonnes de place, ADR 0052/0079) : lit les mêmes colonnes `salle_id`/`position_id`/`generateur_id` sans filtre, place vide = trois nuls comme avant.
- `generateurNom`, `generateurMarque`, `generateurEtat` : le legacy ne les a JAMAIS fournis (`PatientMapper` ne renseigne que `generateurId` ; setters sans appelant) : la fiche portait trois nuls, puis la Séance précise par la GMAO (`Generateurs.preciser`, `GenerateursDesPatientsJdbc`). Aucune lecture GMAO dans l'ACL, donc aucune sémantique à aligner ; la fiche garde trois nuls (test dédié).
- `FichesFournieLegacy` supprimée, remplacée par `FichesFourniesParPatientCouverture` (`FichesDesPatients` + `PlacesDesPatients`, plus de `PatientRepositoryPort`) ; test d'ACL rouge de compilation vu d'abord, 9 tests verts. Aucune dépendance Maven nouvelle, aucun golden modifié.
- Écarts de bord (données hors invariants, le legacy y levait) : type de patient inconnu ou numéro d'assurance vide au mapping legacy : la fiche est maintenant rendue telle que lue (déjà relevé en S3/S5a).

## S6 : avancement (ADR 0134)
- S6a (filet, 18 goldens) : faite (2026-11-11).
- S6b-1 assurés : faite (2026-11-11). `AssureMigrator` écrit par `RepriseDesAssures` (langage publié, cas d'usage `ReprendreLesAssures`) ; `AssureRepositoryAdapter`, `AssureJpaRepository`, `AssureJpaEntity` retirés ; goldens inchangés. Aucun bug KB corrigé (leur code est en S6b-3).
- S6b-2 patients : faite (2026-11-11). `PatientMigrator` écrit par `RepriseDesPatients` (langage publié, cas d'usage `ReprendreLesPatients`, port `PatientsDeLaReprise`, adapter JDBC) ; plus aucun import `domain.patient.*` / `domain.assure.*` dans ce migrateur ; 17 goldens inchangés sur 18, KB-S6a-3 CORRIGÉ (golden `…_nouveau_code`, ancien archivé). Les colonnes de place sont écrites par la reprise (à confirmer, ADR 0134).
- KB-S6a-2 CORRIGÉ (2026-11-11, après S6b-3) : colonne additive `migration_id_map.etat_origine` (V57), golden d'annulation `…_nouveau_code`, voir ADR 0134. Le prérequis « lever le NEEDS_APPROVAL » de S6c est levé.
- S6b-3 affectations / soldes / annulation : faite (2026-11-11), KB-S6a-2 ensuite (ligne ci-dessus). `AffectationMigrator` écrit par `RepriseDesAffectations` (cas d'usage `ReprendreLesAffectations`), `OpeningBalanceMigrator` lit par `RepriseDesPatients.trouver` ; `MigrationConfig`, `MigrationApplicationService.PATIENT_CACHES` (réduit aux caches encore produits) et `InMemoryMigrationPorts` n'importent plus rien de `domain.patient.*` / `domain.assure.*` (`MigrationRollbackJdbcAdapter` n'en importait déjà rien). CORRIGÉS : KB-S6a-1 (rejeu d'une principale) et KB-S6a-5 (assuré sans casse, numéro canonique) ; golden `affectations_controles_import_et_rejeu_nouveau_code` (ancien archivé), 17 autres goldens inchangés. **KB-S6a-2 (restauration d'un assuré rapproché) : NEEDS_APPROVAL**, il faut modifier `IdMapping.java`, `IdMappingJdbcAdapter.java`, `MigrationRollbackPort.java` (hors `covered.json`) + colonne additive `migration_id_map.etat_origine` (ADR 0134). Retirés : `PatientRepositoryAdapter`, `AssurePatientRepositoryAdapter`, `PatientMapper`, `PatientJpaRepository`, `AssurePatientJpaRepository`, `AssurePatientJpaEntity` + 2 tests.
- **Reste pour S6c (retrait des modules, après revue et après KB-S6a-2)** : (1) `platform/.../persistence/entity/PatientJpaEntity.java` (aucun utilisateur ; gardée seulement car Hibernate crée la table `patients` du H2 de test : écrire d'abord `CREATE TABLE patients` dans `schema.sql`, vérifier les largeurs de colonnes avec les goldens avant/après, puis la retirer, avec l'entrée de `TableDuPatientCouvertureTest` et son usage ligne ~290) ; (2) aucun import `domain.patient.*` / `domain.assure.*` ne reste hors de `bc-patient` et `bc-assure` (Grep) : supprimer les dossiers `backend/bc-patient` et `backend/bc-assure` (dont `PatientContactValidationTest`), les `<module>` et `dependencyManagement` de `backend/pom.xml` (l. 28-29, 58-63), les dépendances de `backend/platform/pom.xml` (l. 21, 25), les deux `COPY` de `backend/Dockerfile` (l. 14-15), `bc-patient bc-assure` des boucles de `backend/scripts/pit-f3-validation.sh` (l. 15) et `pit-c3-ecritures.sh` (l. 16) ; (3) `CacheConfig` : noms de caches sans producteur (`patient.byId`, `patient.byNumeroAssurance`, `patient.byCenter`, `patient.countByCenter`, `patient.assure.*`, `patient.assignment.*`, `patient.pec.byId`, `patient.attestation.existsValidAt`) et `CachesDesPatientsLegacy` (ACL Planification qui les évince) ; (4) `.ddd/safety-net/covered.json` : 14 entrées `backend/bc-patient/...` (l. 46-65) à retirer avec les fichiers ; (5) commentaires de doc citant `bc-patient` / `bc-assure` (`.ddd/strategic/*`, glossaire, `TableDuPatientCouvertureTest`) ; (6) jalons `TableDuPatientCouvertureTest` restants à relire (`HistoricalRecordJdbcAdapter`, `MigrationRollbackJdbcAdapter`, `ReferentialAdminTables`, SQL de Séance) : hors périmètre du retrait des modules.

## S6c : faite (2026-11-11), plan S1-S6 CLOS
- `CREATE TABLE IF NOT EXISTS patients` écrit dans `platform/src/main/resources/db/schema.sql` (H2 seulement, la production reste Flyway). Comparaison `information_schema.columns` + contraintes AVANT (Hibernate depuis `PatientJpaEntity`) / APRÈS (schema.sql) : 60 colonnes, types, largeurs (VARCHAR(255) ; TEXT = VARCHAR(1000000000)), nullités (id, center_id, nom, prenom, sexe, date_admission, numero_assurance, type_patient NOT NULL), défauts (aucun), ordinaux : IDENTIQUES ; une seule clé primaire ; aucun index ni FK. Aucun golden modifié.
- Supprimés : `PatientJpaEntity` (+ son jalon de `TableDuPatientCouvertureTest`), `backend/bc-patient`, `backend/bc-assure` (`git rm -r`), `<module>` et `dependencyManagement` de `backend/pom.xml`, dépendances de `platform/pom.xml`, deux `COPY` du `Dockerfile`, `bc-patient bc-assure` des scripts `pit-f3-validation.sh` / `pit-c3-ecritures.sh`, 18 entrées périmées de `covered.json`.
- `CachesDesPatientsLegacy` est CONSERVÉE (elle implémente le port `CachesDesPatients` de la Planification et évince les caches vivants `patient.list.summary` et `.details`) ; ses éviction de `patient.byId`, `byCenter`, `countByCenter`, `byNumeroAssurance` sont retirées. `CacheConfig` : suppression de 9 noms sans producteur (`patient.byId`, `byNumeroAssurance`, `byCenter`, `countByCenter`, `assure.byNumero`, `assure.searchByCenter`, `assignment.primary`, `assignment.history`, `pec.byId`), du `CaffeineCache` à valeurs nulles et du TTL `patient-count`.
- Critère de retrait (inventaire §7) : 0 import `domain.(patient|assure).` ; plus d'entité JPA patient/assuré ; poms nettoyés ; reactor `validate` sans les deux modules ; `install -DskipTests` vert ; goldens et gardes (dont `FrontieresDesContextesTest`) verts.
- Constat hors périmètre : en ordre aléatoire (`-Dsurefire.runOrder.random.seed=12709620220600`, caractérisation + architecture), `ControleDesPeremptionsCharacterizationIntegrationTest` échoue (rejeu de `seed.sql` sur le H2 partagé, violation de clé primaire sur `factures`) ; identique sur HEAD avant S6c (worktree), donc non causé par S6c.
- Cause PROBABLE (lecture du test, de `application.yml:20-26`, `application-test.yml:3` et de `seed.sql:2289-2359`, NON confirmée par exécution) : le test déclare une `@TestConfiguration` (horloge fixe, écouteur) donc obtient son PROPRE contexte Spring, qui rejoue `seed.sql` (`spring.sql.init.mode: always`) sur le H2 `jdbc:h2:mem:hemodialyse;DB_CLOSE_DELAY=-1` PARTAGÉ avec les contextes précédents de la même JVM. L'insertion des `factures` du seed n'est gardée que par `NOT EXISTS` sur `(center_id, numero_facture)`, alors que l'`id` (`b101`/`b201` + rang `rn`) et le numéro dépendent du rang `rn` calculé sur les séances `FACTUREE` présentes À CE MOMENT-LÀ : si des classes précédentes ont ajouté ou retiré des séances/patients, le rang décale, un numéro nouveau reçoit un `id` déjà pris et la clé primaire saute. Cela dépend donc de l'ordre des classes (d'où la graine). Correction possible, hors périmètre ici : garder aussi sur l'`id` dans le seed, ou donner à ces tests une base H2 nommée distincte.
- Résiduel assumé (revue du 2026-11-11, constat 6) : `parCode` et `parNumeroDAssurance` (S5a) rendent le PREMIER par `ORDER BY id` en cas de doublon (code patient ou numéro d'assurance en double au centre), alors que le legacy (`Optional` JPA) LEVAIT `IncorrectResultSizeDataAccessException`. Aucun test ne verrouille ce cas (la donnée est hors invariant : aucune contrainte d'unicité sur `numero_assurance` ni `code_patient` dans `db/migration` ni `schema.sql`, Grep du 2026-11-11) ; à reprendre si une contrainte d'unicité est ajoutée.

## Conséquences
- 251 LOC main et 213 LOC test en moins, aucun changement de comportement.
- Les références documentaires à `FinOccupation` / `MouvementsPatient` restent comme historique.
