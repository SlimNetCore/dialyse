---
status: characterized
date: 2026-10-26
sources: .ddd/tactical/planification.md (P2), docs/reference/regles-de-gestion/10-infirmiers.md (RG-INF-001 à 004, 010 à 012), roadmap §1 sexies, ADR 0049 à 0055
---

# Filet de caractérisation — planification-p2-infirmiers

Refactoring sur place (ADR 0024) : pas de Strangler. Legacy figé ; **aucun seam, aucune modification de production** (`seamAllowed` reste vide).

## Périmètre (9 routes de P2 + 2 routes d'administration observées pour leurs effets)
| Route | Rôles observés | Tables |
|---|---|---|
| `GET /api/v1/infirmiers` | ADMIN, SECRETAIRE, MEDECIN+INFIRMIER (MEDECIN seul et INFIRMIER seul : 403 `ROLE_SCOPE`, KB-PLN-21) | `infirmier`, `app_user` (nom du compte), `infirmier_affectation` (lue, propriété de P3) |
| `POST /`, `PUT /{id}`, `POST /{id}/desactiver`, `POST /{id}/reactiver` | ADMIN | `infirmier` ; `app_user.active` si compte lié |
| `GET /comptes-liables` | ADMIN seul | `app_user`, `app_user_role`, `app_role`, `app_user_center`, `infirmier` |
| `POST /{id}/compte/lier`, `POST /{id}/compte/creer`, `DELETE /{id}/compte` | ADMIN | `infirmier.user_id`, `app_user`, `app_user_role`, `app_user_center` |
| `PUT\|DELETE /api/v1/users/{id}` (administration, observées pour la synchro compte → fiche seulement) | ADMIN | `app_user`, `infirmier` |

Hors périmètre (P3) : `POST|PUT|DELETE /{id}/affectations…` (3 routes du même contrôleur, non exercées).

