---
status: draft
date: 2026-10-09
sources:
  - .ddd/scan/summary.md
  - .ddd/config.json
  - "query.ts (re-vérifié le 2026-10-09 sur le scan régénéré) : seam stock/article, seance/stock, seance/patient, seance/article, migration/referential, pec/patient, pec/insurance, comptabilite/stock, facturation/seance, gmao/stock, reglement/facturation, assure/patient, medical/patient ; untested ; entrypoints"
  - "Lecture de code brut : 0 fichier (aucune lecture nécessaire ; toute affirmation de logique métier est inférée des noms/chemins)"
---

# Analyse du legacy (as-is) — Hémodialyse backend

## Résumé
- Backend Java Spring : 1220 fichiers code (76 k LOC Java) + 267 tests ; 17 « modules » dont **un monolithe `platform` (698 fichiers, 58 k LOC, 546 entrypoints)** et 14 modules `bc-*` hexagonaux, plus `shared-kernel` et `db/migration-legacy` (SQL).
- L'architecture apparente est déjà « DDD-like » (`domain/<ctx>/{model,port,service}` dans les `bc-*`, adapters/REST/JPA dans `platform`). Le legacy est donc **hexagonal incomplet**, pas un big ball of mud.
- **0 cycle entre modules**, mais le couplage réel passe par `platform` : co-changement `bc-seance ↔ platform` = 982 commits, `bc-stock ↔ platform` = 327, `bc-referential ↔ platform` = 251.
- `bc-seance` est un **module fourre-tout** : il contient au moins 4 sous-domaines (séance, planning, infirmier/présence, absence patient) — cluster C1 (infirmier/planning, cohésion 0.92) coupe le module.
- Les `bc-*` n'ont presque aucune dépendance entre eux : seams cross-BC faibles (3 à 21 arêtes) → bonnes coutures. Le vrai nœud est `platform` (controllers, JPA, schedulers, services applicatifs mélangés).
- Hubs (inchangés) : `CenterId` (321 importeurs), `PagedResult` (203) → multi-tenancy par centre transverse ; `PagedResult` est de la plomberie, pas du domaine.
- Dette de test : 530 fichiers non testés sur `platform` ; `bc-medical` (47 non testés) et `bc-facturation` (31, 3 tests pour 33 fichiers) les plus exposés.
- Mode `legacy`, `newCodeRoot = backend/contexts` (config) ; état Git : branche `chore/angular-22-upgrade`.

## Carte réelle vs apparente

| Module apparent | Contenu réel (clusters, termes) | Écart |
|---|---|---|
| `bc-seance` (126 f., 7.5 k LOC) | séance + planning/optimisation (Timefold) + infirmier/présence + absence patient ; C1, C2, C7 | **Trop large** : au moins 4 contextes candidats |
| `platform` (698 f.) | adapters REST/JPA de *tous* les BC, services applicatifs (direction, notification, FHIR, licence, absence), schedulers, optimisation, import Excel | **Dépôt de tout** ; contient aussi de la logique applicative propre (direction, licence C17, notifications) |
| `bc-medical` (98 f.) | ≥ 10 sous-domaines : bilan/greffe pré-greffe (C10, C18, C19), ordonnance (C11), observance/anémie (C12), antécédents (C13), allergies (C14), observations biologiques (C15), sérologie (C20), demandes d'examen (C9), KDIGO (C24) | **Un module, plusieurs agrégats cohérents** ; un ou deux contextes (Dossier médical / Greffe) |
| `bc-stock` + `bc-article` | C2/C21 (stock, bon, commande, lot), articles ; 17 arêtes **stock → article**, sens unique (vérifié par seam) | Stock consomme le catalogue Article (`BonSortieService`, `BonReceptionService`, `StockReferentialUseCase`) ; port/ACL suffit, fusion non imposée |
| `bc-comptabilite` | C3 (cohésion 0.85) + C22 (plan comptable, payeurs) ; tire du stock (`ComptabiliteStockService`) et de la facturation | Cohérent ; dépend d'événements stock/facturation |
| `bc-patient`, `bc-assure`, `bc-insurance`, `bc-pec` | C5 (patient/assure), C25 (attestation/PEC) | 4 micro-modules (122–755 LOC) ; fusion probable « Patient & couverture » |
| `bc-referential` + `bc-migration` | C4 : 21 arêtes mutuelles ; `bc-migration` (plan comptable / mapping import) | Couplage fort ; migration = outil d'import, pas un contexte métier |
| `bc-facturation` + `bc-reglement` | C23 ; `SeanceFacturationJdbcAdapter` (334 l.) lit directement les séances par JDBC | Frontière facturation/séance via SQL direct |
| `bc-gmao` (équipements/interventions) | C6 cohésion 0.8 | Cohérent, isolé (Ca 2, Ce 1) |
| `C8` direction (platform) | dashboard direction, snapshot, alertes : agrège stock/absences/capacité | Contexte de lecture transverse (reporting/CQRS) |

