---
status: proposed
date: 2026-11-02
scope: contexte dossier-medical (DM1 à DM8), vérification globale
sources: [.ddd/state.json, .ddd/migration/roadmap.md, .ddd/migration/slices/dossier-medical-*.md, .ddd/safety-net/covered.json, backend/contexts/dossier-medical, backend/platform, git 2978e639 / 21f6ff7a / b365a63a]
reviewer: relecteur indépendant (lecture seule, aucun build ni test lancé)
---

# Vérification globale : Dossier médical (DM1 à DM8)

## 0. Contrôles automatiques
| Contrôle | Résultat | Commentaire |
|---|---|---|
| `check-arch.ts` | 0 violation (0 en baseline) | ne voit pas les défauts listés plus bas (horloge, sécurité, câblage) |
| `state.ts show` | DM1 à DM8 `built` | faux, voir §3 |
| `scan.ts --incremental` | **non lancé** | il écrirait dans `.ddd/scan` (consigne : lecture seule) |
| ArchUnit `ArchitectureHexagonaleTest` (module) | non exécuté | la règle « pas d'horloge » est contournable (constat M6) ; `failOnEmptyShould=false` |
| `FrontieresDesContextesTest` / `TableDuDossierMedicalTest` | non exécutés | la garde de tables est vide de sens (constat B4) |

## 1. Vérité, en une phrase
Le module `backend/contexts/dossier-medical` est **compilé mais jamais déployé** : `backend/platform/pom.xml:17-80` ne dépend pas de `contexts-dossier-medical` (seul `backend/pom.xml:44` le liste comme module). **100 % du trafic médical en production passe encore par le legacy** : `bc-medical` (98 classes) et 16 contrôleurs `platform/.../web/rest/*` (Serologie, Allergie, Antecedent, BilanPreGreffe, EtapeBilanPreGreffe, DemandeExamen, ObservationBiologique, Ordonnance, PrescriptionMedicale, Constantes, DossierMedicalPatient, Fhir, GreffeExport, KdigoGreffe, VoletMedical, VoletParamedical). Aucune bascule, aucune parité, aucun retrait.

## 2. Constats

