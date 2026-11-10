status: draft
date: 2026-11-10
sources: Grep des imports `domain.patient.` / `domain.assure.` (backend/platform, backend/contexts, pom.xml), wc -l, ADR 0004/0064/0073/0077/0078/0079 (tableau reliquats lu par Grep ciblé, pas en entier)

# Inventaire bc-patient / bc-assure

## 1. Résumé
- bc-patient : 15 fichiers java (12 main, 3 test), 738 LOC. bc-assure : 4 fichiers (main), 142 LOC.
- 109 occurrences d'import dans 40 fichiers. **0 utilisateur dans `backend/contexts`** ; seul `backend/platform` consomme (21 fichiers main/test hors modules eux-mêmes : 14 main, 7 test... voir §2).
- pom.xml : déclarés au réacteur (`backend/pom.xml:28-29`), en dependencyManagement (`:58`, `:63`), dépendances de `backend/platform/pom.xml:21,25`. Aucun autre pom.
- Types SANS utilisateur hors module : MouvementPatient, MouvementLigne, TypeMouvementPatient, MouvementsPatient, FinOccupation, JoursDialyse (hors PatientMapper/PatientMigrator) : le calcul des mouvements est déjà dans `contexts/patient-couverture` (MouvementsDesPatients, TypeDeMouvement).
- Le module est tenu en vie par : (a) outil de migration historique, (b) adapters JPA + mapper, (c) 4 lecteurs de `Patient` via `PatientRepositoryPort`.

## 2. Utilisateurs par type (fichier, M = main, T = test) — hors fichiers internes aux modules

| Type | Utilisateurs |
|---|---|
| Patient (bc-patient, 282 LOC) | M: CahierDialyseReportService:4, PatientMapper:3, PatientRepositoryAdapter:3, PatientMigrator:11, OpeningBalanceMigrator:7, AffectationMigrator:9, FichesFournieLegacy:4, GreffePdfService:11. T: CahierDialyseReportServiceTest:3, PatientRepositoryAdapterCacheIntegrationTest:3, MigrationDomainServiceTest:11, InMemoryMigrationPorts:15, HistoryMigrationTest:12, NotificationServiceSaisieInfirmierTest:4 |
| PatientType | M: PatientMapper:4, PatientMigrator:12. T: PatientRepositoryAdapterTest:3, PatientRepositoryAdapterCacheIntegrationTest:4, MigrationDomainServiceTest:12, HistoryMigrationTest:13 |
| PatientId | M: CahierDialyseReportService:6, PatientMapper:8, PatientRepositoryAdapter:5, PatientMigrator:17, OpeningBalanceMigrator:9, AffectationMigrator:11, FichesFournieLegacy:6, GreffePdfService:13, SaisieInfirmierNotifier:4. T: PatientRepositoryAdapterTest:4, InMemoryMigrationPorts:17 |
| NumeroAssurance (patient.vo) | M: PatientMapper:7, PatientMigrator:16. T: CahierDialyseReportServiceTest:4, PatientRepositoryAdapterCacheIntegrationTest:6, MigrationDomainServiceTest:13, HistoryMigrationTest:14 |
| AssureInfo | M: PatientMapper:5, PatientMigrator:14 |
| JoursDialyse | M: PatientMapper:6, PatientMigrator:15 |
| PatientRepositoryPort | M: CahierDialyseReportService:5, PatientRepositoryAdapter:4, PatientMigrator:13, OpeningBalanceMigrator:8, AffectationMigrator:10, MigrationConfig:21, FichesFournieLegacy:5, GreffePdfService:12, SaisieInfirmierNotifier:3. T: PatientRepositoryAdapterCacheIntegrationTest:5, InMemoryMigrationPorts:16, NotificationServiceSaisieInfirmierTest:5 |
| MouvementPatient, MouvementLigne, TypeMouvementPatient, MouvementsPatient, FinOccupation | **aucun** (seuls les 3 tests propres de bc-patient : MouvementsPatientTest, FinOccupationTest, PatientContactValidationTest) |
| Assure (bc-assure) | M: AssureRepositoryAdapter:3, PatientMigrator:3, AssureMigrator:3, AffectationMigrator:3. T: AssureRepositoryAdapterCacheIntegrationTest:3, InMemoryMigrationPorts:3 |
| AssurePatientAssignment | M: AssurePatientRepositoryAdapter:3, PatientMigrator:4, AffectationMigrator:4. T: InMemoryMigrationPorts:4 |
| AssureRepositoryPort | M: AssureRepositoryAdapter:4, AssureMigrator:4, AffectationMigrator:6, PatientMigrator:6, MigrationConfig:4. T: AssureRepositoryAdapterCacheIntegrationTest:4, InMemoryMigrationPorts:6 |
| AssurePatientRepositoryPort | M: AssurePatientRepositoryAdapter:4, PatientMigrator:5, AffectationMigrator:5, MigrationConfig:3. T: InMemoryMigrationPorts:5 |

