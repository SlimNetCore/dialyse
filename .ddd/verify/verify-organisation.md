---
status: proposed
date: 2026-11-02
scope: contexte organisation (O1–O5)
sources: [check-arch.ts, state.ts show, git 2978e639/54053d14/b2019e3f/9e9072b7/e7cb8826/90533e59/b365a63a, backend/contexts/organisation, .ddd/migration/slices/organisation-*, .ddd/safety-net/organisation-*]
reviewer: relecteur indépendant (lecture seule, aucun build ni test exécuté)
---

# Vérification — contexte Organisation (O1–O5)

Les slices ont été construites hors de la chaîne de revue. Elles sont donc traitées ici comme **non revues**.
Aucun test n'a été lancé : les « verts » cités ci-dessous viennent des fiches et n'ont **pas été reproduits**.

## 0. Contrôles automatiques
| Contrôle | Résultat |
|---|---|
| `check-arch.ts` | 0 violation (0 en baseline, 0 nouvelle) |
| ArchUnit `ArchitectureHexagonaleTest` (contexte) + `FrontieresDesContextesTest:162-171` | règles présentes, non exécutées |
| `TableDeLOrganisationTest` | la garde est **vide de sens** (cf. M3) |

## 1. Vérité du code (ce qui existe réellement)
| Slice | Code présent | Filet de caractérisation | Legacy |
|---|---|---|---|
| O1 Sociétés/centres | `OrganisationService` (créer, modifier, activer, désactiver, transférer, logos, pagination), `SocietesControleur` (`/api/v1/organisation/societes` + `/api/v1/societes`), `CentresControleur` | **aucun** dossier `characterization/organisation`. Preuve de bascule revendiquée : `SocieteIntegrationTest` 12/12 (test legacy inchangé depuis 10606b66), non reproduite | `SocieteRestController` et adapters supprimés (b365a63a) |
| O2 Salles/créneaux | `SallesControleur` (ouvrir/fermer), `CreneauxControleur`, `SallesJdbc`, `CreneauxJdbc` | aucun (fiche O2 `planned`) | lecture via `ReferentielsLegacyControleur` |
| O3 Référentiels | CRUD médecins, machines et protocoles (`ReferentielsControleur`), lectures communes en lecture seule (`ReferentielsCommunsJdbc`) | aucun golden | `ReferentialRestController` supprimé |
| O4 Lectures publiées | 7 types dans `publishedlanguage/`, **aucun consommateur ni publication** (les événements `SalleOuverte`, `SalleFermee` et `CreneauCree` ne sont jamais émis) | — | 42 sources hors du contexte lisent encore les tables en SQL |
| O5 Retrait `bc-referential` | modules `bc-referential` et `bc-migration` supprimés ; admin et migration relocalisés en `platform/infrastructure` | `.ddd/safety-net/organisation-o5…md` = liste de tests legacy existants, aucun golden | `SocieteJpaEntity`/`CenterJpaEntity`, `ReferentialAdminTables` restants |