| # | Sévérité | Domaine | Constat (violation sauf mention « suggestion ») | Preuve | Correction recommandée |
|---|---|---|---|---|---|
| B1 | blocker | legacy | **Code legacy de production modifié sans filet** : une colonne `center_id` `nullable=false` a été ajoutée à l'entité et à l'adapter des lignes d'examen par le commit 21f6ff7a. Aucune caractérisation ne couvre les demandes d'examen. | `platform/.../persistence/entity/LigneDemandeExamenJpaEntity.java:25-27`, `.../adapter/DemandeExamenRepositoryAdapter.java:63,104-108` | revert, ou caractérisation des demandes d'examen + ADR + migration |
| B2 | blocker | archi/données | **Aucune migration Flyway pour cette colonne.** `V1__baseline_schema.sql:851-860` crée `lignes_demande_examen` sans `center_id`, et aucun `V2..V49` ne l'ajoute. Avec `ddl-auto: validate` en production (`application-prod.yml:11`), le démarrage échouera très probablement (colonne manquante). Les lignes existantes n'ont pas de valeur à reprendre. | `db/migration/V1__baseline_schema.sql:851`, `application-prod.yml:11` | migration `V50` avec ajout de colonne, reprise des données depuis `demandes_examen.center_id`, puis `NOT NULL` ; ou revert de B1 |
| B3 | blocker | legacy/tests | **`covered.json` élargi de 44 fichiers legacy sans filet** (ordonnances, demandes d'examen : contrôleurs, DTO, adapters JPA, agrégats `bc-medical`, `ordonnance.jrxml`). Seules les **constantes** sont caractérisées (4 goldens). `guard-legacy` est donc désarmé sur du code de santé non caractérisé. | `.ddd/safety-net/covered.json:90-131` (blame 2978e639) ; `characterization/dossiermedical/constantes/` seul dossier | retirer ces entrées tant que les scénarios n'existent pas |
| B4 | blocker | legacy/archi | **La garde « pas de SQL hors contexte » est vide de sens.** Elle liste les tables du **nouveau** schéma (`serologies`, `antecedents`, `allergies`, `bilans_pregreffe`…) et non les tables réelles (`serologies_patient`, `antecedents_medicaux`, `allergies_patient`, `bilans_pre_greffe`, `etapes_bilan_pre_greffe`, `ordonnances`, `prescriptions_medicales`). Elle exclut `platform` ainsi que `/acl/` et `/persistence/`. Son test d'auto-contrôle exclut aussi `/persistence/`, donc il passe toujours. Exemple non détecté : Planification lit `serologies_patient`. | `platform/src/test/.../architecture/TableDuDossierMedicalTest.java:37-42,54-57,87-88,143-147` ; `planification/.../acl/legacydossiermedical/RisquesInfectieuxJdbc.java:22,25` | réécrire la garde sur les tables réelles, inclure `platform`, puis une liste blanche explicite (ACL Planification) |
| B5 | blocker | archi/données | **Modèle de données parallèle**, contraire à la décision « mêmes tables, mêmes URL, refactoring sur place ». Le module crée de nouvelles tables (`serologies`, `antecedents`, `allergies`, `bilans_pregreffe`, `etapes_bilan_pregreffe`) et de nouvelles URL `/api/v1/dossier-medical/*`. Les fiches disent pourtant « aucune migration ». Trois noms entrent en collision avec les tables legacy, sans garantie de colonnes : `demandes_examen`, `lignes_demande_examen`, `observations_biologiques`. Câbler le module = dual model / split brain. | `contexts/dossier-medical/src/main/resources/db/schema.sql:4,56,111,173,247,303,355,381` ; `interfaces/http/*Controleur.java:18-24` ; fiche `dossier-medical-dm5-...md` § Données | ADR : soit le module se branche sur les tables et URL legacy (sur place), soit on documente une migration de données. Ne pas câbler en l'état |
| B6 | blocker | confidentialité | **Aucun contrôle d'accès** dans les 6 contrôleurs (0 `@PreAuthorize`). Le centre vient du client (`@RequestParam centreId` ou corps de la requête), pas de la session, donc il y aurait une **fuite inter-centres** si le module était câblé. Le legacy, lui, impose les rôles (`SerologieRestController.java:51,66,75,89,101`) et refuse le cross-centre (golden `constantes_cross_centre.approved.json`). | `SerologiesControleur.java:27-41`, idem pour les 5 autres contrôleurs ; les tests d'intégration ne posent ni utilisateur ni rôle | garde de centre issue du JWT, `@PreAuthorize` par rôle, tests négatifs (infirmier, autre centre) |
| B7 | blocker | confidentialité | **Écritures et lectures médicales hors du Journal d'audit** : `AuditActionResolver` ne connaît que les routes legacy `/api/v1/patients/{id}/serologies…`. Aucune entrée pour `/api/v1/dossier-medical/*`. | `platform/.../audit/AuditActionResolver.java:56-58` | entrées d'audit (lectures et écritures) avant tout câblage |
| M1 | major | tactique/legacy | **`SerologiePositiveDetectee` n'est jamais publié** (consommateur no-op) : le flux sérologie positive → réaffectation en isolement (P5) n'existe pas dans le nouveau code. Même chose pour `BilanPreGreffeComplete`. Pas d'outbox. | `configuration/SerologiesConfiguration.java:37-43`, `configuration/BilansPreGreffeConfiguration.java:37-40` | outbox dans la même transaction, ou garder le chemin legacy P5 (`ReaffectationEnIsolementDeLaSerologie`) |
| M2 | major | intégrité | **Aucune transaction** (0 `@Transactional` dans le module) : `BilansPreGreffeJdbc.save` fait UPDATE + DELETE + INSERT des étapes sans transaction. Pas de version optimiste. | `infrastructure/persistence/BilansPreGreffeJdbc.java:77-86` | frontière transactionnelle dans le use case, colonne `version` |
| M3 | major | tactique | **Règle et mutation dans le contrôleur.** `preleve` mute l'agrégat sans le sauvegarder : l'endpoint ne fait rien (le commentaire l'avoue). L'instant `maintenant` vient du client. | `interfaces/http/DemandesExamenControleur.java:64-72`, `ResultatDisponibleRequete` (`req.maintenant()`, ligne 80) | use case `PreleverExamen`, horloge serveur |
| M4 | major | intégrité | **Horloge et fuseau.** Le domaine appelle `LocalDate.now(UTC)` (`domain/Serologie.java:101`, `domain/ObservationBiologique.java:79`) au lieu du fuseau du centre (V46 `centres_fuseau_horaire`). Les contrôleurs utilisent `Instant.now()` / `OffsetDateTime.now()` ; les services `LocalDate.now(UTC)` et `UUID.randomUUID()`. Une variable `aujourdhui` est inutilisée (`SerologieService.java:41`, `DemandeExamenService.java:37`). | lignes citées | port `Horloge` + fuseau du centre |
| M5 | major | tactique | **Contrat HTTP = entités de domaine** : `ResponseEntity<Serologie>` et `PagedResult<Serologie>`. Erreurs en `IllegalArgumentException` (400/500 au lieu de 404). `TypeSerologie.valueOf` est appelé sur une entrée brute. | `SerologiesControleur.java:28,37,48` ; `SerologieService.java:60` | DTO de lecture, exceptions de domaine nommées |
| M6 | major | tests/archi | **Règle ArchUnit contournable** : `callMethod(LocalDate.class,"now")` ne cible que la surcharge sans argument. `LocalDate.now(ZoneId)` passe (cas de M4). | `architecture/ArchitectureHexagonaleTest.java:28-34` | cibler toutes les surcharges (`callMethodWhere` sur le nom) |
| M7 | major | legacy | **DM1 et DM2 n'ont que le domaine** : `Constante` et `Prescription` existent sans service, sans adapter ni contrôleur. Écart sémantique : la constante legacy est lue depuis le volet médical de la séance (golden `constantes_centre_A_page`), alors que la nouvelle `Constante` est un modèle autonome (poids, TA, pouls, température), donc pas de parité possible. Ordonnances (`OrdonnanceRestController`, `ordonnance.jrxml`) : non modélisées. | `domain/Constante.java`, `domain/Prescription.java` ; aucun `*Service` correspondant | redéfinir DM1 (lecture du volet de séance, possédé par Séance ?) et DM2 dans le tactique |
| M8 | major | legacy | **Aucun filet pour DM2 à DM7** : 0 golden legacy, 0 `_nouveau_code`, 0 `_ecarts-legacy`. Seul DM1 a 4 goldens legacy (centre A, centre B vide, cross-centre 403, infirmier 403), sans contrepartie sur le nouveau code. Rien à approuver « en lot » parce que rien n'existe. La sérologie n'est caractérisée que du côté Planification (P5, `planification/p5-placement/isolement/*`). | `src/test/resources/characterization/dossiermedical/` | `/ddd-safety-net` par slice avant toute suite |
| M9 | major | doc | **La documentation client et le référentiel de règles (RG) décrivent des droits et des endpoints faux** : « `MEDECIN` (écriture), `ADMIN` (lecture) » pour les 6 contrôleurs du module, qui ne sont ni déployés ni sécurisés. Les bilans pré-greffe y sont attribués à « slice DM5 ». | `docs/reference/regles-de-gestion/16-annexe-tracabilite.md:61-62,123-126` ; `docs/client/documentation-fonctionnelle.html` | corriger : endpoints réels = legacy `/api/v1/patients/{id}/…` |
| M10 | major | code mort | Le service frontend `dossier-medical-context-api.service.ts` (commit 21f6ff7a) n'a **aucun consommateur** et pointe vers des endpoints non déployés (404 assurés s'il était utilisé). | `frontend/src/app/core/api/dossier-medical-context-api.service.ts` (aucun import hors de son `.spec`) | supprimer ou geler jusqu'au câblage |
| m1 | minor | tests | Tests **dépendants de la date** : `ObservationBiologiqueTest.java:75` (`LocalDate.now(UTC).plusDays(1)` contre le `LocalDate.now(UTC)` du domaine, risque à minuit) ; `ConstanteTest:16,21` et `PrescriptionTest:18,24,31` (`Instant.now()`, sans impact sur les assertions) ; la caractérisation constantes utilise `OffsetDateTime.now` sur des colonnes non comparées (`...CharacterizationIntegrationTest.java:56-64`). | lignes citées | horloge fixe injectée |
| m2 | minor | stratégique | Les 8 fiches DM font 13 à 24 lignes : pas de critères de bascule, pas de flag, pas de période d'observation, pas de filet, pas de consommateurs (FHIR, KDIGO, GreffeExport, volets). | `.ddd/migration/slices/dossier-medical-*.md` | compléter avant `/ddd-slice` |
| m3 | minor | stratégique | Aucune entrée `KB-DM-*` dans `known-bugs.md`, aucun ADR de slice. Normal, car aucune caractérisation n'a été faite, mais à prévoir. | `.ddd/safety-net/known-bugs.md` | — |
| s1 | suggestion | archi | Ports internes `SerologieUseCases` exposant `Instant maintenant` en paramètre : préférer un port Horloge. | `application/SerologieService.java:36` | — |

**Hors module, non re-vérifié ici** (déjà couvert par la vérification de Planification P5) : chemin legacy `SerologieRestController` → `ReaffectationEnIsolementDeLaSerologie` → `AuditActionResolver` `SEROLOGIE_*`. C'est le **seul chemin actif** ; il est dans `covered.json:59-60`. FHIR (`FhirExportService`, `FhirExportRestController`) : legacy intact, aucune caractérisation, aucune slice.

## 3. Cohérence de l'état (rien n'a été modifié)
| Slice | state.json | roadmap | fiche | Réalité constatée | État proposé |
|---|---|---|---|---|---|
| DM1 constantes | built | planned | planned | 4 goldens legacy ; nouveau = domaine seul, modèle divergent | `characterized` (partiel) |
| DM2 prescriptions | built | planned | **built** | domaine seul, ordonnances absentes, aucun filet | `planned` |
| DM3 examens | built | planned | planned | module non câblé + **legacy modifié** (B1, B2) | `planned` + revert/rattrapage |
| DM4 sérologie | built | planned | planned | module non câblé, événement no-op ; chemin réel = legacy + P5 | `planned` |
| DM5 antécédents/allergies | built | planned | built | module non câblé, aucun filet | `planned` |
| DM6 biologie | built | planned | built | idem | `planned` |
| DM7 bilan pré-greffe | built | planned | built | idem, sans transaction | `planned` |
| DM8 lectures publiées/retrait | built | planned | built | rien de retiré, garde vide de sens | `planned` |
Origine : `state.json` a été posé par 2978e639 (DM1 à DM4 `built`, DM5 `characterized`, DM6 à DM8 `planned`), puis passé en `built` ; les fiches DM2 et DM5 à DM8 ont été passées en `built` par 21f6ff7a. Constat déjà noté dans le roadmap (revue P9, M3) mais non tranché. Le résumé donné à l'appelant (« fiches characterized ») est aussi inexact : aucune fiche n'est `characterized`.

## 4. Contrôles de production (ordonnés)
1. **Avant le prochain déploiement** : vérifier que le démarrage en `validate` réussit. Sinon, revert de B1 ou migration `V50` sur `lignes_demande_examen.center_id`.
2. Si une base de dev (`ddl-auto: update`) a déjà ajouté la colonne : contrôler `SELECT count(*) FROM lignes_demande_examen WHERE center_id IS NULL`.
3. Vérifier que `contexts-dossier-medical` n'est pas embarqué dans le jar `platform` (aucun endpoint `/api/v1/dossier-medical/*` exposé ; sinon accès sans contrôle de rôle, B6).
4. Vérifier qu'aucune table `serologies`, `antecedents`, `allergies`, `bilans_pregreffe` ou `etapes_bilan_pregreffe` n'a été créée en production (le module `schema.sql` est en `IF NOT EXISTS`).
5. Journal d'audit : toujours alimenté pour `SEROLOGIE_*` et les écritures médicales legacy (inchangé).
6. Requêtes de la section sérologie existante de `verifications-production.md` (l. 1347-1353) : toujours valables (tables legacy).

## 5. Validations restantes pour l'utilisateur
1. **Décider** du sort du module : (a) le geler et le marquer non câblé ; (b) le refondre « sur place » (mêmes tables et URL legacy, ADR) ; (c) le supprimer.
2. **Décider** du revert de B1 ou de sa régularisation (filet + migration + ADR).
3. **Approuver** le retrait des 44 entrées de `covered.json` (B3).
4. **Trancher l'état** : `state.ts set slices.dossier-medical-dmN-… <état>` selon §3, et aligner les fiches DM2, DM5 à DM8.
5. Redéfinir **DM1** (constantes = lecture du volet séance : propriétaire Séance ou Dossier médical ?) et **DM2** (Ordonnance + PrescriptionMedicale : un ou deux agrégats ?) dans `.ddd/tactical/dossier-medical.md`.
6. Corriger `16-annexe-tracabilite.md` et régénérer la documentation client.
7. Le glossaire `dossier-medical.md` est `validated`, avec 12 termes en `proposed` à valider. ADR 0065 et 0067 : `accepted`.

## Verdict : **fail**
Top 3 actions :
1. Traiter B1 et B2 (production menacée au démarrage) et retirer les entrées de `covered.json` non couvertes (B3).
2. Remettre `state.json` et les fiches à l'état réel (§3) ; aucune slice DM n'est `built`.
3. Ne câbler aucun code du module avant : décision « sur place » (B5), sécurité et garde de centre (B6), audit (B7), filet par slice (M8).
