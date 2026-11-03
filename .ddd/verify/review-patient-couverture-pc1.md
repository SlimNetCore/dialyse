---
status: proposed
date: 2026-11-03
scope: slice patient-couverture-pc1-identite-mouvements (321275cb..5787f6d4)
sources: [git diff 321275cb..HEAD, ADR 0076, .ddd/safety-net/known-bugs.md, .ddd/migration/verifications-production.md §41, seams.md, covered.json]
reviewer: ddd-reviewer (lecture seule ; aucun build ni test lancé)
---

# Revue indépendante — PC1 identité et mouvements

## Contrôles automatiques
- `check-arch` : 0 violation (0 en baseline, 0 nouvelle).
- `state.ts show` : `patient-couverture-pc1-identite-mouvements: built`.
- `scan --incremental` : 2853 fichiers, 25 clusters (pas de nouveau cycle signalé).

## Parité des goldens (vérifiée)
- Les 47 archives `pc1/_ecarts-legacy/**/*.legacy.json` et `_ecarts-legacy/list_assures_cross_centre_adminB.legacy.json` sont des renommages git **R100**, donc des blobs identiques à 321275cb. L'archive P5 `avant_pc1.legacy.json` est identique octet pour octet (`cmp`) à `git show 321275cb:…/requetes_invalides_nouveau_code.approved.json`.
- Aucun golden `assure/`, `pec/`, PC2, PC3 ou P5 n'a changé de contenu hors liste : le seul `M` sous `characterization/` est le golden P5 `requetes_invalides_nouveau_code.approved.json` (500→400 ×4, 400→404 patient inconnu : écart décidé). La restauration de `assure/` et `pec/` est donc propre. Le seul autre changement PC2 est le renommage du golden `cross_centre_refuse` en `_nouveau_code` (`AssurePrincipalCharacterizationIntegrationTest.java:172`).
- Les 47 couples archive ↔ `_nouveau_code` diffèrent tous ; j'ai relu chaque diff (`diff -U0`). Ils se rangent dans les écarts décidés (403 de garde ou de rôle, 400 au lieu de 500, 404, 422 état fermé ou date requise ou naissance, unicité normalisée, size ≤ 100, `etatPatient` nul → `PERMANENT`, KB-PAT-05), **à quatre exceptions près, qui ne sont pas dans la liste du 02/11** (voir M1, M2, m1, m2).

## Constats