## 2. Constats
| Sév. | Domaine | Constat | Preuve | Correction recommandée |
|---|---|---|---|---|
| blocker | archi/sécurité | **Écritures d'organisation ouvertes à tout utilisateur authentifié.** Créer, modifier ou transférer un centre, ouvrir ou fermer une salle, faire le CRUD des médecins, machines et protocoles : aucun `@PreAuthorize` et aucune garde de centre. Seul `/api/v1/organisation/**` → `anyRequest().authenticated()` s'applique. Le legacy réservait ces opérations au SUPERADMIN (`SocietesControleur:44`). Le module est câblé (`platform/pom.xml:75-80`, scan `com.hemodialyse.backend`) | `CentresControleur.java:25-40`, `ReferentielsControleur.java:38-93`, `SallesControleur.java:22-27`, `CreneauxControleur.java:24-29`, `SecurityConfig.java:82` | `@PreAuthorize` par endpoint (SUPERADMIN ou ADMIN du centre) + `CenterAccessGuard.requireCenter` ; test d'autorisation 403 par rôle |
| blocker | archi/legacy | **Rupture probable en production.** `SallesJdbc` lit et écrit `salle.ouverte`, et `MachinesJdbc`/`ProtocolesJdbc` utilisent les tables `machine`/`protocole`. Ces trois éléments n'existent que dans `db/schema.sql` (H2). Aucune migration Flyway ne les crée, et le profil prod n'utilise que Flyway. La façade legacy `/api/v1/referentials/salles` passe désormais par `SallesJdbc` : sa lecture échouerait en SQL | `SallesJdbc.java:23,32,42` ; `ReferentielsLegacyControleur.java:51-56` → `OrganisationService.java:221-222` ; `V1__baseline_schema.sql:1207-1213` (pas de `ouverte`) ; `application-prod.yml:4-14` | Migration Flyway (ajouter `salle.ouverte`, créer `machine` et `protocole`) **ou** retour à la requête legacy `SELECT id, code, nom FROM salle` ; test contractuel sur Postgres (Testcontainers) |
| blocker | tests/legacy | **Legacy supprimé sans filet de caractérisation.** Il n'y a ni golden ni `_nouveau_code`, et le dossier `characterization/organisation` n'existe pas. La parité ne repose que sur `SocieteIntegrationTest` (assertions legacy jamais adaptées) et sur des affirmations dans la fiche | `find …characterization` (absent) ; `b365a63a` supprime `SocieteRestController`, `ReferentialRestController` et `bc-referential` | Caractérisation HTTP (sociétés, centres, `/referentials/*`) sur le commit `b365a63a^`, puis rejouée sur le nouveau code ; consigner les écarts |
| major | tactique | Invariant « au moins un centre actif par société » vérifié par lecture puis écriture, **sans transaction ni verrou** (aucun `@Transactional`/`TransactionTemplate` dans le contexte). Deux désactivations concurrentes peuvent toutes deux passer | `OrganisationService.java:81-93,96-109,129-132` | Transaction avec `SELECT … FOR UPDATE` sur la société, ou verrou optimiste ; test de concurrence |
| major | tactique | Règles métier dans le service applicatif (dernier centre actif, même société, société inactive, unicité des codes). `Centre.setActif` est un setter anémique | `OrganisationService.java:83-106,111-127` ; `Centre.java:75` | `Centre.desactiver()`/`activer()` ; un agrégat `Societe` (ou un service de domaine) qui porte la règle du dernier centre |
| major | archi | Entités de domaine renvoyées directement en JSON (`List<Centre>`, `PagedResult<Medecin>`, `Centre`). Ce sont des classes à accesseurs non-JavaBean, sans configuration Jackson de visibilité : rendu `{}` ou erreur 500. La fiche le reconnaît comme dette | `CentresControleur.java:43-55` ; `ReferentielsControleur.java:53-56` ; fiche O5 `:140-141` | DTO de réponse (`CentreReponse` existe déjà) pour chaque endpoint `/organisation/*` |
| major | legacy | Garde `TableDeLOrganisationTest` vide de sens : elle ne scanne pas `platform/src/main/java` et exclut `/acl/` et `/persistence/`. `TABLES_LUES_AILLEURS = Set.of()` donne donc une fausse assurance. Lecteurs SQL réels hors contexte : platform 19, planification 8, facturation 7, stock 5, absences 2, comptabilité 1 | `TableDeLOrganisationTest.java:28,44-47,74-75` | Scanner platform et ACL, lister ces lecteurs en liste blanche nommée et datée |
| major | stratégique | O4 « tous les consommateurs passent par organisation.v1 » est **faux** : aucun module n'importe `contexts.organisation.publishedlanguage`, et les ACL `legacyorganisation`/`legacycentres` lisent les tables | grep import → 0 consommateur ; `planification/…/acl/legacyorganisation/SallesDuCentreJdbc.java` | Remettre O4 à `planned` ; inventorier les consommateurs |
| major | archi | `DELETE FROM medecin/machine/protocole WHERE id = ?` sans `center_id` (suppression inter-centre possible) et en suppression physique. Les médecins sont référencés par les séances | `MedecinsJdbc.java:40`, `MachinesJdbc.java:39`, `ProtocolesJdbc.java:39` | Filtrer sur `center_id` ; désactivation logique ou refus si référencé |
| major | legacy | Fiche O5 contradictoire et corrompue. Statut `done`, alors que l'état dit `characterized` et que des cases restent ouvertes. « Relocation ANNULÉE » (`:50-53`) contre « relocalisées » (`:107-110`). Caractères de contrôle dans le texte : `rouver\`n`, `\f`/`\r` | fiche O5 `:3,50-53,107-110,119-124,149,158-159,163-165` | Réécrire la fiche ; ADR pour l'admin générique et la migration en platform |
| minor | archi | Horloge et aléa dans l'application : `OffsetDateTime.now(UTC)` pour `createdAt`, `UUID.randomUUID()`. Un `created_at` NULL est remplacé par `now()` à la lecture | `OrganisationService.java:39-42,60,207` ; `SocietesJdbc.java:34` | Injecter `Clock` et un générateur d'ID ; ne pas fabriquer de date à la lecture |
| minor | archi | Correspondance des erreurs par sous-chaîne du message (`contains("inconnu")`) | `ErreursDeLOrganisationAdvice.java:60-63` | Exceptions de domaine typées |
| minor | legacy | Écart de parité non décidé : `/referentials/salles` et `/positions` sont triés par `code` (`ORDER BY code`), alors que le legacy ne triait pas | `SallesJdbc.java:42` contre legacy `ReferentialRepositoryAdapter:62` (b365a63a^) | Consigner l'écart (accepté ou reproduit) |
| minor | stratégique | 6 termes du glossaire encore `proposed` (Société, Centre, Transfert, Salle, Créneau, Mois) alors que le code est en place | `glossaries/organisation.md:11-16` | Validation métier |
| nit | legacy | Fichier vide parasite à la racine du dépôt | `EquipementHttpCharacterizationIntegrationTest.java` (0 octet, b365a63a) | Supprimer |