## Logique métier localisée
(Localisation par graphe ; lignes non vérifiées faute de lecture — voir Limites.)

| Règle / sous-domaine | Emplacement | Nature |
|---|---|---|
| Cycle de vie de la séance, orchestration (stock, patient) | `bc-seance/.../domain/seance/service/SeanceDomainService.java` (378 l., churn 14), `model/Seance.java` (281 l., 55 symboles) | Métier (service de domaine qui dépend de `bc-stock` et `bc-patient`) |
| Volet paramédical | `bc-seance/.../model/VoletParamedical.java` (434 l., 121 symboles) | Métier, god-object / agrégat trop gros |
| Valorisation des absences patient | `bc-seance/.../domain/absence/service/ValorisationAbsenceService.java` | Métier |
| Capacité théorique, planning semaine | `bc-seance/.../domain/planning/service/{PlanningSemaineService,CapaciteTheoriqueCalculator}.java` | Métier |
| Sorties de stock, PMP, inventaire | `bc-stock/.../service/BonSortieService.java` (376 l.) | Métier |
| Interventions GMAO, coûts | `bc-gmao/.../model/Intervention.java` (498 l., 50 symboles) | Métier (agrégat gros) |
| Génération d'écritures comptables | `bc-comptabilite/.../service/{GenerateurEcritureComptable,GenerateurEcritureStock,PiecesComptablesService}.java` | Métier ; **non testé** |
| Facturation / TVA | `bc-facturation/.../aggregate/TypeTVA.java`, `entity/LigneFacture.java` | Métier ; **non testé** |
| Optimisation de planning | `platform/.../optimisation/timefold/TimefoldOptimiseurAdapter.java` (303 l.) | Plomberie/algorithme (infrastructure) ; dépendance `ai.timefold.solver` (84 imports) |
| Contrôleur de séances | `platform/.../web/rest/SeanceRestController.java` (1209 l., 46 symboles, churn 17) | **UI/plomberie + logique applicative probable** — god file |
| Notifications | `platform/.../application/notification/NotificationService.java` (398 l., churn 17) | Applicatif transverse |
| Export FHIR, documents PDF, import Excel | `FhirExportService`, `DirectionReportPdfService`, `DocumentRestController`, `FeuilleComptageExcel` | Plomberie / intégration (`org.apache.poi` : 42) |
| Schedulers (15 crons) | `platform/.../infrastructure/scheduling/*` (absences, audit, comptabilité stock, direction temps réel/snapshot, alertes d'expiration, libération de places, licence en ligne, purge de notifications, observance, présence infirmier, replanification automatique, régularisation des séances) | Règles métier **déclenchées par le temps**, logées dans l'infrastructure |

## Couplages cachés (tables, hubs, cycles)
- **Cycles inter-modules : 0.**
- **Tables partagées** : le scan ne rapproche que `db/migration-legacy` ↔ `platform` (20 tables : `patients`, `centers`, `societes`, `audit_log`, `prescriptions_medicales`, `resultats_analyses`, `serologies_patient`, `direction_snapshot`…). Les `bc-*` n'accèdent pas à la base : **tous les JPA/JDBC sont dans `platform`** → la base est un schéma unique partagé de fait (124 tables) entre tous les contextes ; aucune frontière de données.
- **Schéma/seed** en god files SQL : `schema.sql` (1268 l., churn 29), `seed.sql` (2295 l., churn 17), `V1__baseline_schema.sql` (1868 l.) — double source de vérité du schéma (schema.sql + Flyway) à clarifier.
- **Hubs** : `CenterId` (321) et `PagedResult` (203) ; le `centre/tenant` est une dimension transverse (garde de centre) ; prévoir shared kernel minimal.
- **Dépendances directes cross-BC** (arêtes d'import) : `bc-seance → bc-stock` 4, `bc-seance → bc-patient` 3, `bc-seance → bc-article` 3 (sens unique), `bc-stock → bc-article` 17 (sens unique ; article → stock : 0), `bc-migration → bc-referential` 21 (sens unique ; les migrators dépendent des modèles d'import `ValidationIssue`, `ImportTable`), `bc-pec → bc-patient` 4 (`PecDomainService`), `bc-pec → bc-insurance` 1. **0 arête d'import** entre comptabilite↔stock, facturation↔seance, reglement↔facturation, gmao↔stock, medical↔patient, assure↔patient : leur lien passe uniquement par `platform` ou la base. `bc-stock → bc-seance` : 0.
- **`platform` → `bc-*`** : 129 arêtes vers `bc-stock` seul ; `platform` importe directement des classes de domaine de `bc-seance` (ex. `AbsencePatientService` → `ValorisationAbsenceService`, `PlanningSemaineService`) : les services applicatifs sont dans `platform` et non dans le BC.
- **Co-changement** : `bc-seance ↔ bc-stock` 45 commits, `bc-patient ↔ bc-seance` 32 → couplage logique séance–stock et séance–patient (consommation de stock par séance ; patient lié à séance).
- **Polysémie** : voir Questions ouvertes.

## Coutures candidates (coût estimé)

| # | Couture | Signal convergent | Coût | Remarque |
|---|---|---|---|---|
| 1 | **GMAO** (`bc-gmao` + C6 côté `platform`) | Ca 2/Ce 1, cohésion 0.8, 0 arête vers autres BC | Faible | Candidat de première slice (isolé) |
| 2 | **Comptabilité** (`bc-comptabilite`, C3/C22) | cohésion 0.85, Ce 1, déclenché par `ComptabiliteStockScheduler` | Faible à moyen | 0 test sur les services ; 0 arête d'import vers stock/facturation : l'alimentation passe par `platform` (adapters/scheduler), la vraie couture est donc dans `platform` |
| 3 | **Facturation + Règlement** (C23) | 927+375 LOC, Ce 1 | Moyen | 0 arête facturation↔seance et reglement↔facturation côté BC ; lecture JDBC directe des séances dans `platform` (`SeanceFacturationJdbcAdapter`) : couplage par SQL, ACL à prévoir |
| 4 | **Planning / Infirmiers / Absences** hors `bc-seance` (C1) | cohésion 0.92 (la plus haute) | Moyen | Découpage interne de `bc-seance` ; dépendances vers séance/patient |
| 5 | **Séance → Stock** | 4 arêtes, 45 co-changements | Faible (peu d'arêtes) | Inversion via événement `SeanceTerminée`/port de consommation |
| 6 | **Dossier médical** (`bc-medical`, C9–C15, C18–C20, C24) | 1 module, Ce 1, 9+ clusters internes cohésifs | Moyen (volumétrie, 47 non testés) | Possible sous-découpage Greffe / Observance / Biologie |
| 7 | Stock → Article | 17 arêtes, sens unique | Moyen | Port de lecture du catalogue (ACL) ; fusion à trancher avec les experts |
| 8 | Référentiel ↔ Migration | 21 arêtes | Moyen | `bc-migration` est de l'outillage, à sortir du domaine |

## Zones à risque / à haute valeur
- **Risque** : `SeanceRestController` (1209 l., churn 17, non testé) ; `SeanceDomainService` (churn 14) ; `NotificationService` ; `schema.sql`/`seed.sql` ; `DomainServiceConfig` (209 l., churn 11 — câblage manuel de tous les services de domaine).
- **Risque métier** : facturation/TVA et comptabilité (non testées, argent) ; valorisation d'absences (TTC → HT) ; schedulers (comportements invisibles, 15 crons).
- **Haute valeur** : cœur « séance » (co-change maximal), planning/optimisation, stock (PMP, traçabilité lots), direction (dashboards).
- **Fuite de frameworks** : Spring Web 438, Spring Data 195, Security 163, JDBC 117 — dans `platform` (attendu) ; vérifier que les `bc-*` restent purs (imports `jakarta.validation` : 121 à localiser).
- **Nommage technique** : 139/1574 classes en `Service/Manager/Helper/…` ; **572 occurrences de `request`** (DTO) dans `platform`.

## Dette de test
- 267 fichiers de test pour 1220 de code. Non testés : `platform` 530, `bc-medical` 47, `bc-seance` 34, `bc-facturation` 31, `bc-stock` 31, `bc-comptabilite` 13, `bc-gmao` 8, `bc-migration` 6 (beaucoup sont des ports/DTO sans logique).
- Services non testés prioritaires : tous les services de `bc-comptabilite` (`ComptabiliteService` 160 l., `ComptabiliteStockService` 167 l., `PiecesComptablesService` 155 l., `PlanComptableService` 132 l., `GenerateurEcritureComptable` 129 l.), `SeanceRestController` (1209 l.), `ComptabiliteRestController` (427 l.), `DirectionReportPdfService` (417 l.), `SeanceFacturationJdbcAdapter`.
- Bonne couverture relative : `bc-seance` (77 tests), `shared-kernel` (106 tests).
- 546 entrypoints (530 HTTP, 15 cron, 1 main) = cibles de caractérisation ; les 15 crons sont les moins visibles.

## Limites de l'analyse statique
- Aucun code lu : emplacements métier inférés des noms, tailles, clusters ; `chemin:ligne` non fourni.
- Les tables partagées ne sont détectées que SQL ↔ `platform` ; le scan ne montre pas quels *contextes* utilisent quelle table (les JPA entities sont toutes dans `platform`).
- Pas de visibilité sur : événements Spring/`@EventListener`, jobs externes, intégrations (FHIR, e-mail, licence en ligne), consommateurs externes de l'API, usages côté frontend (exclu du scan).
- Le co-change sur 133 commits est court et dominé par les commits transverses (`feat(...)` touchant `platform` à chaque fois) → gonfle le couplage `platform ↔ bc-*`.
- `seed.sql`/`schema.sql` (churn élevé) faussent les hotspots techniques ; les noms de module « depth 2 » masquent les sous-paquets (`domain/absence`, `domain/planning`, …).

## Consommateurs implicites probables
- 15 schedulers (`AuditScheduler`, `DirectionSnapshotScheduler`, `LicenseOnlineCheckJob`, `ComptabiliteStockScheduler`, …).
- Exports : comptable (`ExportComptablePort`), FHIR, PDF direction, documents, Excel (feuille de comptage).
- Import `bc-migration` (plan de migration / mappers) et jeux de données `db/demo/generate-renadial-seed.cjs`.
- Notifications et alertes (expiration, observance) = effets de bord consommés par l'UI.

## Questions ouvertes pour les experts
1. « Séance » : même sens pour planning, facturation (forfait) et stock (consommation) ? `bc-seance`, `bc-facturation`, `bc-stock` utilisent tous le terme.
2. « Patient » : un patient du dossier médical, de l'assurance (assuré/PEC) et de la séance est-il le même objet ? Rapport `patient` / `assure` / `bc-pec` / `bc-insurance` ?
3. « Centre » vs « Center » vs « Société » : hiérarchie (société → centre → site) et rôle de « Direction » ; deux langues dans les identifiants (`center` / `centre`).
4. « Statut » (79 `platform`, 46 `bc-medical`, 25 `bc-gmao`…) : quels cycles de vie par agrégat (séance, ordonnance, intervention, bon, facture) ?
5. « Compte » / « Compte payeur » / compte comptable / compte utilisateur : 4 sens ?
6. « Article », « Lot », « Bon (de sortie / commande) » : découpage Stock vs Article vs Pharmacie ; unité propriétaire du PMP ?
7. Absence : absence de patient (valorisation) vs absence d'infirmier : même concept ou deux ?
8. Règles temporelles : que font précisément les 15 crons (régularisation des séances, libération de places, réplanification automatique) ?
9. Clôture mensuelle/comptable, périodes verrouillées (non détectées).
10. Y a-t-il des consommateurs externes de l'API ou de la base (BI, exports, autres systèmes) ?
11. Priorité business : quel sous-domaine apporte le plus de valeur pour une première slice (GMAO ? comptabilité ? facturation ?).
12. (Hypothèse non vérifiée) `bc-migration` est de l'outillage d'import ponctuel et non un contexte — à confirmer.
13. (Hypothèse non vérifiée) `schema.sql` est l'artefact de démo/dev et Flyway `V1__baseline_schema.sql` le schéma réel — à confirmer.