| # | Sévérité | Domaine | Constat | Preuve | Correction recommandée |
|---|---|---|---|---|---|
| M1 | major | legacy / sécurité | **Les droits de rôles connus changent sans que ce soit décidé ni testé.** Dans le legacy, les lectures (`GET /{id}`, `/search`, `/summary`, `/summary/details`, `/assures`, `/{id}/assures/history`) n'avaient aucun `@PreAuthorize` : tout utilisateur authentifié y avait accès. Les écritures d'assuré utilisaient `!hasRole('MEDECIN') or hasAnyRole(...)`, donc les rôles personnalisés étaient acceptés. Or `PHARMACIEN` et `DIRECTEUR_ADMINISTRATIF` sont des rôles réels (46 et 27 usages dans le backend), non limités par `RoleScopeFilter`. Ils passent de 200 à **403** en lecture et en écriture d'assuré. Les matrices ne testent pas ces deux rôles. ADR 0076 §6 affirme « droits des rôles connus inchangés » : c'est faux pour eux. | legacy `git show 321275cb:…/PatientRestController.java` l.46, 79, 179, 186, 307, 329, 357 ; `SecurityConfig.java:80` ; `PatientsControleur.java:56-57` ; `matrice_POST_search_nouveau_code.approved.json` (pas de PHARMACIEN ni de DIRECTEUR_ADMINISTRATIF) | Faire trancher par l'utilisateur (garder le 403 ou ajouter ces rôles en lecture). Ajouter les deux rôles aux matrices et corriger ADR 0076 §6. |
| M2 | major | legacy | **`PUT` : le numéro d'assurance du corps est désormais écrit** (`numero_assurance en base` passe de `ASS-P3-P1` à `PC1-AUTRE-NUMERO`). La fiche de slice le classe encore « à valider en S3 » et KB-PAT-03 le passe à « Corrigé » sans trace de validation. Effet de bord possible : la normalisation (TRIM et majuscules) réécrit le numéro de lignes existantes au premier `PUT`, alors que le numéro relie la fiche aux tables `assure` et `assure_patient` (PC2). | `numero_d_assurance_ignore` (diff) ; `PatientsJdbc.java:48` ; fiche de slice §« Écarts de détail à valider en S3 » ; `known-bugs.md` KB-PAT-03 | Obtenir une décision écrite. Vérifier en recette qu'un renumérotage ne casse pas l'affectation d'assuré (`assure_patient.numero_assurance`). |
| M3 | major | concurrence | **Unicité du numéro et ordre global des verrous non garantis pour une fiche sans place.** `PlacerLePatient.verifier` sort **sans prendre de verrou** quand aucune place n'est demandée (« Une fiche sans place est acceptée sans verrou lors du contrôle »). Conséquences : (a) `trouverLeDetenteurDuNumero` s'exécute sans verrou, donc deux `POST` ou `PUT` simultanés avec le même numéro passent tous les deux (aucune contrainte SQL) ; (b) en modification, `patients.enregistrer` verrouille la fiche **avant** que `place.placer` ne prenne le verrou du centre. L'ordre devient fiche → centre, inverse de l'ADR 0061, d'où un risque d'interblocage avec P5, P7 ou Absences (rattrapé en 409, mais en contradiction avec ADR 0076 §5). Aucun test de concurrence réel n'existe (seul un double qui lève `EcritureConcurrente`). | `PlacerLePatient.java:26-28, 59-60, 91` ; `AdmettreUnPatient.java:41-49` ; `ModifierLaFiche.java:47-56` ; `FicheDuPatientTest.java:146` | Prendre le verrou du centre **inconditionnellement** en tête de l'unité de travail de la fiche (port `VerrouDuCentre`, ou `verifier` qui verrouille toujours), puis ajouter un test d'intégration PG16 de deux admissions concurrentes avec le même numéro. |
| M4 | major | règles / données | **Un état non nul hors liste (`'BIDON'`, `'permanent'`) est relu comme `PERMANENT`**, pas seulement l'état nul (seul cas décidé). Un `PUT` sans état réécrit alors silencieusement la colonne en `PERMANENT`. Une sortie mal orthographiée redevient un patient actif, donc plaçable. | `PatientsJdbc.java:164-171` ; `Patient.java` `reconstituer` ; §41 requête 2 | Décision métier. À défaut : refuser la lecture ou l'écriture (409 ou 422) tant que la ligne n'est pas corrigée. Lancer la requête §41-2 **avant** la mise en production. |
| m1 | minor | legacy | La ligne héritée (état nul) **ne crée plus le mouvement `REPRISE`** au `PUT` (`mouvements` : 1 ligne → `[]`). Conséquence logique de « nul = PERMANENT », mais ce n'est écrit ni dans ADR 0076 ni dans KB-PAT-09. | diff `une_ligne_heritee…/mouvements` | Le mentionner dans KB-PAT-09, RG-PAT-041 et ADR 0076 §10. |
| m2 | minor | legacy | `qualiteAssure` nul est rendu `ASSURE_LUI_MEME` (signalé « à valider en S3 », sans trace de validation). | fiche de slice §S2 | Faire valider avec M2. |
| m3 | minor | tests | Le filet ne couvre plus le chemin « vacancier **avec** attestation » : le scénario sans date échoue maintenant en 422 avant d'atteindre l'attestation (VacancierAvec disparaît de `attestations_ecrites`). De même, « occasionnel sans fin d'attestation » ne vérifie plus le 400 « Attestation obligatoire » à la création. | diffs `l_attestation_n_est_exigee_que_hors_/*` | Ajouter une date d'évènement à ces scénarios `_nouveau_code` pour réexercer la règle d'attestation. |
| m4 | minor | tests | L'assomption « jour JVM = jour du centre » reste en place alors que KB-PAT-17 est corrigé : le scénario est ignoré en silence autour de minuit. | `PatientCouvertureCharacterizationSupport.java:51-52` | Retirer l'assomption ou figer l'horloge. |
| m5 | minor | legacy | `covered.json` contient **16 entrées mortes** (fichiers supprimés : `PatientRestController`, `PatientApplicationService`, DTO, etc.). Un fichier recréé à ces chemins serait éditable sans filet. | `.ddd/safety-net/covered.json` (contrôle `fs.existsSync`) | Retirer ces entrées (via le processus habituel). |
| m6 | minor | archi | `TableDuPatientCouvertureTest` est toujours `@Disabled` avec un motif périmé (« GELE… à réécrire au filet PC1 ») ; la liste blanche de 25 fichiers n'est qu'une documentation, sans garde exécutable. Dette acceptable si datée PC5, mais le motif doit être mis à jour. | `TableDuPatientCouvertureTest.java:24-26` ; `safety-net/patient-couverture-pc1…md:56-58` | Mettre à jour le motif, ou activer dès maintenant une garde « pas de nouveau lecteur hors liste blanche ». |
| m7 | minor | archi | Les sources gelées Assure, Pec et Attestation (28 `excludes`) restent dans `src/main` : code mort compilé par l'IDE, glob `**/domain/Assure*.java` assez large pour exclure un futur fichier légitime. C'est une dette **acceptable** car consignée (GEL.md, ADR 0076), avec retrait prévu de PC2 à PC4. | `contexts/patient-couverture/pom.xml:99-132` | Garder, mais déplacer ces sources hors de `src/main` (par ex. `frozen/`) si PC2 tarde. |
| m8 | minor | front | `loadAllPatients` demande `size: 200`, ce qui donne maintenant 400. Le mode `local` n'est pas utilisé aujourd'hui (`dataMode = 'remote'`), mais c'est une régression latente. | `patient-list.store.ts:162` ; `patient-list.component.ts:257` | Passer à 100. |
| n1 | nit | tactique | Le port sortant `NotificationsDeLaFiche` est rangé dans `publishedlanguage` ; c'est un port, pas du langage publié. La notification est appelée depuis le contrôleur (comme dans le legacy). | `PatientsControleur.java:10, 101, 113` | Le déplacer dans `application` lors de PC5. |
| n2 | nit | données | Après PC1, les fiches existantes en séjour limité sans date, ou avec une naissance après l'admission, ne peuvent plus être enregistrées (422 à chaque `PUT`) tant qu'on ne les corrige pas. C'est cohérent avec la décision, mais à annoncer aux secrétaires. | `Patient.java` `verifier` ; §41 requêtes 3 et 4 | Communiquer le volume relevé par §41 avant la mise en production. |

