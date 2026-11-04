---
status: proposed
date: 2026-11-04
périmètre: slice patient-couverture-pc4-attestations (945b872c..8375f177)
sources: [git diff 945b872c..HEAD, check-arch, state.ts, ADR 0078, .ddd/safety-net/*, verifications-production.md §43]
relecteur: ddd-reviewer (lecture seule, aucun build ni test lancé)
---
# Revue indépendante — PC4 attestations de droits

Contrôles automatiques : `check-arch` 0 violation (0 baseline, 0 nouvelle) ; `state.ts` : PC4 = `built` (auto-attestation).

## Vérifications positives (preuves)
- **Archives octet pour octet** : les 21 `pc4/_ecarts-legacy/**/*.legacy.json` sont des renommages `R100` depuis 945b872c (`git diff -M --name-status`).
- **Aucun golden hors PC4 modifié** entre 945b872c et HEAD (seuls `.json` non-PC4 modifiés : `covered.json`, `state.json`, i18n front). Aucun `.received.json`/`.bak` suivi par git (ceux de `backend/platform/target/` sont des artefacts de build non versionnés, hors PC4).
- **Diffs archive → `_nouveau_code`** (21 fichiers comparés un à un) : uniquement 403 garde de centre, 422 `ATTESTATION_PATIENT_INTROUVABLE`, 404 `ATTESTATION_INTROUVABLE` sans diffusion (+ `patientId` réel dans `ATTESTATION_DELETED`, ADR 0078 §5), 400/415 de forme, audit `ATTESTATION_*` (+ `entity_id` à la création), jokers échappés, date de filtre illisible 400, suppression concurrente 200+404. Rien d'autre.
- **Argent reproduit** : `AttestationDroit.declarer`/`saisieSurLaFiche` (`domain/AttestationDroit.java:41-60`) = règles legacy (`git show 945b872c:…/AttestationDomainService.java` et `…/model/AttestationDroit.java`) ; début=fin, doublons, chevauchements acceptés ; suppression sous PEC validée non contrôlée. `SeanceBillingEligibilityPort` et `SeanceBillingEligibilityJdbcAdapter` : **diff vide**.
- **Recherche** : prédicats, `EXISTS` patient même centre, `LEFT JOIN` patient, tri `date_fin DESC` identiques à `AttestationSpecifications`/`PecReadQueryService` (`LectureDesAttestationsJdbc.java:30-66`).
- **Concurrence** : verrou du centre en tête dans l'unité de travail (`GererLesAttestations.java:62-86`), `SET LOCAL lock_timeout` (`UniteDeTravailSpring.java:54`), notification après commit.
- **Production** : table présente dans Flyway (`db/migration/V1__baseline_schema.sql:287`), `ddl-auto: validate` en prod (`application-prod.yml:11`) ; DDL H2 `schema.sql:170-178` = colonnes V1.
- `bc-insurance` retiré de `pom.xml`, `Dockerfile`, scripts PIT ; `seamAllowed` vide ; entrées mortes retirées de `covered.json`.

## Constats
| Sév. | Domaine | Constat | Preuve | Correction recommandée |
|---|---|---|---|---|
| major | legacy | `DashboardRestController` (legacy, `legacyPaths`) **modifié** (pas seulement supprimé) sans figurer dans `covered.json` (ni à 945b872c ni à HEAD) ; seul le registre `seams.md` le trace. Le filet existe (`compteurs`) mais l'inventaire est faux et le hook aurait dû bloquer. | `.ddd/safety-net/covered.json` (absent) ; `DashboardRestController.java:31,61` ; `seams.md:241` | Ajouter le fichier à `covered.json` ; expliquer comment le hook a laissé passer l'édition. |
| major | tests | Le changement « jour du centre » (KB-PAT-47) n'est **discriminé par aucun test** : le seul scénario du tableau de bord se saute quand jour JVM ≠ jour du centre ; `JourDuCentre` (constructeur à `Clock` prévu) n'a aucun test unitaire ; le chemin séance sans date (`create`/`updateDate`) non plus. | `EffetsEtLecteurs…IntegrationTest.java:188-189` ; `JourDuCentre.java:33` ; grep `JourDuCentre` dans `src/test` → seul `TableDeLOrganisationTest` | Test unitaire `JourDuCentre` avec `Clock` fixe à 23:30 UTC (Africa/Algiers ⇒ J+1), fuseau illisible/absent ⇒ défaut ; test du service séance avec date nulle. |
| major | stratégique | Écarts métier (422 rattachement, ARGENT point 11) décidés « par délégation à l'assistant », pas par les experts métier ; la slice ne peut pas être basculée/considérée validée avant confirmation. | ADR 0078 en-tête `decision-makers` ; §11 et Conséquences | Faire confirmer KB-PAT-39/41/46/47 par les métiers ; tracer la date dans l'ADR. |
| minor | legacy | `seams.md` ne mentionne que « séance créée sans date » alors que `updateDate` est aussi modifié (l'ADR §9 le dit). | `SeanceApplicationService.java:108-110` ; `seams.md:241` | Aligner la ligne du registre. |
| minor | sécurité | La garde de centre (403) est évaluée **après** la lecture du corps en création/recherche : un appel d'un autre centre avec corps illisible reçoit 400, pas 403 (pas de fuite de données). Le « corps lu après l'autorisation » ne vaut que pour le rôle (filtre). | `AttestationsControleur.java:92-94, 120-121` | Accepter (documenter) ou garder le centre avant `CorpsJson.lire` si `centerId` est en paramètre. |
| minor | tests | Rejeu PG16 : pas de course réelle **attestation** (suppressions simultanées 200+404) sur PostgreSQL ; le 200+404 n'est prouvé que sur H2. | `patient-couverture-pc4-pg16-rejeu.sh:51-57` ; tests ajoutés au contrat sans parallélisme | Ajouter au contrat PG un test de deux suppressions concurrentes. |
| minor | archi | `JourDuCentre` (JDBC + `@Component`) est rangé dans `application/centre` de la plateforme et lit `centers` en SQL (liste blanche transitoire sans date de retrait). | `JourDuCentre.java:1-20` ; `TableDeLOrganisationTest.java:47-49` | Déplacer en `infrastructure/`, fixer l'échéance (organisation.v1) dans le plan. |
| minor | stratégique | Glossaire : doublons contradictoires (`AttestationDeDroits`/`bc-insurance` l.19 vs `AttestationDroit` l.43 ; `HorlogeDesCentres` l.37 vs `JourDuCentre` l.48) ; nouvelles lignes sans colonne Statut (`proposed` absent). | `.ddd/strategic/glossaries/patient-couverture.md:19,37,43,48` | Fusionner, retirer `bc-insurance`, poser `proposed`. |
| minor | legacy | Dette « `bc-assure` conservé pour l'import » imprécise : l'écrivain hors verrou de `attestation_droit` est `HistoricalRecordJdbcAdapter` (plateforme) ; `bc-assure` ne référence pas les attestations. | `HistoricalRecordJdbcAdapter.java:30` ; grep `attestation` dans `bc-assure` = 0 | Corriger ADR 0078 Conséquences / fiche ; dater le retrait. |
| minor | prod | §43 ne vérifie pas les fuseaux des centres alors que KB-PAT-47 en dépend ; requêtes 5-6 utilisent `current_date` (fuseau de la session PG, pas du centre). | `verifications-production.md:1777-1780` | Ajouter `SELECT id, name, fuseau_horaire FROM centers` + `SHOW timezone` et le fuseau de la JVM de prod. |
| nit | tactique | Identité redevenue primitive (`UUID id`) : `AttestationDroitId` supprimé. | `domain/AttestationDroit.java:20` | Réintroduire l'ID typé (suggestion). |
| nit | tactique | Règle « patient du centre » portée par le use case (`RegleDePatientViolee`) — acceptable (multi-agrégats) mais à documenter comme politique. | `GererLesAttestations.java:64-66` | Suggestion. |

## Verdict : **pass-with-actions**
État réel proposé : **`built` confirmé** (parité vérifiée sur les goldens, legacy retiré, argent identique), **pas** `validated`/`done` tant que les 3 majors ne sont pas levés et que les métiers n'ont pas confirmé.

### Top 3 actions
1. Tests du jour du centre (`JourDuCentre` avec `Clock`, séance sans date) indépendants du jour de la JVM.
2. `DashboardRestController` dans `covered.json` + registre `seams.md` aligné (`updateDate`).
3. Confirmation métier des écarts ADR 0078 (422, argent §11, jour du centre).

### Contrôles de production PC4 (ordre)
1. `SHOW timezone` PG, fuseau JVM du conteneur, `SELECT id, fuseau_horaire FROM centers` (valeurs valides).
2. §43-8 : comptes ADMIN actifs sans centre (seront refusés 403).
3. §43-1 : attestations orphelines / autre centre.
4. §43-7 : centres sans `planning_parametres` (409 sur première écriture concurrente).
5. §43-2 : périodes inversées (attendu 0).
6. §43-3 / §43-4 : doublons et chevauchements (argent, à présenter aux métiers).
7. §43-5 / §43-6 : échues comptées « à échéance », PEC validées sans attestation couvrante.
8. Après bascule : journal d'audit `ATTESTATION_*`, absence de 500 sur `/api/v1/pec/attestations**`, compteurs du tableau de bord autour de minuit centre.