## Point d'observation
HTTP (MockMvc, contexte Spring complet, sécurité réelle + `RoleScopeFilter`, H2 `test`), état SQL normalisé de `infirmier`, `app_user` (sans hash : drapeau « haché »), rôles et centres, `audit_log`. Aucun mock. Harnais : `backend/platform/src/test/java/com/hemodialyse/backend/characterization/planification/InfirmiersCharacterizationSupport.java` (au-dessus de `PlanificationCharacterizationSupport` de P1 ; comptes `p2.*` nettoyés, centres de test créés pour la clé étrangère `app_user_center`, mot de passe temporaire masqué dans les goldens et vérifié par assertions de forme/hachage).
Tests (4 classes, 48 scénarios) : `…/characterization/planification/infirmiers/{Autorisations,Referentiel,Comptes,Audit}DesInfirmiersCharacterizationIntegrationTest` ; goldens : `backend/platform/src/test/resources/characterization/planification/p2-infirmiers/{autorisations,referentiel,comptes,audit}/` (≈ 190 fichiers).
Commande : `./mvnw -o -q -pl platform -am test -Dtest='com.hemodialyse.backend.characterization.planification.infirmiers.*IntegrationTest' -Dsurefire.failIfNoSpecifiedTests=false` (`-Dcharacterization.approve=true` pour régénérer après revue ; `-Dsurefire.runOrder=random` pour l'ordre aléatoire).

## Scénarios (par comportement)
- **Autorisations** : matrice 9 routes × 11 profils + anonyme (statut, code, « a-t-on écrit ? ») ; formes de refus (`ROLE_SCOPE`, `ACCESS_DENIED`, anonyme sans corps) ; corps lu avant `@PreAuthorize` (KB-PLN-12).
- **Liste** : vide ; tri nom, prénom, matricule (casse et prénom nul) ; inactifs inclus ; isolement par centre ; roulement et compte embarqués (compte disparu → `null`) ; mêmes données pour les profils de lecture ; pagination (page 0, hors limite, 1000, 0, négatifs, illisibles).
- **Garde de centre** : `?centerId=` identique, autre centre (403), illisible (500), ADMIN sans centre ; fiche d'un autre centre = introuvable (422) en écriture.
- **Création** : fiche active sans compte ; trim ; prénom/téléphone blancs ou absents → `null` ; champs inconnus ignorés (`actif`, `id`, `userId` du corps sans effet) ; refus (obligatoires 400, qualification inconnue 400, booléen absent 500…) ; bornes de colonnes ; unicité du matricule par centre (casse, espaces, autre centre) et ordre des contrôles ; deux créations simultanées.
- **Modification** : remplacement complet ; état et compte conservés ; fiche inactive modifiable ; matricule de soi-même (casse) accepté, d'un autre refusé ; fiche inconnue ; dernière écriture gagnante ; compte non modifié (KB-PLN-14).
- **Activation** : désactiver/réactiver idempotents ; sans corps accepté ; compte lié suit (désactivation, réactivation forcée, compte disparu) ; effet sur les profils de planification de P1 (la fiche inactive disparaît de la liste des profils, profil conservé, écriture refusée 422).
- **Comptes** : liste des comptes liables (rôle, centre, déjà lié, inactifs, deux rôles, pagination) ; liaison nominale, deux inactifs, 7 refus et ordre des contrôles ; création (mot de passe temporaire : forme, hachage, `must_change_password`, jamais relisible, mélangé ; nom complet, e-mail normalisé ; identifiant : 50/51 caractères, caractères interdits, doublon sans casse tous centres, espaces de bord ; fiche inactive/déjà liée/inconnue ; rôle INFIRMIER absent du référentiel) ; déliaison (compte conservé, de nouveau liable, idempotente, incohérente).
- **Synchronisation compte → fiche** : désactivation/réactivation via `PUT /users/{id}` ; compte multi-centres (KB-PLN-15) ; suppression de compte (refusée par la base puis réussie) (KB-PLN-16).
- **Audit** : écritures tracées (route, méthode, statut, libellé d'action) y compris refusées ; lectures non tracées (KB-PLN-17). Aucune diffusion ni notification.

## Seams
Aucun. Le non-déterminisme est maîtrisé par normalisation : UUID générés (`<UUID_GENERE_n>`), alias nommés des fixtures, horodatages, mot de passe temporaire (`<MOT_DE_PASSE_TEMPORAIRE>`), tableaux d'objets jamais retriés (ordre SQL explicite). Aucune date : le référentiel n'en porte pas (`created_at` des comptes non lu).

## Bugs et constats
KB-PLN-12 à KB-PLN-23 (`known-bugs.md`), reproduits tels quels.

## Couverture et mutation
- Stabilité : 3 exécutions consécutives de la suite Planification (P1 50 + P2 48 scénarios, plus les tests existants des infirmiers et l audit de P1) : 0 échec, 0 flaky, dont 2 avec `-Dsurefire.runOrder=random`.
- PIT 1.30.0 (`backend/scripts/pit-p2-infirmiers.sh`, ~10 min, Spring complet) sur `InfirmierService`, `CompteInfirmierService`, `GenerateurMotDePasseTemporaire`, `Infirmier`, `InfirmierJdbcAdapter`, `ComptesInfirmierJdbcAdapter`, `InfirmierRestController` : 1re mesure 108 mutants, 91 tués (84 %), couverture de lignes des classes mutées 93 % (214/230). Survivants analysés : 6 gardes de constructeur de `Infirmier` (L23-26, 67-68) et 4 conditions de nullité (`InfirmierService:49`, `CompteInfirmierService:61`, `InfirmierJdbcAdapter:80`, `ComptesInfirmierJdbcAdapter:66`) **inatteignables ou équivalents par HTTP** (validés en amont par `@NotBlank` / contrôles de service / agrégat `COUNT`) ; `Collections.shuffle` du mot de passe (tué par le test de mélange ajouté) ; 5 mutants sans couverture dans les routes d'affectations (P3) et 1 dans l'absence du rôle INFIRMIER (couvert depuis). Après ajout de 3 scénarios (mélange du mot de passe, rôle INFIRMIER absent, compte partagé) : **108 mutants, 93 tués (86 %), 5 sans couverture (affectations, P3), force des tests 90 %** ; les 10 survivants restants sont ceux analysés ci-dessus (équivalents ou inatteignables par HTTP).

## Limites (ce qui n'est PAS verrouillé)
- Les 3 routes d'affectations du même contrôleur (P3) et `AffectationInfirmierService`, ainsi que `UserRestController` (administration des utilisateurs : seuls les effets de synchronisation sont observés) ne sont pas dans `covered.json`. `InfirmierRestController` y est inscrit pour ses 9 routes de P2 ; toute édition de ses lignes d'affectations n'est pas protégée tant que P3 n'a pas son filet.
- Le chemin de production (PostgreSQL) : clés étrangères de `app_user_*` (KB-PLN-16), limite de l'e-mail (150) non observables en H2.
- Course sur le matricule : issue non déterministe (201+422 ou 201+500), seules les invariants sont affirmés (une fiche, pas de 409).
- Lecteurs hors module non rejoués ici : `PresenceDonneesJdbcAdapter`, `PresenceInfirmierScheduler` (P4), `InfirmiersDuCentreJdbc` / `LectureDesPreferencesJdbc` de P1 (observés via la liste des profils uniquement), `OptimisationApplicationService` (P8).
- Rien sur la connexion elle-même (mot de passe temporaire accepté, changement forcé : RG-SEC-025).

## Après la construction (2026-10-26)
Le filet a été rejoué contre le nouveau code (`contexts/planification`) : 50 scénarios verts (48 + 2 ajoutés pour la suppression d'un compte lié à une fiche inactive) ; 42 goldens sur 194 changent par décision (`<nom>_nouveau_code`), l'ancien comportement est conservé dans `p2-infirmiers/_ecarts-legacy/`. Quelques scénarios ont été adaptés à leurs nouvelles règles (matricules distincts dans la matrice de création, refus d'audit par une qualification inconnue). `backend/scripts/pit-p2-infirmiers.sh` (classes legacy supprimées) est remplacé par `backend/scripts/pit-p2-module.sh`. Voir `.ddd/migration/slices/planification-p2-infirmiers.md`.