Points vérifiés sans constat :
- Garde de centre sur toutes les lectures et écritures, résumé compris. Le mois est résolu après la garde et avant le cache, avec une clé par centre et par mois (`ConsulterLEffectif.java:39-45`, `ResumesEnCache`).
- 403 avant la lecture du corps (`byte[]` puis `CorpsJson` après `@PreAuthorize`).
- La liste blanche d'écriture de fiche (`ADMIN`, `SECRETAIRE`, `INFIRMIER`) est identique au legacy `ECRITURE_FICHE_PLACEMENT`, donc `COMPTABLE` reste en 403.
- Les notifications sont identiques au legacy (nom et prénom déjà diffusés avant). Aucun log de données de santé dans le module.
- `lock_timeout` est posé à l'entrée de l'unité, et 55P03 ou un interblocage donnent 409 (`UniteDeTravailSpring.java`).
- `piecesJointesJson` reste nul (`FicheReponse.java:64`, KB-PAT-19).
- Les 32 scénarios inchangés couvrent le JSON de la fiche et l'ordre des listes.
- Docs : RG-PAT-031 à 041 présentes, KB-PAT-01 à 19 avec statuts, glossaire en `proposed`, GEL.md levé, §41 en SELECT uniquement, documentation client régénérée.

## État proposé (non posé)
`built` reste l'état réel : le code est fusionné et le legacy supprimé, mais ADR 0076 est `proposed`, M1 à M4 n'ont pas de décision et le §41 n'a pas tourné. Passage à `verified` seulement quand M3 est corrigé, M1, M2 et M4 sont décidés et le §41 est fait.

## Contrôles de production (ordre)
1. §41-2 : états hors liste (M4). Ce contrôle décide d'un nettoyage avant la mise en production.
2. §41-1 : doublons `UPPER(TRIM(numero_assurance))` par centre (M3, M2).
3. §41-6 : divergence assuré lui-même / numéro (M2).
4. §41-3 et §41-4 : séjours limités sans date, naissances incohérentes (n2).
5. §41-5 : lignes héritées nulles (m1).
6. §41-7 et §41-8 : patients sans `ADMISSION`, types de mouvement hors liste.
7. Recette : connexion `PHARMACIEN` et `DIRECTEUR_ADMINISTRATIF` sur les écrans patients (M1) ; deux enregistrements simultanés du même numéro (M3).

## Verdict : **pass-with-actions**
Top 3 :
1. Verrouiller le centre inconditionnellement dans `AdmettreUnPatient` et `ModifierLaFiche`, et ajouter un test PG16 concurrent (M3).
2. Faire décider M1 (rôles `PHARMACIEN`, `DIRECTEUR_ADMINISTRATIF`), M2 (`PUT` du numéro) et M4 (état inconnu → `PERMANENT`), puis corriger ADR 0076 §6, §5 et §10.
3. Lancer §41-2 et §41-1 en recette avant la mise en production ; réexercer les chemins d'attestation (m3) ; nettoyer `covered.json` (m5).
