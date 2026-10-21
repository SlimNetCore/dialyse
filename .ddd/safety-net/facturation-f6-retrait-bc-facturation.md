---
status: proposed
date: 2026-10-21
slice: facturation-f6-retrait-bc-facturation
sources: [.ddd/migration/roadmap.md § 1 ter ligne F6, .ddd/safety-net/facturation-f5-consultation-pilotage.md, gmao-g6-temps2-bc-gmao.md, comptabilite-c7-retrait-bc-comptabilite.md, fiches facturation-f1..f5, known-bugs.md, seams.md, grep exhaustif du dépôt (2026-10-21)]
---
# Filet F6 : retrait du module `backend/bc-facturation` et derniers consommateurs de la Facturation

Refactoring sur place (ADR 0024, 0030..0034) : mêmes tables, mêmes URLs, aucune façade/routage/projection. Slice de **nettoyage** : aucun nouveau comportement.
**Aucun code de production modifié, aucun seam appliqué** (`seamAllowed` vide). Tests et goldens ajoutés seulement.

## 1. Inventaire exact des références (grep du 2026-10-21)

### 1.1 Au module Maven `bc-facturation` (le module ne contient plus que `pom.xml`, `README.md`, `target/`)
| Où | Référence | Nature |
|---|---|---|
| `backend/pom.xml:35` | `<module>bc-facturation</module>` | build |
| `backend/pom.xml:106` | `dependencyManagement` `bc-facturation` | build |
| `backend/platform/pom.xml:57` | dépendance `bc-facturation` (seul module qui la déclare ; `bc-migration`, `bc-seance`, `bc-stock`, `contexts/*` ne la déclarent pas) | build |
| `backend/Dockerfile:23` | `COPY bc-facturation/pom.xml ./bc-facturation/pom.xml` | image (le build Docker échouerait une fois le dossier supprimé) |
| `backend/scripts/pit-c3-ecritures.sh:17`, `pit-f3-validation.sh:16` | boucle `for MOD in … bc-facturation …` (remplacement des jars par `target/classes`) | scripts PIT |
| `backend/scripts/pit-f3-validation.sh:3` (commentaire) et `:33` | `--sourceDirs "$B/platform/src/main/java,$B/bc-facturation/src/main/java"` | script PIT (sourceDir inexistant après retrait) |
| `backend/bc-facturation/{pom.xml,README.md,target/}` | le module lui-même (le `pom.xml` dépend de `shared-kernel`, que `platform` déclare déjà : retrait sans effet de classpath) | à supprimer |
| `.idea/{compiler.xml,encodings.xml,workspace.xml,copilotDiffState.xml}` | configuration d'IDE | à laisser régénérer par l'IDE |
| `.ddd/*` (ADR 0032/0034, roadmap, fiches F5, tactical, strategic, reviews, verify) | texte | mise à jour documentaire |

**Java, production ET tests : 0 référence** à `com.hemodialyse.backend.domain.facturation` (grep sur `backend/**/*.java`, hors `contexts/facturation`) ; le seul fichier qui cite ce chemin est `pit-f3-validation.sh`. `TypeTVA`, `TypeTvaRepositoryPort`, les ports de tableau de bord et `FacturationDomainService` sont déjà supprimés (git status). Les tests ne dépendent plus du module : le retrait ne casse aucune compilation Java.