Non trouvés (aucun constat) : tests dépendants de la date dans `organisation/src/test` (grep `now()` et jours fixes : 0). `ErreursDeLOrganisationAdvice` est bien limité au contexte (`:22`).

## 3. Cohérence d'état
| Source | Dit | Réalité |
|---|---|---|
| `state.json` O1–O4 | `built` | Le code existe, mais sans filet, avec des blockers et sans revue → **`characterized` est lui-même faux** (pas de golden) |
| roadmap `:290-293` | `planned` | Plus proche de la vérité pour O2/O4 |
| fiches | O1 `characterized`, O2 `planned`, O3 `built`, O4 `built`, O5 `done` | Les fiches se contredisent entre elles et avec l'état |
| `state.json` O5 | `characterized` | Legacy **déjà supprimé** (b365a63a) sans caractérisation → état inexistant dans le protocole : « retiré sans filet » |
| `tactical/organisation.md` | `status: proposed` | Le gate tactique global est `approved`, mais le modèle de ce contexte ne l'est pas |

**État réel proposé** (à poser par l'utilisateur, pas par moi) : O1 `in-progress`, O2 `in-progress`, O3 `in-progress`, O4 `planned`, O5 `in-progress` (retrait exécuté, filet et parité à rétablir). Contexte `organisation` : `architected` (inchangé).
Commandes : `state.ts set slices.organisation-o1-societes-centres in-progress`, etc., après décision.

## 4. Contrôles de production (ordre)
1. Avant le déploiement : vérifier sur une **copie** de la base prod que `salle.ouverte`, `machine` et `protocole` existent ; sinon migration Flyway d'abord (blocker 2).
2. `GET /api/v1/referentials/salles|positions|medecins?centerId=` sur 2 centres : même contenu que la version précédente (compter les lignes).
3. `GET/POST/PUT /api/v1/societes…` en SUPERADMIN : liste paginée, logo (PUT/GET/DELETE), activer/désactiver, transfert, refus du dernier centre (422 `SOCIETE_DERNIER_CENTRE`).
4. Avec un compte non SUPERADMIN : `POST /api/v1/organisation/centres` et `POST /api/v1/organisation/referentiels/medecins` doivent renvoyer **403** (aujourd'hui 200 → blocker 1).
5. `GET /api/v1/organisation/centres?societeId=` : corps JSON non vide (risque `{}`).
6. Caches `ref.*` : modifier une salle, puis vérifier l'invalidation.
7. `/api/v1/admin/referentials` (import/export/templates) et `/api/v1/admin/migration` : fonctionnels après la relocalisation.
8. Connexion → choix société/centre (`/api/v1/auth/societes`) inchangé.
9. Documents (`ModeleDocumentPrinter`) : logo et pied de page de la société toujours rendus.

Aucune section Organisation dans `.ddd/migration/verifications-production.md` : à créer.

## 5. Validations restantes pour l'utilisateur
- Statut de `tactical/organisation.md` (`proposed`) et des 6 termes `proposed` du glossaire.
- ADR manquant : hébergement de l'admin générique et de la migration de données dans `platform/infrastructure`. Choix possibles : rester en platform, porter vers le contexte, ou retirer.
- Décision : référentiels orphelins (payeurs, agences, caisses, transporteurs, forfaits) dans Organisation ou dans leurs contextes propriétaires.
- Décision : tri `ORDER BY code` accepté ou non ; suppression physique contre logique des médecins.
- Retrait de `SocieteJpaEntity`/`CenterJpaEntity` (auth et licence).

## Verdict : **fail**
Top 3 actions :
1. Sécuriser les écritures (`@PreAuthorize` + garde de centre) et ajouter des tests 403.
2. Aligner le schéma prod (Flyway) sur `SallesJdbc`, `MachinesJdbc` et `ProtocolesJdbc`, avec un test contractuel Postgres.
3. Écrire le filet de caractérisation HTTP sur `b365a63a^`, le rejouer sur le nouveau code, puis corriger l'état et les fiches.
