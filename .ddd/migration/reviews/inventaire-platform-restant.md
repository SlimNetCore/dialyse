---
status: draft
date: 2026-11-11
sources:
  - .ddd/config.json (sliceBudget : 5 points d'entrée, 15 fichiers, 1500 lignes)
  - .ddd/strategic/bounded-contexts.md, .ddd/migration/roadmap.md
  - ADR 0024, 0070, 0094-0100, 0102-0106, 0111, 0121-0134
  - mesures : find/wc/grep sur backend/platform/src/main/java (lecture seule, aucun scan.ts)
---

# Inventaire de ce qui reste dans `backend/platform` (au 2026-11-11)

Périmètre : `backend/platform/src/main/java/com/hemodialyse/backend/{application,infrastructure}` = **290 fichiers, 26 300 lignes**
(22 contrôleurs REST, 4 tâches `@Scheduled`). Aucune modification faite. Les comptes de lignes sont des `wc -l` ; les groupes
« ~ » sont des sommes arrondies.

## 1. Résumé

| Catégorie | Fichiers | Lignes | Lecture |
|---|---|---|---|
| A. Plateforme voulue (transverse technique) | ~150 | ~13 000 | Identité/MFA/sécurité, licence, audit, supervision BD, config, WebSocket, notifications, moteur de documents, outil de reprise, ACL d'intégration. Peu de décisions écrites : seul `bounded-contexts.md:131-132` nomme les génériques ; le reste est « non documenté ». |
| B. À migrer (métier ou propriété d'un contexte) | ~95 | ~9 500 | Presque tout est déjà une fine couche au-dessus de langages publiés (peu de logique résiduelle) : rendus PDF/Excel, statistiques, export greffe, lectures Direction, admin générique de référentiels, catalogue de notifications. |
| C. Mort / à décommissionner | 30 + 43 + 10 | ~2 000 | 28 fichiers JPA sans consommateur (**mais ils génèrent encore des tables du H2**), 2 DTO, 2 `GEL.md`, 43 `.received.json`, 10 `.log` non suivis. `covered.json` : 0 entrée périmée. |

Alerte calendrier : au moins 5 gardes d'architecture portent `REVUE = 2026-12-15` et échouent après cette date si on ne la repousse pas
(`TableDuPilotageTest.java:36`, `TableDesSeancesTest.java:37`, `EcrivainsDeLaFactureDesSeancesTest.java:37`, `TableDuDossierMedicalTest.java:30`,
`TableDesAnalysesTest.java:31` ; ADR 0105 §5 le dit explicitement pour le Pilotage). Soit **34 jours** : décision à prendre avant tout.

## 2. A. Plateforme voulue

Décision écrite générale : `bounded-contexts.md:131-132` (« Génériques : Identité & accès, Notifications, Audit, Licence. Adoptés derrière des ports ; ne
portent aucune règle métier ») et `bounded-contexts.md:33` (outil de reprise hors contexte). ADR 0024 (§2, contrôleurs soumis à la chaîne sécurité/licence/rôle/audit).

| # | Ensemble | Chemins (package) | Fichiers / lignes | Décision écrite | Remarque |
|---|---|---|---|---|---|
| A1 | Identité & accès : login, MFA (TOTP), setup initial, utilisateurs, rôles, JWT, filtres, garde de centre | `application/auth` (+`mfa`), `application/setup`, `infrastructure/security` (11), `AuthRest/MfaRest/InitialSetupRest/UserRest/RoleRest/SystemRest`, `persistence/spec`, entités/dépôts `AppUser`, `AppRole`, DTO user/rôle | ~42 / ~3 800 | `bounded-contexts.md:131` (SD12) ; MFA et JWT : non documenté | `UserRestController` et `AuthService.java` sont des « jointures avec les comptes » (ADR 0111:60-61, O4-R9/R10). |
| A1b | Comptes de direction et d'administrateur de société, garde d'accès Direction | `application/direction/{DirectionAccountService,SocieteAdminAccountService,PasswordPolicy}`, `SocieteAdminAccountRest`, `SocieteDirectionAccountRest`, `security/DirectionAccessGuard` | 6 / ~800 | ADR 0105:35 (PD5-R6) et 0111:60-62 (O4-R10/R11) : cible Identité et accès, « déplacer, jamais retirer » | Hors `covered.json` hormis `DirectionAccessGuard` (ADR 0105:35). Aucun module IAM n'existe : cible = rester plateforme. |
| A2 | Licence | `application/license` (4), `security/license` (2), `LicenseRestController`, `scheduling/LicenseOnlineCheckJob`, `config/DevLicenseBootstrapInitializer`, entité `License`, 4 DTO | ~14 / ~1 100 | `bounded-contexts.md:132` (SD15) | Tâche 03:15. |
| A3 | Audit asynchrone | `application/audit` (4), `AuditRestController`, `scheduling/AuditScheduler`, `security/AuditRequestFilter` (couvert) | ~8 / ~520 | `bounded-contexts.md:132` (SD14) | Tâches : toutes les 3 s et 03:30. |
| A4 | Supervision de la base de données (pg_stat_statements, plan, santé) | `application/supervision` (+`port`, 15), `persistence/adapter/{PgPlan,PgSante,PgStatStatements}Adapter`, `SupervisionBaseRestController`, `db/supervision` | ~21 / ~1 330 | **non documenté** (ADR 0066 parle d'une autre « supervision ») | Bon exemple de ports/adaptateurs ; à inscrire au glossaire des génériques. |
| A5 | Config, OpenAPI, caches, amorçage dev/démo, garde de production | `infrastructure/config` (8), `openapi`, `seed/StockDemoDataSeeder`, `web/ApiExceptionHandler`, `BackendApplication` | ~13 / ~870 | non documenté (ADR 0111:63 O4-R12 pour `DevSuperAdminBootstrapInitializer`) | `DevSuperAdminBootstrapInitializer.java` ne lit que `SELECT id FROM centers` : `AnnuaireDesCentres.tous()` suffirait (ADR 0111:63). |
| A6 | Transport temps réel (STOMP) | `infrastructure/websocket` (7), `stock/RelaiDesInventairesVersWebSocket` | 8 / ~450 | ADR 0105:19 et :36 (PD5-R7) pour la Direction ; pour le Stock : non documenté | Raison du maintien : les modules n'ont pas de dépendance Spring messaging/WebSocket. |
| A7 | Notifications (centre de notifications) | `application/notification` (3), `NotificationJournalJdbcAdapter`, `NotificationRestController`, `NotificationPurgeScheduler` | 7 / ~1 025 | `bounded-contexts.md:131-132` (SD13 « catalogue RG-NOT ») | **Écart** : `NotificationService.java` (654 l.) contient ~40 méthodes `notifyXxx` nommées par événement métier d'autres contextes (patient, séance, planification, absences, stock, GMAO) : un catalogue métier dans un générique « sans règle ». Voir B8. |
| A8 | Moteur de documents et modèles imprimables (Jasper, modèles versionnés, identité imprimée, logo) | `reporting/{JasperReportService,ModeleDocument*,CustomTemplateCompiler,DocumentIdentityProvider,SocieteLogoService,SocieteLetterheadAdapter,FrenchFormatFactory}`, `reporting/JrxmlSecurityValidator`, `LogoImageValidator`, `DocumentRestController`, `ModeleDocumentTemplateRestController`, entité `ModeleDocumentVersion`, `resources/reports` | ~22 / ~3 000 | ADR 0111:31 (« modèles de documents restent à la plateforme ») ; roadmap §1 G6 (« moteur de documents dans la plateforme ») | **Dette interne** : `DocumentRestController.java:141,156,175,210,284,370` fait du SQL direct sur `modele_document` dans un contrôleur de 495 lignes. |
| A9 | Outil de reprise de l'historique (ex `bc-migration`) | `application/migration`, `infrastructure/migration/{model,port,service}` (25), `persistence/adapter/migration` (6), `MigrationRestController`, `MigrationConfig`, `importer/MigrationTemplateWriter`, DTO | ~36 / ~3 900 | `bounded-contexts.md:33`, ADR 0134 (réécrit sur patient-couverture, 2026-11-11) | KB-S6a-4, 6, 7 non corrigés (voir §8). 17 de ses fichiers sont dans `covered.json`. |
| A10 | ACL d'intégration (traducteurs entre langages publiés et services de plateforme) | `infrastructure/acl/*` (10 sous-dossiers) + `infrastructure/listener` (2) | 42 / ~2 260 | ADR 0008 (legacy entouré d'ACL) ; résiduels datés : ADR 0100:74 (F-R1), dm9 fiche M-R1 | **Voulu à titre transitoire** : 12 beans « `*Legacy` » enrobent `NotificationService`/impressions ; ils disparaissent avec B8 et B1. Faux positifs « 0 référence » : ce sont des `@Component` (voir §9). |

## 3. B. À migrer

Pour chaque ensemble : contexte cible, taille, dépendances, risque, slices (budget : ≤ 5 points d'entrée, ≤ 15 fichiers, ≤ 1 500 lignes). Toutes les slices sont **sur place**
(ADR 0024 : mêmes URL, mêmes tables, filet de caractérisation d'abord ; `guard-legacy` exige l'entrée dans `covered.json`).

| # | Ensemble | Preuve | Cible | Taille | Dépendances | Risque | Filet existant |
|---|---|---|---|---|---|---|---|
| B1 | Rapports métier rendus par la plateforme | `CahierDialyseReportService.java:1-109`, `BonInterventionReportService` 212, `InventaireReportService` 210 + `FeuilleComptageExcel` 329, `FacturationSyntheseMensuelleReportService` 387 + `CaissesDesAgences` 15, `DirectionReportPdfService` 415 + `SocieteLetterheadPort` | Séance / GMAO / Stock / Facturation / Pilotage | 11 fichiers + 5 `.jrxml` / ~1 750 | moteur A8 (il faut un port « rendu de document » publié par la plateforme, déjà prévu par G6) ; langages publiés existants | moyen (PDF byte-à-byte) ; F-R8 (`cahier_dialyse.jrxml`, butoir 2027-06-30, ADR 0100:81) | tous dans `covered.json` |
| B2 | Statistiques d'un patient (paramédical, médical, export) | `PatientStatsRestController.java:31-109` (6 routes), `PatientStatsQueryService` 293 | Dossier médical (hôte proposé : compose `StatistiquesMedicalesPubliees` et `StatistiquesDesSeancesPubliees`) | 4 fichiers / ~510 | `dossiermedical.publishedlanguage`, `seance.publishedlanguage` | faible à moyen | oui |
| B3 | Export PDF du dossier de greffe | `GreffeExportRestController.java:40` (1 route), `GreffePdfService` 195 | Dossier médical | 2 fichiers / 245 | `DossierDeGreffePublie`, `FichesDesPatients` ; rend `SousLaGardeDuDossierMedical` inutile ailleurs (dm9 M-R5) | faible | oui |
| B4 | Tableau de bord des compteurs patients | `DashboardRestController.java:32,63` (2 routes) | Patient & couverture (`CompteursDeCouverture`) ; `JourDuCentre` (14 l.) + `JourDuCentreLegacy` → Organisation (ADR 0094 R1, calendrier du centre) | 4 fichiers / ~230 | `application/centre/JourDuCentre` | faible | oui |
| B5 | Contrôleurs et lectures de la Direction | `DirectionDashboardRestController` 202 (10 routes), `DirectionSnapshotRestController` 78 (4), `DatesIllisiblesDeLaDirectionAdvice` 183, `DirectionAbsences/Capacite/Gmao/StockGroupesQueryService` (733 l.), `AnonymityPolicy` 26, `ValorisationArticlesService` 41 | Pilotage (contrôleurs, garde `AnonymityPolicy`, PD5-R9) ; lectures → Absences, Planification/GMAO, Stock | 11 fichiers / ~1 270 | `DirectionAccessGuard` (reste A1b) ; langages publiés | moyen (données anonymisées, KB-DIR-01) | oui |
| B6 | Administration générique des référentiels | `ReferentialAdminRestController` (7 routes), `referential/admin` (14), `ReferentialAdminJdbcAdapter` 253 (SQL dynamique), `ReferentialAdminTables` 90, `ReferentialCacheEvictor`, `TabularFileReader` 235, `ReferentialTemplateWriter` | Organisation (salle, créneau, médecin) ; Patient & couverture (descripteurs FORFAIT, CAISSE, AGENCE, CENTRE_PAYEUR, TRANSPORTEUR) ; le lecteur de fichiers reste générique (A) | ~25 fichiers / ~2 100 | 31 goldens `o2-administration` + import ; caches `ref.*` | **élevé** (ADR 0111:33-40) | `ReferentialAdminJdbcAdapter`, `ReferentialAdminTables`, `ReferentialAdminDomainService` couverts |
| B7 | Modèles de documents : SQL hors port | `DocumentRestController.java:141-370` | Plateforme (A8), refactoring interne | 1 fichier (495) à scinder en 3 | `modele_document` (propriété plateforme) | faible | `DocumentRestController` couvert |
| B8 | Catalogue de notifications métier | `NotificationService.java:48-585` (≈ 40 méthodes) + 12 ACL `*Legacy` | chaque contexte émet son événement ; le générique ne garde que le routage/journal (SD13) | 14 fichiers / ~1 350 | `acl/*` (A10), `SimpMessagingTemplate`, `TempsReelDeLaDirection` | moyen (messages, RG-NOT) ; ordre d'événements après commit (`NotificationService.java:644`) | `NotificationService` couvert |

### 3.1 Slices proposées (chiffrées, dans le budget)

| Slice | Contenu | Points d'entrée | Fichiers | Lignes | Risque | Prérequis |
|---|---|---|---|---|---|---|
| **PL0** | Décision calendrier : repousser/renouveler les 5 `REVUE` du 2026-12-15 ou fermer les résiduels (cf. §6) | 0 | 5 tests | ~20 | faible | décision métiers/DPO |
| **PL1** | Décommissionner les entités JPA mortes : (a) 3 entités payeurs + `AssurePatientId` ; (b) entités `Article/Lot/StockMovement/Emplacement/Fournisseur` + 5 dépôts, DDL H2 à écrire dans `schema.sql` ; (c) bons de commande/réception/sortie + lignes + 6 dépôts ; (d) `AppSetting*`, `UserCenterAssignment`. Quatre sous-slices | 0 | (a) 4, (b) 10, (c) 12, (d) 3 | (a) ~250, (b) ~830, (c) ~780, (d) ~170 | moyen : même piège que `PatientJpaEntity` (ADR 0134:143) | `ddl-auto: update` crée les tables du H2 depuis ces entités (`application.yml:12`) |
| **PL2** | B3 Export greffe | 1 | 3 | ~300 | faible | — |
| **PL3** | B4 Dashboard compteurs + `JourDuCentre` | 2 | 5 | ~260 | faible | — |
| **PL4a/b** | B2 Statistiques patient : (a) paramédical + médical (4 routes), (b) export (2 routes) | 4 / 2 | 3 / 2 | ~350 / ~200 | faible–moyen | — |
| **PL5** | B1 Cahier de dialyse (route + service + jrxml) → Séance | 1 | 4 | ~250 | moyen | port de rendu |
| **PL6** | B1 Bon d'intervention → GMAO | 0 (déjà servi) | 3 | ~260 | moyen | port de rendu |
| **PL7** | B1 Inventaire + feuille de comptage → Stock | 0 | 4 | ~600 | moyen | port de rendu |
| **PL8** | B1 Synthèse mensuelle de facturation (+ RF3c-J1) → Facturation | 1 | 4 | ~450 | moyen | RF3c-J1 : lecture publiée de la chaîne des payeurs |
| **PL9** | B1 Rapport PDF Direction → Pilotage | 2 | 4 | ~480 | moyen | PL5 (même port) |
| **PL10a/b/c** | B5 : (a) snapshots (4 routes) ; (b) tableau de bord, 2 slices de 5 routes ; (c) services de lecture vers Absences, Planification, GMAO, Stock | 4 / 5+5 / 0 | 4 / 5 / 8 | ~330 / ~500 / ~800 | moyen | PD5-R1 à R4 ; unifier le seuil d'anonymat (PD5-R9) |
| **PL11a-d** | B6 : (a) descripteurs résiduels FORFAIT/CAISSE/AGENCE/CENTRE_PAYEUR/TRANSPORTEUR → Patient & couverture ; (b) salles + créneaux → Organisation ; (c) médecins → Organisation ; (d) retrait de l'adaptateur SQL dynamique | 5 par sous-slice | 8-14 | 400-900 | **élevé** | filet des 31 goldens o2 ; décision par invariant (ADR 0111:38) ; butoir 2027-03-31 |
| **PL12** | B7 : extraire un port `modele_document` hors du contrôleur | 8 | 5 | ~600 | faible | — |
| **PL13a-d** | B8 : catalogue de notifications par contexte (patient/PEC ; séance ; planification ; absences/stock/GMAO), en retirant les ACL `*Legacy` au fur et à mesure | 0 | 6-10 | 300-500 | moyen | publier `notifications.v1` ; ordre séquentiel (`NotificationService` partagé) |

Ordre conseillé : PL0, PL1a, PL2, PL3, PL4, PL1b-d, PL5-PL9 (une fois le port de rendu posé), PL10, PL13, PL11 en dernier (le plus risqué, butoir 2027-03-31), PL12 en tâche de fond.

## 4. C. Mort ou à décommissionner

| # | Élément | Preuve (existence / références) | Statut git | Action |
|---|---|---|---|---|
| C1 | 28 fichiers JPA sans consommateur (18 entités, 10 dépôts, 1 964 l.) : `Agence/CaisseAssurance/CentrePayeur`, `Article`, `Bon*` (3), `Ligne*` (3), `Lot`, `StockMovement`, `Emplacement`, `Fournisseur`, `AssurePatientId`, `UserCenterAssignment`, `AppSetting` + `AppSettingId`, dépôts `Bon*`, `Ligne*`, `Lot`, `StockMovement`, `Emplacement`, `Fournisseur` | `grep -w` : 0 référence hors d'eux-mêmes ; seul `TableDesPayeursTest` cite les 3 entités payeurs ; les tables sont gérées en JDBC par les contextes (ex. `app_settings` dans `SequenceDesNumerosDePiecesJdbc.java:54`) | suivis | **Ne pas supprimer d'un coup** : `schema.sql` n'a pas de `CREATE TABLE` pour `articles`, `lots`, `bons_*`, `stock_movements`, `emplacements`, `fournisseurs`, `app_settings` (il n'a que des `ALTER`, ex. `schema.sql:1306`) ; Hibernate les crée en H2. Slice PL1 : écrire d'abord le DDL, comme S6c pour `patients`. Les tables payeurs ont déjà leur `CREATE TABLE` (suppression directe). |
| C2 | `PointBiologiqueResponse` (16 l.), `EntityWriteResponse` (15 l.) | 0 référence dans `src/main`, `src/test`, `contexts` | suivis | supprimer (couvre aussi leur absence de `covered.json`) |
| C3 | `backend/contexts/dossier-medical/GEL.md` (périmé), `backend/contexts/patient-couverture/GEL.md` (note historique) | le premier dit « DM2 à DM8 à venir » alors que DM1-DM9 sont `built` et `bc-medical` est retiré (ADR 0101) ; le second se déclare « plus aucune source gelée » | suivis | archiver ou remplacer par l'ADR 0072/0073 (déjà la trace) |
| C4 | 43 fichiers `*.received.json` : 29 dans `backend/src/test/resources/characterization/gmao/plans/` (répertoire `backend/src` qui ne contient que cela) et 14 dans `platform/src/test/resources/characterization/{absences-patients,seance}/` | `find` | **non suivis, ignorés** (`.gitignore:25`) ; `git status` propre | supprimer sur disque ; vérifier pourquoi `backend/src` existe (un test GMAO exécuté depuis `backend/` ?) |
| C5 | 10 journaux : `backend/platform/{backend-startup,startup,startup-final}.log` (302 l.), `backend/{compile-check-copilot,compile-test,patient-gen-test,run-live-err,run-live-out,run-seed-check,tmp-debug}.log` | `find` | non suivis, ignorés (`.gitignore:13`) | supprimer sur disque |
| C6 | `.ddd/safety-net/covered.json` | 127 entrées, 127 vérifiées par `test -e` : **0 fichier absent** (la seule non-correspondance est le motif `infrastructure/acl/seance/*.java`, 3 fichiers présents) | suivi | 5 entrées à retirer **avec PL1** : `ArticleJpaEntity`, `EmplacementJpaEntity`, `FournisseurJpaEntity`, `EmplacementJpaRepository`, `FournisseurJpaRepository` (code mort couvert). Penser à `covered.json` pour les futures slices (PD5-R6 : comptes hors filet) |
| C7 | Modules et dossiers voisins hors périmètre mais suspects : `backend/db/migration-legacy` (39 fichiers suivis) et `backend/shared-kernel` (16 fichiers) en doublon apparent de `platform/resources/db/migration` et de `contexts/shared-kernel` | `git ls-files`, `backend/pom.xml:27-28` | suivis | à trancher (question 7) |

## 5. Couplages et risques

- Les contrôleurs restants sont **minces** : `PatientStatsQueryService`, `GreffePdfService`, `ValorisationArticlesService` et les `Direction*QueryService` n'importent que des langages publiés (`contexts.*.publishedlanguage`), sans SQL (ADR 0105:20). Les seuls accès SQL de `application`/`infrastructure` hors adaptateurs : `DocumentRestController`, `ReferentialAdminJdbcAdapter`, adaptateurs de reprise, supervision, journal des notifications.
- Hotspot de valeur : `NotificationService` (654 l., 15 fichiers l'importent), `DocumentRestController` (495), `AuthService` (443), `DirectionReportPdfService` (415), `JrxmlSecurityValidator` (409, sécurité des modèles).
- Hors `covered.json` (donc modification bloquée) : comptes de direction/société, `AuthService`, `LicenseService`, `Referential*` sauf trois fichiers, `JasperReportService`, `ModeleDocumentCatalog/Printer/TemplateService`, `NotificationRestController`, `SupervisionBase*`. Toute slice qui les touche commence par un filet.

## 6. Résiduels datés (butoir 2027-03-31, revue 2026-12-15)

| Réf. | Résiduel | Cible | Source |
|---|---|---|---|
| PD1-4, PD5-R1 à R9 | lectures Direction, indicateurs, comptes, tracker STOMP, double seuil d'anonymat | Absences, Planification, GMAO, Stock, Pilotage, IAM | ADR 0102:56, 0103:41, 0104:39, 0105:27-38 |
| O4-R9 à R13 | `AuthService`, comptes, garde, `DevSuperAdminBootstrapInitializer`, admin générique | IAM / Organisation | ADR 0111:58-67 |
| RF1-R1, RF2-R1, RF3-R1, RF3c-J1, RF4-R1 | descripteurs de l'admin générique ; gabarit `synthese_mensuelle_facturation.jrxml` lisant `agence`/`caisse_assurance` | Patient & couverture, Facturation | ADR 0122:32, 0124:34, 0126:40, 0128:46, 0130:30 ; ADR 0121:56 (Comptabilité lit `centre_payeur`) |
| F-R1 à F-R7 | quatre fournisseurs de `seance.v1` dans la plateforme (`FichesFournies`, `Diffusions`, `Rappels`, `SaisiesDeSeanceFournies`), lectures transitoires de la Séance | Patient, Notifications | ADR 0100:74-80 |
| F-R8 | `seed.sql`, `renadial-*.sql`, `cahier_dialyse.jrxml` | — | ADR 0100:81, **butoir 2027-06-30** |
| E-R1 à E-R8 | `MigrationRollbackJdbcAdapter` lit `volet_medical`/`volet_paramedical` (E-R6) ; lectures `patients` | Séance, Patient | ADR 0099:76-83 |
| D-R*, C2-R*, SE7a/b/c1 R* | lectures publiées des séances, ordre non défini, jointures `patients` | Séance, Patient | ADR 0098:69-75, 0097:60-66, 0096:36, 0095:55-59, 0094:65-70 |
| M-R1 à M-R8 | deux ACL de notification du Dossier médical ; KB-MED-94 à 110 ; `GardeDePatient` à rétrograder ; `ContraintesDesPatientsJdbc` | Dossier médical, Notifications | `.ddd/migration/slices/dossier-medical-dm9-statistiques-et-lectures-sql.md:62-69` |
| O4-R5/R6/R7/R8 | calendrier du centre, événements d'Organisation jamais émis, PG16, KB-ORG-10 | Organisation | ADR 0111:66 |

## 7. Limites de l'analyse

Statique : les « 0 référence » se basent sur le nom de classe (`grep -w`) ; les beans Spring (`@Component`) et les entités mappées par nom (JPQL) ne sont pas détectés. Le balayage complet des 290 classes (tâche de fond) n'a été exploité que sur son début ; les 28 JPA ont été vérifiés un par un. Le comptage de lignes « ~ » des groupes est arrondi. Pas de vérification dynamique (profil `prod`, DI par configuration, Flyway V1-V57 non rejoués). Les cibles de contexte sont des propositions, pas des décisions.

## 8. KB-S6a-4, 6, 7 (outil de reprise, A9)

| KB | Constat | Preuve | État |
|---|---|---|---|
| KB-S6a-4 | tout mot dont les 3 premières lettres sont un jour est accepté (« Lunch » = LUN) | `LegacyValueParser.java:193` ; golden `patients_formats_invalides_et_assures_manquants` | non corrigé (`known-bugs.md:2195`) |
| KB-S6a-6 | le SUPERADMIN est refusé (403 `ROLE_SCOPE`) sur les routes de reprise ; branche SUPERADMIN de `CenterAccessGuard` inatteignable | golden `droits_et_garde_de_centre` ; `RoleScopeFilter` | non corrigé (`known-bugs.md:2197`) |
| KB-S6a-7 | `GET /batches/{id}` liste plusieurs exécutions si les `executed_at` sont égaux (MAX sans départage) ; non verrouillé | `MigrationBatchJdbcAdapter.java:95-97` | non corrigé, non déterministe (`known-bugs.md:2198`) |

KB-S6a-2 est **corrigé** (2026-11-11, ADR 0134:112). Proposition : slice `PL14` de reprise (3 corrections, ≈ 6 fichiers, ≈ 400 l.) après décision métier sur les trois points.

## 9. Faux positifs de la recherche de code mort

Douze `@Component` portent 0 référence par nom et ne sont **pas** morts : `NotificationsDuCentreLegacy`, `SignalementDesSaisiesLegacy`, `NotificationsDeLAnemieLegacy`, `SignalementDesPrescriptionsLegacy`, `LiaisonDesSeancesParLaSeance`, `SeancesDeFacturationParLaSeance`, `SeancesDesFacturesParLaSeance`, `CapaciteDesSallesDePlanification`, `GmaoEvenementsReformeListener`, `NotificationsDeLaFicheParLaPlateforme`, `AbordsVasculairesParLeDossierMedical`, `AlertesDeLOptimisationLegacy` (et leurs semblables du dossier `acl/planification`), plus `CacheConfig`, `SeedPasswordInitializer`, `SpringTransactionRunner`.

## Questions ouvertes

1. Les gardes datées du 2026-12-15 (5 tests) : repousser (à quelle date ?) ou solder PL10/PL11 avant ? Qui valide la revue des « décisions en vigueur » (métiers, DPO) ?
2. L'administration générique (B6) est-elle un sous-domaine à part (outil d'admin du référentiel) ou doit-elle disparaître au profit d'écrans par contexte ? ADR 0070 option 2 est la seule voie écrite ; décision par invariant à prendre (ADR 0111:38).
3. Notifications (A7/B8) : confirmer que le catalogue de messages passe aux contextes émetteurs, ou accepter un contexte « Notifications » avec son propre catalogue (alors le déplacer dans `backend/contexts`) ?
4. Hôte des statistiques patient (B2) : Dossier médical (hypothèse) ou Séance ? Elles composent les deux langages publiés.
5. Moteur de documents (A8) : sous-domaine générique durable (port de rendu publié) ou à éclater par contexte ? Conditionne PL5-PL9.
6. Supervision de la base (A4) : générique voulu ? À consigner dans un ADR (aucun écrit aujourd'hui) ; idem MFA et JWT.
7. `backend/db/migration-legacy` (39 fichiers) et `backend/shared-kernel` (16 fichiers, listé avant `contexts/shared-kernel` dans `backend/pom.xml:27-28`) : doublons morts ou encore utilisés ?
8. `backend/src/test/resources/characterization/gmao/` : pourquoi un répertoire `backend/src` (29 `.received.json`, aucun autre fichier) ? Un test s'exécute-t-il depuis `backend/` ?
9. PL1 : accord pour écrire le DDL H2 des tables du stock et d'`app_settings` dans `schema.sql` (modification d'un fichier hors `covered.json`) ?
10. KB-S6a-4/6/7 : corriger ou assumer ? KB-S6a-6 touche la politique de rôle du SUPERADMIN.
11. `AnonymityPolicy.THRESHOLD` (plateforme) et `SeuilDAnonymat.SEUIL` (Pilotage) : quelle copie fait foi (PD5-R9, KB-DIR-01, DPO) ?