### 1.2 Aux tables `factures`, `facture_lignes`, `facture_reglements`, `facture_sequence`, `facturation_settings` (et `seances.facture_id/statut`) HORS `contexts/facturation`
| Module | Classe (prod) | Accès | Filet |
|---|---|---|---|
| `contexts/comptabilite` | `FacturesAComptabiliserJdbc` (`factures`), `ReglementsAComptabiliserJdbc` (`facture_reglements`) | lecture SQL directe | C5, F3, F4 (§ 3) + **F6** (reprise, KB-FAC-67) |
| `platform` listeners | `FacturationValideeComptabiliteListener`, `ReglementEncaisseComptabiliteListener` | événements publiés par `contexts.facturation.publishedlanguage` (aucun SQL) | F3/F4 `EvenementEtComptabilite…` |
| `platform` Direction | `DirectionDashboardQueryService`, `DirectionBreakdownQueryService`, `DirectionAbsencesQueryService` | **publié** `SyntheseFinanciereDesCentres` (F5) ; ne lisent plus que `seances`/`patients` en SQL. `DirectionRealtimeService` et `DirectionReportPdfService` ne citent que le champ `factures` des DTO | F5 |
| `platform` Absences | `AbsenceDonneesJdbcAdapter.periodeFacturee` | lecture SQL `factures` (`patient_id`, `period_start <= d <= period_end`) | `AbsencePatientIntegrationTest` + **F6** |
| `platform` Migration | `OpeningBalanceJdbcAdapter` (écrit `factures`, `facture_lignes`, `facture_reglements`), `MigrationRollbackJdbcAdapter` (lit/supprime les mêmes tables, lit `seances.facture_id`) | SQL direct | `MigrationHistoryIntegrationTest` (sans golden) + **F6** |
| `platform` Stock | `SeanceBillingStatusJdbcAdapter.isBilled` (port `bc-stock` `SeanceBillingStatusPort`) | lit `seances.statut = 'FACTUREE'` seulement | `BonSortieRestControllerIntegrationTest`, `BonSortieServiceTest` + **F6** (adaptateur direct) |
| `platform` Séance | `Seance`/`SeanceDomainService`/`VoletMedical|Paramedical…` (`bc-seance`) : statut `FACTUREE` immuable | domaine Séance, la Facturation écrit le statut (F3) | `SeanceSuppressionIntegrationTest` (DELETE) + **F6** (10 autres routes) |
| `platform` Impression | `FacturationSyntheseMensuelleReportService` (SQL du gabarit dynamique : `facture_lignes`, `factures`), gabarit statique `reports/synthese_mensuelle_facturation.jrxml` via `DocumentRestController` | SQL direct dans le gabarit ; ces deux classes importent aussi `application`/`domain` du contexte (dette, § 6) | F5 |
| `platform` Référentiel | `ReferentialAdminTables` (`facture_lignes.forfait_id` : blocage de suppression d'un forfait) | usage déclaré | F1 |
| `platform` ACL | `AbsencesDesPatientsLegacy` (importe `application.simulation`, `domain`) | ACL vers Absences pour F2 | F2 |
| Ressources | `db/migration/V1`, `V40`, `V41`, `db/schema.sql`, `db/seed.sql`, `db/demo/renadial-{seed,purge}.sql` | schéma et données | hors périmètre (le schéma ne change pas) |
| Tests non « characterization » qui posent ces tables en SQL | `ComptabiliteStockIntegrationTest`, `AnalyseurPlanTest`, `AbsencePatientIntegrationTest`, `Direction{,Breakdown,Realtime,Report}IntegrationTest`, `DocumentRestControllerSyntheseMensuelleIntegrationTest`, `FacturationRestControllerIntegrationTest`, `FacturationSynthesePrintRestControllerIntegrationTest`, `MigrationHistoryIntegrationTest`, `ReglementRestControllerIntegrationTest`, `contexts/comptabilite …MappingComptableTest` | fixtures SQL ; aucun n'importe de classe legacy | verts, à rejouer |

## 2. Point d'observation et harnais
HTTP via MockMvc sur le contexte Spring complet (`@SpringBootTest` MOCK, profil `test`, Spring Security et `RoleScopeFilter` réels, **H2**). Migration : routes admin `/api/v1/admin/migration/batches/**` (import CSV `soldes-ouverture`, `seances`, `patients`, `cancel`). L'adaptateur `SeanceBillingStatusJdbcAdapter` (consommateur Stock) est appelé directement (bean de production, sans double). Aucun mock du code caractérisé.
Harnais : `backend/platform/src/test/java/com/hemodialyse/backend/characterization/facturation/retrait/RetraitCharacterizationSupport.java` (identifiants fixes NOMMÉS, jours relatifs NOMMÉS par `jour(n, nom)`, masquage de `patient_code`, `REG-`/`REP-`, horodatages ; clés triées). Goldens : `backend/platform/src/test/resources/characterization/facturation/retrait/{seance-facturee,absences,reprise}/*.approved.json` (37 fichiers, vérifié).
Alias de dates : `<AUJOURD_HUI>` (jour d'exécution) ; `<J_MOINS_21>`, `<J_MOINS_20_DEBUT_DE_PERIODE>`, `<J_MOINS_15>`, `<J_MOINS_10_FIN_DE_PERIODE>`, `<J_MOINS_9>`, `<J_MOINS_5_RATTRAPAGE>` (déclaration d'absence limitée à 60 jours en arrière : seule règle qui oblige à des jours relatifs). Les autres dates de fixtures sont des constantes de 2026. Aucun `<UUID_GENERE_n>` dans les goldens (vérifié).

## 3. Scénarios ajoutés (27 tests, 3 classes) et ce qui était DÉJÀ verrouillé

| Consommateur | Déjà verrouillé (classe) | Ajouté par F6 |
|---|---|---|
| **Séance** (`FACTUREE` immuable) | `SeanceSuppressionIntegrationTest` (DELETE : `SEANCE_FACTUREE_NON_SUPPRIMABLE`), `SuppressionSeanceServiceTest`, tests unitaires `SeanceDomainServiceTest`, `Volet*DomainServiceTest`, `SuppressionSeanceTest` | `SeanceFactureeImmuableCharacterizationIntegrationTest` (14) : HTTP sur une séance facturée : date, forfait, valider, signer-médecin, consommable ajout/modification/retrait, volet médical, volet paramédical, déverrouillage, administration de traitement (réponse 422 + état SQL séance/stock/volets inchangé) ; témoin : la date d'une séance validée reste modifiable ; |
| **Stock** (`isBilled`) | `BonSortieRestControllerIntegrationTest` (PUT bon de sortie → `SEANCE_BILLED_STOCK_EXIT_IMMUTABLE`), `BonSortieApplicationServiceIntegrationTest`, `BonSortieServiceTest` (port en mémoire) | même classe : `POST /patients/{id}/administrations-anemie` sur séance facturée (`createViaFefo`, stock intact) ; adaptateur `isBilled` : insensible à la casse, centre isolé, séance inconnue, seul le statut compte (pas le lien `facture_id`) |
| **Absences** (`periodeFacturee`) | `AbsencePatientIntegrationTest` (service : qualification, annulation, déclaration, détection d'un patient dont la période est facturée) | `PeriodeFactureeAbsencesCharacterizationIntegrationTest` (5), HTTP : déclaration refusée ; **bornes incluses** (veille/lendemain acceptés, premier et dernier jour refusés) ; facture d'un autre patient ou d'un autre centre sans effet ; qualification, **rattrapage** (non couvert avant) et annulation refusés après émission, absence inchangée ; témoin sans facture |
| **Migration** (soldes d'ouverture, rollback) | `MigrationHistoryIntegrationTest` (import nominal, facture soldée ignorée, rejeu sans doublon, annulation vide les tables, annulation refusée par séance facturée), `HistoryMigrationTest` (bc-migration, ports en mémoire) | `FacturesDeRepriseCharacterizationIntegrationTest` (8) : **état exact** des tables (facture `REPRISE-…` hors séquence, TVA 0, TTC = HT, une ligne « Solde d'ouverture — reprise », règlement `REP-…` `saisi_par = REPRISE`, `mode_reglement` NULL, `facture_sequence` vide) ; compte rendu d'import (warning `FULLY_PAID`) ; payeur et agence figés depuis le patient ; remplacement sur place au rejeu (même identifiant de facture) ; encaissement de la plateforme → rejeu refusé `PAID_IN_PLATFORM` et annulation refusée `MIGRATION_CANCEL_BLOCKED`, état inchangé ; annulation ne supprimant que les factures de reprise (facture ordinaire et son règlement intacts) ; annulation selon le lien `facture_id` (pas le statut) |
| **Règlement F4 vu d'une facture de reprise** | F4 (factures posées en SQL ou émises par F3) | même classe : recherche, tableau de bord et historique des paiements d'une facture de reprise ; paiement de la plateforme ajouté (reste, nombre de paiements) |
| **Comptabilité** (rebuild des ventes/règlements) | C5 `VentesEtReglementsCharacterizationIntegrationTest`, `ParcoursDesEcrituresCharacterizationIntegrationTest`, F3/F4 `EvenementEtComptabilite…`, F5 `Coherence…` | même classe : `GET /comptabilite/ventes/non-comptabilisees` puis `POST /comptabilite/rebuild` sur des factures de reprise → ventes (`VE`) et règlements (`BQ`) générés (**KB-FAC-67**) |
| **Direction** | F5 `LecturesFinancieresDirectionCharacterizationIntegrationTest` (12) via `SyntheseFinanciereDesCentres` | rien : déjà passée par le langage publié |
| **Architecture** | `FrontieresDesContextesTest` (Comptabilité) | +2 règles ArchUnit : plus aucune classe ne dépend de `domain.facturation..` ; le legacy n'utilise la Facturation que par `publishedlanguage`, **hors dette connue** de 3 classes (§ 6) |

## 4. Mesures
- **Suite F6 (27 tests + 3 ArchUnit)** : voir § 8. Mutation PIT : non lancée (aucune logique de calcul nouvelle ; les adaptateurs SQL sont exercés par des goldens d'état ; le module `bc-facturation` est vide). Couverture de branches : JaCoCo non configuré (comme F1 à F5) ; sont exercés par un scénario nommé : `periodeFacturee` (borne basse, borne haute, autre patient, autre centre, hors période), `isBilled` (vrai/faux/inconnu/autre centre), `OpeningBalanceJdbcAdapter.create/replace/findByNumero/countPaymentsOutsideMigration`, `MigrationRollbackJdbcAdapter` branches `SOLDES_OUVERTURE` (bloquée/non bloquée), `SEANCES` (lien/sans lien) et `delete` de `SOLDES_OUVERTURE`, les 11 routes de modification d'une séance facturée.
- Fichiers ajoutés à `covered.json` (en ajoutant, rien retiré, +14) : `OpeningBalanceJdbcAdapter`, `MigrationRollbackJdbcAdapter`, `SeanceBillingStatusJdbcAdapter`, `AbsenceDonneesJdbcAdapter`, `DocumentRestController`, `ReferentialAdminTables`, `BonSortieApplicationService`, `BonSortieService`, `SeanceBillingStatusPort`, `OpeningBalancePort`, `MigrationRollbackPort`, `OpeningBalanceMigrator` (+ `AbsencesDesPatientsLegacy` et `FacturationSyntheseMensuelleReportService` déjà couverts). `pom.xml`, `Dockerfile`, scripts et `bc-facturation/**` ne sont pas dans le périmètre du hook (`src/main/java`) : à modifier sans filet de hook, vérifiés par la compilation.

## 5. Non verrouillé (limites)
- Les `pom.xml`, `Dockerfile` et scripts PIT ne sont pas observables par un test ; la validité du retrait est celle du build (`./mvnw -o clean verify`, `docker build`).
- `DirectionRealtimeService` et `DirectionReportPdfService` (PDF/diff temps réel) : non rejoués ici (aucune requête propre sur les factures).
- PostgreSQL (échelle des `NUMERIC`, ordre sans `ORDER BY`), concurrence (rebuild pendant une validation, encaissement pendant la reprise), performance, fuseau horaire du serveur, front (e2e à la charge de l'utilisateur).
- Reprise : colonnes optionnelles du CSV (période, date du dernier règlement, libellé) hors cas nominal ; volume ; dates futures et montants invalides (déjà dans `HistoryMigrationTest`).
- Résolution du payeur d'un patient sans agence, séances `SIGNEE` rattachées par un autre chemin que la reprise.
- Seams non appliqués (§ 7) : jours relatifs des absences, identifiants aléatoires de reprise.

## 6. Plan de retrait recommandé (une compilation et une suite verte entre chaque étape)
**Étape A, retrait mécanique du module (aucune classe à migrer)**
1. `backend/pom.xml` : supprimer `<module>bc-facturation</module>` (l.35) et l'entrée `dependencyManagement` (l.106).
2. `backend/platform/pom.xml` : supprimer la dépendance (l.57).
3. `backend/Dockerfile` : supprimer la ligne 23 `COPY bc-facturation/pom.xml …`.
4. `backend/scripts/pit-c3-ecritures.sh` (l.17) et `pit-f3-validation.sh` (l.16, `--sourceDirs` l.33, commentaire l.3) : retirer `bc-facturation`.
5. Supprimer `backend/bc-facturation/` (pom, README, `target/`). Les 28 entrées périmées de `covered.json` pour ce dossier sont sans objet (laisser).
6. Vérifier : `./mvnw -o clean verify` (suite complète), `check-arch.ts`, `FrontieresDesContextesTest`, les 5 classes F5, les 3 classes F6 ; `docker build backend` si possible.
**Étape B, derniers consommateurs par le langage publié (dette, sans changer un golden)** — ports à créer dans `contexts/facturation/publishedlanguage`, adaptés par des ACL de `platform` ou de `contexts/comptabilite` :
7. `FacturationDuPatient.periodeFacturee(centre, patient, jour)` (ou `PeriodesFacturees`) pour Absences : `AbsenceDonneesJdbcAdapter` ne lit plus `factures`. Parité : `PeriodeFactureeAbsences…` (5) + `AbsencePatientIntegrationTest`.
8. `RepriseDesSoldes` (créer ou remplacer un solde non encaissé, compter les encaissements hors reprise, supprimer les soldes d'un lot, indiquer les factures liées à des séances) pour la Migration : `OpeningBalanceJdbcAdapter`/`MigrationRollbackJdbcAdapter` deviennent des ACL fines ; `bc-migration` garde `OpeningBalancePort`/`MigrationRollbackPort`. Parité : `FacturesDeReprise…` (8) + `MigrationHistoryIntegrationTest`. **Décision préalable : KB-FAC-67.**
9. Comptabilité : `FacturesEmises`/`ReglementsEncaisses` publiés (lecture seule, par période) à la place de `FacturesAComptabiliserJdbc`/`ReglementsAComptabiliserJdbc`. Parité : C5/F3/F4 + `compta_*` de F6.
10. Impression : `FacturationSyntheseMensuelleReportService` et `DocumentRestController` n'importent plus `application`/`domain` du contexte (publier `SyntheseMensuelle`, `ConsulterLaSynthese`, formats) ; `AbsencesDesPatientsLegacy` idem. Puis vider la liste d'exemptions de la 2e règle ArchUnit.
11. Stock : `SeanceBillingStatusPort` lit `seances.statut` (données Séance/Stock, pas Facturation) : à ne pas déplacer dans cette slice ; option `SeancesFacturees` publié par la Séance (hors F6). `ReferentialAdminTables` : usage `facture_lignes.forfait_id` à remplacer par un port publié `ForfaitsUtilises` si le blocage de suppression doit se faire sans SQL.
**Étape C, documentation** : `backend/bc-facturation/README.md` (supprimé avec le module), `.ddd/migration/roadmap.md` (ligne F6), fiches F1..F5 (« reste à retirer avec F6 »), tactical/strategic, regles-de-gestion (aucun changement de comportement), `state.ts set slices.facturation-f6-retrait-bc-facturation retired`.

## 7. Seams
Aucun appliqué ni nécessaire. Optionnels (non demandés, `seams.md`) : `Clock`/générateur d'identifiants de `OpeningBalanceJdbcAdapter` ; `Clock` d'`AbsencePatientService` (permettrait des dates de déclaration fixes).

## 8. Exécution
Depuis `backend/`, une classe à la fois, noms complets (ne jamais lancer deux builds Maven en parallèle) :
```
./mvnw -o -pl platform -am test '-Dtest=com.hemodialyse.backend.characterization.facturation.retrait.SeanceFactureeImmuableCharacterizationIntegrationTest,com.hemodialyse.backend.characterization.facturation.retrait.PeriodeFactureeAbsencesCharacterizationIntegrationTest,com.hemodialyse.backend.characterization.facturation.retrait.FacturesDeRepriseCharacterizationIntegrationTest,com.hemodialyse.backend.architecture.FrontieresDesContextesTest' -Dsurefire.failIfNoSpecifiedTests=false
```
Ordre aléatoire : ajouter `-Djunit.jupiter.testmethod.order.default=org.junit.jupiter.api.MethodOrderer$Random`. Approuver un golden : `-Dcharacterization.approve=true` sur la seule classe visée, après revue.
Résultat : voir « Mesures finales » ci-dessous.

## 9. Bugs connus
KB-FAC-67 à 71 (`known-bugs.md`) : rebuild comptable des factures de reprise, compte rendu de reprise trompeur, trois définitions de « séance facturée », quatre formes de refus d'une séance facturée, `saisi_par` = principal. Aucun corrigé.

## 10. Questions de décision
1. **KB-FAC-67** : reproduire (le rebuild comptabilise les factures `REPRISE-…`) ou exclure/segmenter les factures de reprise du rebuild ? À trancher avec le comptable avant l'étape 8/9.
2. F6 = retrait mécanique (étape A seule) ou aussi les ports publiés des étapes 7 à 10 ? Recommandation : A d'abord (livrable immédiat, risque nul), puis B par consommateur.
3. Définition unique de « séance facturée » (statut ou lien `facture_id`, KB-FAC-69) et code stable de refus d'une séance facturée (KB-FAC-70) : reproduire ou corriger ?
4. Accord pour les deux seams optionnels (`Clock`) ?

## Mesures finales (2026-10-21)
3 exécutions consécutives vertes de **30 tests** (27 de caractérisation + 3 ArchUnit) : la 1re en ordre par défaut, les 2 autres avec `MethodOrderer$Random` ; 0 flaky ; aucun `*.received.json` résiduel. Durée ≈ 1 min par exécution (démarrage Spring ≈ 35 s, partagé).

## Décisions du 2026-10-21 (réponses au § 10)
1. KB-FAC-67 : corrigé (reprise exclue du rebuild). 2. F6 = étapes A et B (ports publiés). 3. KB-FAC-70 : code unique `SEANCE_FACTUREE_NON_MODIFIABLE` ; KB-FAC-69 reproduit. 4. Pas de seam `Clock`. Voir `.ddd/migration/slices/facturation-f6-retrait-bc-facturation.md` et ADR 0035. Goldens remplacés : `reprise/compta_*_nouveau_code` (3), `seance-facturee/*_nouveau_code` (11).