(numéros = ligne de l'import ; les usages en corps de code ne sont pas tous recensés, cf. §9.)

## 3. Familles (fichiers / LOC)

| Famille | Fichiers | LOC | Détail |
|---|---|---|---|
| F1 Outil de migration historique | 6 main + 3 test | ~1129 main + 774 test | PatientMigrator 419, OpeningBalanceMigrator 133, AssureMigrator 125, AffectationMigrator 152, MigrationConfig 55 (plus les autres migrateurs qui n'importent pas directement) ; T: MigrationDomainServiceTest 290, InMemoryMigrationPorts 286, HistoryMigrationTest 198 |
| F2 Adapters/entités/mapper JPA | 4 adapters+mapper main (PatientRepositoryAdapter 80, AssureRepositoryAdapter 83, AssurePatientRepositoryAdapter 99, PatientMapper 150) = 412 LOC ; + 3 entités JPA et 3 JpaRepository (hors imports domain, 41 occurrences de PatientJpaEntity/AssureJpaEntity/AssurePatientJpaEntity dans 12 fichiers) | ~412 + entités | T: PatientRepositoryAdapterTest 75, PatientRepositoryAdapterCacheIntegrationTest 86, AssureRepositoryAdapterCacheIntegrationTest 91, AssureRepositoryAdapterTest (n'importe pas domain.*), TableDuPatientCouvertureTest (garde, 5 occ.) |
| F3 Lecteurs de Patient (via PatientRepositoryPort) | 4 main + 2 test | 64 + 195 + 109 + 53 = 421 LOC | FichesFournieLegacy (ACL Séance), GreffePdfService, CahierDialyseReportService, SaisieInfirmierNotifier (lit getNom/getPrenom, l.41-44) ; T: CahierDialyseReportServiceTest 45, NotificationServiceSaisieInfirmierTest 175 |
| F4 Propres tests des modules | 3 test | 253 | FinOccupationTest, MouvementsPatientTest, PatientContactValidationTest |
| F5 Mort probable (domaine non référencé) | 5 main | MouvementPatient 33, MouvementLigne 8, TypeMouvementPatient 49, MouvementsPatient 72, FinOccupation 89 = 251 | + JoursDialyse partiellement (via mapper/migrateur) |

KDIGO / FHIR : aucun import de `domain.patient.` / `domain.assure.` détecté ; la mention de l'ADR 0079 (« utilisés par Séance, FHIR, cahier, KDIGO ») est **périmée** pour Séance/FHIR/KDIGO (Séance : `SeanceJpaEntity` supprimée, commit 5558612b).

## 4. Équivalent dans `contexts/patient-couverture` et verdict

| Famille | Équivalent disponible | Manque | Verdict |
|---|---|---|---|
| F5 Mouvements / FinOccupation | `domain/MouvementsDesPatients`, `MouvementDuPatient`, `TypeDeMouvement`, `MouvementsJdbc`, `ConsulterLesMouvements` | rien (à confirmer : parité des tests FinOccupationTest/MouvementsPatientTest portés ?) | à supprimer (mort), après vérif que les 2 tests ont un équivalent dans le contexte |
| F3 SaisieInfirmierNotifier (nom, prénom) | `publishedlanguage.FichesDesPatients` (v1) | vérifier que la lecture nom/prénom est publiée | remplaçable maintenant (probable) |
| F3 GreffePdfService, CahierDialyseReportService | `FichesDesPatients` / `CouvertureDesPatients` | champs lus par le PDF/cahier (NumeroAssurance, identité) à vérifier dans le langage publié | remplaçable maintenant si champs publiés, sinon slice (extension de lecture) |
| F3 FichesFournieLegacy (ACL Séance) | `FichesDesPatients` | l'ACL est un adaptateur de la Séance gelée (ADR 0073/0074) ; sortie = vers `FichesDesPatients` | nécessite slice (petite) |
| F2 Adapters/mapper/entités JPA | `PatientsJdbc`, `AssuresJdbc` (contexte) | seuls les migrateurs (F1) et F3 écrivent/lisent via le port | à supprimer une fois F1 et F3 réglées ; entités JPA à retirer avec (attention `TableDuPatientCouvertureTest`) |
| F1 Outil de migration | aucun (hors périmètre du contexte ; ADR 0077 pt 12, 0078 pt 10, 0079 reliquats) | décision : réécrire sur `AdmettreUnPatient`/`GererLesAssures` (SQL du contexte) ou retirer l'outil | nécessite slice, ou à supprimer si la migration historique est terminée (question métier) |
| Assure / AssurePatientAssignment | `AssurePrincipal`, `Affectation`, `AssuresJdbc`, `GererLesAssures` | idem F1 | tributaire de F1 |

## 5. Code mort (aucun utilisateur de prod)
- Mort certain (0 import hors module, 0 import dans contexts) : MouvementPatient, MouvementLigne, TypeMouvementPatient, MouvementsPatient, FinOccupation (251 LOC) + 2 tests (213 LOC).
- PatientContactValidationTest (40) : teste Patient ; disparaît avec Patient.
- Mort conditionnel : tout F2 si F1 et F3 sont retirés ; `PatientRepositoryPort.findByCodePatient/findByNumeroAssurance/findAllByCenter/countByCenter` : usages non vérifiés dans les lecteurs (grep du corps non concluant, cf. §9) — candidats à réduction du port avant retrait.

## 6. Découpage proposé (≤ 6 slices, ordre)

| # | Slice (un comportement) | Risque | Filet existant | Dépend de |
|---|---|---|---|---|
| S1 | Retirer le calcul des mouvements mort de bc-patient (5 classes + 2 tests) | faible | parité vérifiée vs MouvementsDesPatients (tests du contexte) ; compile | - |
| S2 | Notification de saisie infirmière : lire nom/prénom via `FichesDesPatients` | faible | NotificationServiceSaisieInfirmierTest (175) | - |
| S3 | Cahier de dialyse : lire l'identité/assurance via le langage publié | moyen | CahierDialyseReportServiceTest (45, mince) | - |
| S4 | PDF greffe : idem pour GreffePdfService | moyen | peu de tests (à confirmer via `query.ts untested`) | - |
| S5 | ACL Séance `FichesFournieLegacy` : fournir les fiches depuis `FichesDesPatients` | moyen | tests ACL Séance (à localiser) | - |
| S6 | Outil de migration historique : réécrire (ou supprimer) PatientMigrator/AssureMigrator/AffectationMigrator/OpeningBalanceMigrator ; puis retirer F2 (adapters, entités, mapper) | **élevé** (écriture en bloc, tables partagées) | HistoryMigrationTest, MigrationDomainServiceTest, InMemoryMigrationPorts | décision métier ; S1-S5 |

S2-S5 sont indépendantes (parallélisables, `/ddd-ship`). Pas de Strangler (mémoire projet : refactoring sur place, mêmes tables).

## 7. Critère de retrait final des deux modules
1. `Grep domain\.(patient|assure)\.` dans `backend/platform` et `backend/contexts` = 0 (main et test).
2. Plus aucun `PatientJpaEntity` / `AssureJpaEntity` / `AssurePatientJpaEntity` (ni repositories/mapper/adapters).
3. Retirer `<module>` (`backend/pom.xml:28-29`), dependencyManagement (`:58`, `:63`) et dépendances `platform/pom.xml:21,25`.
4. Build complet vert (platform + contexts), `TableDuPatientCouvertureTest` et tests d'archi verts, nouvelle ligne dans le tableau reliquats de l'ADR 0079 (ou ADR de clôture).
5. Vérifier Dockerfile et scripts `scripts/pit-*.sh` : références à `bc-patient|bc-assure` (grep avec glob incertain, à refaire).

## 8. Questions ouvertes
1. La migration historique (F1) est-elle encore exécutée en production, ou peut-elle être retirée (gain : S6 devient suppression) ?
2. Les migrateurs doivent-ils écrire via les cas d'usage du contexte (verrou du centre) ou en SQL direct hors activité (ADR 0077 pt 12) ?
3. Les lectures nom/prénom/assurance/identité du PDF greffe, cahier, notifications sont-elles toutes publiées par `FichesDesPatients` / `CouvertureDesPatients` ?
4. Les tests FinOccupationTest/MouvementsPatientTest ont-ils un équivalent dans le contexte (sinon les porter avant S1) ?
5. `JoursDialyse`, `AssureInfo` : concepts encore utiles dans le langage du contexte (jours de dialyse) ?

## 9. Limites de l'analyse
- Grep des imports uniquement : pas d'imports joker détectés ; usages en corps de code et DI Spring (injection par type) non tracés ; les greps de méthodes sur les 4 lecteurs n'ont rien renvoyé (motifs trop étroits), donc les méthodes du port réellement appelées restent à confirmer.
- Aucune lecture de corps de classe hors PatientRepositoryPort ; LOC par `wc -l` (lignes brutes).
- Dockerfile/scripts : grep non concluant.
- Scan (`scan.ts`) non utilisé conformément à la consigne.
