---
status: proposed
date: 2026-11-02
scope: contexte seance (SE1–SE7), vérification globale, code NON REVU (commit 2978e639 du 2026-11-01, « feat(pec) »)
sources: [tools/ddd-scan/check-arch.ts, tools/ddd-scan/state.ts show, .ddd/migration/roadmap.md §1 nonies, .ddd/migration/slices/seance-se*.md, backend/contexts/seance/**, backend/platform/pom.xml, db/migration V1/V21/V22, TableDesSeancesTest, FrontieresDesContextesTest, ADR 0002/0062]
---

# Vérification globale — contexte Séance (SE1–SE7)

## 0. Contrôles automatiques
| Contrôle | Résultat |
|---|---|
| `check-arch.ts` | 0 violation (0 baseline, 0 nouvelle) — **non probant** : le module n'est pas assemblé dans l'application (cf. B1) |
| ArchUnit `contexts/seance/.../ArchitectureHexagonaleTest` | présent (106 l.), non exécuté ici (consigne : aucun build) |
| `FrontieresDesContextesTest:150-160` | règle Séance en `allowEmptyShould(true)`, commentée « squelette » : cohérente avec un squelette, ne prouve rien |
| `TableDesSeancesTest` (garde SE7) | **inerte** (cf. M1) |
| Suite de caractérisation Séance | **inexistante** : pas de `characterization/seance` (java ni resources) |

## 1. Vérité sur le code
Module `backend/contexts/seance` : 29 fichiers main + 4 tests, **1 256 lignes au total**, ajoutés en bloc par 2978e639 (327 fichiers, 16 233 lignes, message sans rapport). Le code n'est **pas déployé** : `backend/platform/pom.xml:45-80` ne dépend pas de `contexts-seance` ; aucune classe hors module ne l'importe. Le legacy Séance est **intact** : `bc-seance` (58 fichiers main), `SeanceRestController` (1 315 l.), `SeancePlanningJdbcAdapter`, `SeancePlaceJdbcAdapter`, `SeanceRaccourciJdbcAdapter`, `SeanceBillingEligibilityJdbcAdapter` (`SeanceFacturationJdbcAdapter` n'existe pas sous ce nom).

| Slice | Code réel | Filet / goldens | Legacy retiré | Verdict |
|---|---|---|---|---|
| SE1 création | `Seance.creer`, `SeanceService.creer`, `SeancesJdbc`, `PecValidationJdbc` ; incompatible avec le schéma (B3) | aucun | non | non construit (squelette) |
| SE2 validation/signature | `valider`/`signer` sans horodatage ni auteur (`validated_at`, `signed_*` ignorés) | aucun | non | non construit |
| SE3 consommables (ADR 0047) | **zéro ligne** (pas de `ConsommableDeclare`, pas d'appel à `ConsommationsDeSeance`) | aucun | non | non commencé |
| SE4 dérogation/régularisation | agrégat `DerogationRegularisation` + table V22 en double (B2) ; déverrouillage non persisté (B3) | aucun | non | non construit |
| SE5 forfait surchargé | non persisté (B3), prix nul accepté (M4) | aucun | non | non construit |
| SE6 verrou facturation | écouteur `FacturationValideeEvent` → UPDATE d'une colonne inexistante (B3) | aucun | non | non construit |
| SE7 lectures publiées | aucun port publié ; 18 lecteurs SQL de `seances` hors contexte (M1) | garde inerte | non | non commencé |

## 2. Constats
| Sév. | Domaine | Constat | Preuve | Correction recommandée |
|---|---|---|---|---|
| **blocker** | legacy/données | **Deux migrations Flyway V22** : `V22__derogation_regularisation.sql` (ajout 2026-11-01, 2978e639) et `V22__fiche_article_complete.sql` (2026-10-05, déjà appliquée). Flyway s'exécute au démarrage ⇒ « more than one migration with version 22 » ⇒ **l'application entière ne démarre plus** sur cette branche, tous contextes confondus. Une V22 insérée après V23..V49 serait de toute façon hors ordre | `backend/platform/src/main/resources/db/migration/V22__*.sql` (2 fichiers) ; plus haute version : V49 | Retirer `V22__derogation_regularisation.sql` (le module n'est pas déployé) ou le renuméroter > V49 seulement quand SE4 sera réellement livrée. À faire **avant tout déploiement** |
| **blocker** | archi/legacy | Module non déployé mais déclaré `built` partout (state, 5 fiches sur 7) : auto-attestation sans parité, sans filet, sans revue | `backend/pom.xml:41` (module dans le réacteur) vs `backend/platform/pom.xml` (aucune dépendance) ; `state.ts show` SE1–SE7 `built` | Rétablir l'état (§4) ; décider : retirer le module du réacteur ou le garder comme brouillon explicitement marqué |
| **blocker** | tactique/données | Persistance incompatible avec la table réelle : `pec_id`, `type_seance`, `verrouille_facturation` **n'existent pas** dans `seances` ; `trouver` ne lit pas le verrou (exception avalée ⇒ toujours `false` ⇒ verrou contourné) ; forfait surchargé et déverrouillage jamais écrits (`forfait_override_*`, `regularisation_deverrouillee_*` ignorés) ; `StatutSeance` sans `FACTUREE`/`ABSENT` ⇒ `valueOf` plante sur les lignes réelles | `SeancesJdbc.java:33-37,45,52-62,94` ; `V1__baseline_schema.sql:1219-1239` ; `V21:6-9` ; `StatutSeance.java:3-6` vs `SeanceStatus.java:3-12` | Repartir des fiches avec un filet de caractérisation ; mapper sur les colonnes réelles via ACL de transition (ADR 0002 : `ABSENT` ↔ exclue) |
| major | archi/legacy | Garde SE7 `TableDesSeancesTest` inerte : ne scanne pas `platform/src/main` et exclut `/acl/`, `/persistence/` ⇒ invisibles : `SeanceRestController`, `Direction*QueryService` (3), `PatientStatsQueryService`, `CahierDialyseReportService`, `MigrationRollbackJdbcAdapter` et 11 ACL/persistence de Facturation, Absences, Planification, Stock. Test négatif sur répertoire synthétique seulement | `TableDesSeancesTest.java:34-38,63-76` | Scanner aussi `platform/src/main/java`, ne pas exclure `/acl/` ni `/persistence/`, liste blanche nominative datée des 18 lecteurs |
| major | archi | Si le module est câblé : `@RequestMapping("/api/v1/seances")` en double avec le legacy ⇒ mapping ambigu au démarrage | `SeancesControleur.java:20` ; `SeanceRestController.java:38` | Remplacement sur place (mémoire « pas de Strangler ») : mêmes URLs, un seul contrôleur |
| major | sécurité | Aucun `@PreAuthorize`, centre fourni par le client (`@RequestParam centreId`, corps) ⇒ pas de garde de centre, IDOR inter-centres ; legacy : rôles par endpoint | `SeancesControleur.java:37-99` vs `SeanceRestController.java:67,114,170` | Centre issu du contexte de sécurité, rôles alignés sur le legacy |
| major | tactique/argent | Forfait : `prixHt` null ⇒ 0 silencieux ; `montantSurcharge` reçu puis ignoré ; auteur par défaut `"system"` ; re-surcharge ignorée sans erreur (le legacy remplace le forfait) | `ForfaitApplique.java:20` ; `SeancesControleur.java:91-97` ; `Seance.java:89-90` | Refuser un prix absent ; auteur = utilisateur authentifié ; caractériser la re-surcharge legacy |
| major | tactique | Verrou SE6 posé par UPDATE de masse hors agrégat sur **toutes** les séances de la période, sans filtre des séances facturées ni des exclues (`ABSENT`) ; RG-ABS-012 (séance réalisée) et sorties FEFO Stock non traités | `SeancesJdbc.java:93-95` ; `VerrouillageFacturation.java:17-19` | Verrouiller les séances facturées de la facture (identifiants publiés dans l'événement) ; décision écrite |
| major | archi | `PecValidationJdbc` lit en SQL `prise_en_charge` (table de Patient et couverture) au lieu d'un port publié (`PECRepository` prévu par la fiche SE1) | `PecValidationJdbc.java:17-18` ; `seance-se1-creation.md` « Prérequis » | Port publié de Patient et couverture |
| major | tactique | Horloge non injectée, fuseau UTC au lieu de celui du centre (le legacy prend le fuseau de la JVM : divergence entre 00:00 et 01:00 heure locale) | `SeanceService.java:45,64,78` ; `DerogationRegularisationService.java:39` ; `SeancesControleur.java:56,96-97` ; `Seance.java:97-98` | Port `Horloge` + fuseau du centre, conforme au reste des contextes |
| major | tests | Seulement 3 classes de tests de domaine ; aucun test application/adapter/caractérisation/parité ; invariants SE3/SE6/SE7 sans test | `contexts/seance/src/test/**` | Filet de caractérisation Séance d'abord (règle legacy-safety n°5) |
| minor | tactique | Événements `SeanceCreee`, `ForfaitDeSeanceSurchargé` jamais publiés ; identifiant non ASCII | `publishedlanguage/*.java` (aucune référence ailleurs) | Publier ou supprimer ; renommer en ASCII |
| minor | tactique | Primitive obsession (`typeSeance`, auteurs en `String`), trois constructeurs publics télescopiques, `verrouillerFacturation()` sans garde, `supprimer` (DELETE) au repository sans use case | `Seance.java:16,21,24-42,80-82` ; `SeancesJdbc.java:66-68` | VO, fabrique de reconstitution unique, retirer DELETE |
| minor | archi | Le domaine `Seance` est sérialisé tel quel en HTTP ; trois routes synonymes `deverrouiller`/`debloquer`/`unlock` | `SeancesControleur.java:54-86` | DTO de réponse ; une seule route (celle du legacy) |
| minor | stratégique | `tactical/seance.md` en `status: proposed` alors que le gate `tactical` est `approved` | `.ddd/tactical/seance.md:2` | Confirmer à l'utilisateur |
| nit | tests | `OffsetDateTime.now()` dans les tests (n'influe pas sur le résultat) ; noms anglais `should_…` | `SeanceTest.java:18-56` | Date fixe ; noms en langage omniprésent |

Points vérifiés sans écart : `check-arch` vert ; aucun KB-SEA dans `known-bugs.md` (normal, rien n'est caractérisé) ; `covered.json` liste des fichiers legacy Séance (l. 21-27, 45-47, 65-67, 89) couverts par les filets d'autres contextes, `seamAllowed` vide ; glossaire `seance.md` `validated` ; ADR 0062/0067 `accepted` ; documentation client : aucun comportement Séance nouveau à documenter. Jalons hérités (Planification port `attenduLe` / règle de présence recopiée dans `SeancePlanningJdbcAdapter`, colonnes de place de `patients` dans `SeanceRestController`, roadmap l. 228-236, 263-266) : **tous ouverts**.

## 3. Parité et goldens
Aucun golden Séance, ni `_nouveau_code`, ni `_ecarts-legacy` : rien à approuver, rien n'a pu être approuvé en lot. Les goldens contenant « seance » appartiennent à Absences, Facturation, Planification, Stock.

## 4. Cohérence de l'état (faux → réel proposé ; rien n'est modifié)
| Élément | Actuel | Réel proposé |
|---|---|---|
| `state.json` slices `seance-se1..se7` | `built` (posé par 2978e639) | `planned` (aucun filet ⇒ pas `characterized`) |
| Fiches SE1, SE3, SE4, SE6, SE7 | `built` | `planned` |
| Fiche SE2 | `characterized` | `planned` |
| Fiche SE5 | `planned` | `planned` (juste) |
| Roadmap §1 nonies | `planned` | juste |
| `contexts.seance` | `architected` | juste (sous réserve du point `tactical/seance.md` `proposed`) |
Commandes (à lancer par l'utilisateur) : `state.ts set slices.seance-se<N>-<nom> planned` pour N = 1..7.

## 5. Contrôles de production (ordonnés) et validations restantes
1. **Avant tout déploiement de la branche** : supprimer ou renuméroter `V22__derogation_regularisation.sql`, puis en production `SELECT version, description FROM flyway_schema_history WHERE version = '22';` (attendu : 1 ligne « fiche article complete »). Sinon : la base reste inchangée, mais l'application ne démarre pas.
2. Vérifier qu'aucun environnement n'a la table `derogations_regularisation` (`SELECT to_regclass('public.derogations_regularisation');`, attendu : NULL) ; si elle existe, ne rien supprimer sans validation écrite.
3. Pas d'autre contrôle Séance à faire : aucun comportement Séance n'a changé en production. Ne pas ajouter de section Séance à `verifications-production.md` tant qu'une slice n'est pas vraiment livrée.

Décisions pour l'utilisateur : (a) l'état réel des slices (§4) ; (b) le sort du module squelette : le retirer du réacteur ou le garder comme brouillon non câblé ; (c) confirmer `tactical/seance.md` ; (d) la même incohérence d'état pour `organisation`, `patient-couverture`, `dossier-medical` et `pilotage-direction` (même commit, roadmap l. 242-246) appelle la même vérification ; (e) traiter `ABSENT` (ADR 0002 : à retirer du modèle cible) avec Facturation F2 et Absences, qui en dépendent.

## Verdict : **fail**
Top 3 actions : 1) supprimer ou renuméroter la V22 en double (bloque le démarrage de toute l'application) ; 2) remettre SE1–SE7 et leurs fiches à `planned` puis décider du sort du squelette ; 3) reprendre la Séance dans l'ordre du pipeline : filet de caractérisation (`/ddd-safety-net` SE1) avant tout code, et réparer d'abord la garde `TableDesSeancesTest`.
