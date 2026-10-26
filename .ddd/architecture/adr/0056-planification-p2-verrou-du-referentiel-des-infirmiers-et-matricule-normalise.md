---
status: proposed
date: 2026-10-26
decision-makers: [utilisateur (décisions du 2026-10-26), architecte]
context-scope: planification
related: ADR 0024, 0043, 0049, 0053, 0055 ; `.ddd/tactical/planification.md` P13 ; KB-PLN-12 à 24 ; revue `.ddd/verify/review-planification-p2.md`
---
# 0056 — Planification P2 : verrou du référentiel des infirmiers, ordre global des verrous, matricule normalisé sans index, un compte = un centre

## Contexte et problème
La slice P2 (référentiel des infirmiers et de leurs comptes, 9 routes) doit (1) sérialiser les écritures d'un centre et les refuser en 409 en cas de
concurrence (KB-PLN-18), (2) rendre l'unicité du matricule insensible à la casse sans casser la production (KB-PLN-19), (3) rendre atomiques la fiche et le
compte (KB-PLN-23), (4) borner au centre les effets sur le compte et refuser les états incohérents (KB-PLN-15, 22), (5) laisser
l'administration des utilisateurs (legacy) synchroniser les fiches par un port publié.
Le verrou du centre de l'ADR 0055 (`SELECT … FOR UPDATE` de la ligne `planning_parametres`, sans la créer) **ne convient pas** : un centre n'a pas
forcément de ligne, et l'unicité d'un matricule à la création n'a aucune ligne `infirmier` à verrouiller.

## Décisions
1. **Verrou du référentiel** (`VerrouDuReferentielDesInfirmiers`) : verrou de la **ligne du centre** (`centers`), pris en premier par chacune des 7 écritures,
   par `UPDATE centers SET id = id WHERE id = ?`. Sur PostgreSQL c'est un verrou `FOR NO KEY UPDATE` (la clé n'est pas modifiée) : il exclut les
   autres écritures du référentiel du même centre **sans bloquer** les insertions des tables qui référencent le centre (clés étrangères, verrou
   `FOR KEY SHARE`) — ce que ferait `SELECT … FOR UPDATE`. Prouvé sur PostgreSQL 16 (`should_let_a_row_referencing_the_centre_be_inserted_while_the_centre_lock_is_held`).
   La ligne du centre existe toujours (le centre de la session) : aucune ligne n'est créée, contrairement à la variante de l'ADR 0055 réservée au
   paramétrage. Délai : 5 s (`SET LOCAL lock_timeout`, ADR 0043) ; refus de verrou, interblocage ou clé créée entre-temps = `EcritureConcurrente` = 409
   `PLANIFICATION_ECRITURE_CONCURRENTE`. Toute lecture d'une fiche avant écriture (`pourEcriture`, `fichesDuCompte…`) verrouille la ligne `infirmier`
   (`FOR UPDATE`, ADR 0049 : « roulement : verrou de la ligne `infirmier` »).
2. **ORDRE GLOBAL DES VERROUS du contexte Planification** (à respecter par P3 à P9 et par tout code legacy qui écrit ces tables ; la revue du 26/10 a trouvé un
   cycle, corrigé) : **1. ligne du centre** (`centers` ; ou `planning_parametres` pour le paramétrage et le placement, ADR 0055, jamais les deux dans une même
   transaction sans prendre d'abord la ligne du centre) → **2. lignes `infirmier`, par identifiant croissant** (puis `infirmier_affectation`, absence,
   remplacement : ADR 0051) → **3. lignes `app_user`** (état du compte lu pour une liaison, ou modifié) → **4. verrou consultatif des identifiants de connexion**.
   Conséquences appliquées : `DesactiverUnInfirmier`/`ReactiverUnInfirmier` = centre, fiche, `app_user` ; `LierUnCompte` verrouille le compte (`FOR UPDATE`) **avant** de lire son
   état ; `UserRestController.updateUser` appelle la synchronisation des fiches (verrou de fiche) **avant** l'`UPDATE app_user` (l'ancien ordre inverse pouvait
   s'interbloquer) ; `deleteUser` délie les fiches avant le `DELETE app_user`. Preuve : `should_not_deadlock_when_a_nurse_deactivation_and_an_account_update_cross`
   (H2 et PostgreSQL 16). Un interblocage résiduel (SQLSTATE 40P01) devient un 409 : côté contexte par `EcritureConcurrente`, côté administration des utilisateurs par
   l'exception du langage publié `EcritureConcurrenteDesFiches` rendue en 409 par `EcritureConcurrenteDesFichesAdvice`.
3. **Matricule normalisé en majuscules, sans index fonctionnel** : le matricule saisi est mis en majuscules et l'unicité est vérifiée par
   `LOWER(matricule) = LOWER(?)` **sous le verrou du référentiel** ; la contrainte `UNIQUE (center_id, matricule)` reste le dernier garde-fou (409). Un index
   unique fonctionnel `(center_id, upper(btrim(matricule)))` a été **écarté** : il échouerait en production s'il existe déjà des doublons de casse (le legacy les
   permettait), exigerait une migration Vxx compatible H2/PG16 (H2 ne supporte pas les index sur expression de la même façon) et un nettoyage de données préalable
   que l'exploitation doit d'abord mesurer. Le contrôle SELECT-only des doublons est dans `verifications-production.md` § 33 (requête n° 1) et rejoué sur la
   base jetable par `planification-p2-pg16-rejeu.sh`. Les fiches existantes gardent leur casse jusqu'à leur modification. **À reconsidérer** : l'index unique
   une fois le contrôle n° 1 vide en production. (Une saisie de 50 caractères peut dépasser 50 une fois en majuscules, par exemple `ß` : sans effet pratique.)
4. **Une transaction par cas d'usage** (`UniteDeTravail`) : la fiche, le compte (création : 3 INSERT) et la liaison sont atomiques. Le port `ComptesUtilisateurs`
   (ACL `legacyidentite`, tables `app_user*`) participe à la transaction du cas d'usage. Lectures du roulement (P3) par un port en lecture seule
   (`RoulementsDesInfirmiers`, ACL `legacyroulement`).
5. **Identifiants de connexion** : `app_user.username` n'a **aucune contrainte d'unicité dans Flyway** (KB-PLN-24). Un verrou consultatif de transaction
   (`pg_advisory_xact_lock`, PostgreSQL seulement ; H2 a `UNIQUE`) sérialise les créations, puis le contrôle d'existence est rejoué sous ce verrou. Il est pris par la création
   d'un compte depuis une fiche **et par `UserRestController.createUser`** (via `FichesDesInfirmiers.verrouillerLesIdentifiantsDeConnexion`, `createUser` devient
   `@Transactional`) : la protection est complète pour les deux chemins applicatifs. Un index unique `lower(username)` reste à décider (défense en profondeur, hors
   code applicatif).
6. **Un compte = un seul centre, états cohérents** (décision du 26/10, KB-PLN-15) : lier un compte déjà lié à une fiche d'un autre centre est refusé en 422
   `COMPTE_LIE_A_UN_AUTRE_CENTRE`. Désactiver/réactiver une fiche n'agit que sur le centre de la fiche ; la synchronisation compte → fiche se limite au centre de
   l'administrateur (tous centres sans centre de session). Refusés en 422 : réactiver une fiche active dont le compte est désactivé (`COMPTE_ETAT_INCOHERENT`),
   délier une fiche inactive dont le compte est actif (`COMPTE_DELIAISON_INCOHERENTE`), supprimer un compte lié à une fiche **active** (`COMPTE_LIE_A_UNE_FICHE_ACTIVE`).
   Les comptes **déjà** multi-centres (données historiques) sont repérés par la requête n° 5 de `verifications-production.md` § 33 et traités à la main avant le
   déploiement ; sans eux, la réactivation d'une fiche ne peut plus réactiver le compte d'un autre centre.
7. **Langage publié d'écriture** `publishedlanguage.FichesDesInfirmiers` (`suivreLEtatDuCompte`, `delierAvantLaSuppressionDuCompte`, `verrouillerLesIdentifiantsDeConnexion`) : seul point
   d'entrée du legacy ; il traduit le refus `COMPTE_LIE_A_UNE_FICHE_ACTIVE` et l'écriture concurrente en exceptions publiées ; `UserRestController` (POST/PUT/DELETE `/users`)
   l'appelle dans une transaction (`@Transactional`) ; le legacy n'écrit plus jamais `infirmier`.
8. **Entrées** : le corps est lu **après** l'autorisation (`@RequestBody byte[]` puis `CorpsJson`) : 403 avant 400/415 (la décision ne couvre que le corps : un identifiant de chemin ou un
   `centerId` illisible reste un 400 avant le 403). Les erreurs de saisie sont des 400 `BAD_REQUEST` (domaine : `SaisieInvalide`), les refus de règle des 422 à codes stables.
9. **Audit** : libellés métier par route dans `AuditActionResolver` (7 écritures) ; l'identifiant de la fiche créée est transmis au filtre par l'attribut de requête
   `hemodialyse.audit.entityId` (le chemin d'une création n'en porte pas). Les éditions de `AuditActionResolver` et `AuditRequestFilter` sont **ratifiées par écrit par
   l'utilisateur** (2026-10-26, `seams.md`).
10. **Pas d'événements de domaine** en P2 (aucun consommateur ; `InfirmierEnregistre`… du modèle tactique restent à introduire avec le premier consommateur).

## Dette connue
- La règle « le compte suit l'état de la fiche » est appliquée par les cas d'usage (`DesactiverUnInfirmier`, `ReactiverUnInfirmier`), pas rendue par l'agrégat (qui devrait
  retourner l'intention « état du compte à imposer »). Déplacement non trivial (l'agrégat ne connaît le compte que par identité) : à faire avec les événements de domaine.
- `DelierAvantLaSuppressionDuCompte` et `SuivreLEtatDuCompte` modifient plusieurs agrégats `Infirmier` (éventuellement de plusieurs centres) dans une transaction, sans verrou de
  centre : exception voulue à « une transaction = un agrégat », imposée par la suppression/désactivation d'un compte qui n'appartient à aucun centre (KB-PLN-16).

## Conséquences
+ Écritures d'un centre sérialisées ; fiche + compte atomiques ; aucune migration ; retour arrière = redéploiement ; plus de cycle de verrous.
+ Le legacy ne contient plus de code d'écriture du référentiel ; `InfirmierService` est réduit à la lecture du détail pour « Mon planning » (P3).
− Le verrou du centre sérialise aussi, brièvement, les autres écritures du même centre qui prennent ce verrou (admin seulement, volume négligeable).
− Le compte reste une ressource partagée avec l'administration des utilisateurs : désactiver une fiche désactive le compte (jamais l'inverse sans passer par l'administration).
− `UniteDeTravail` du module et `@Transactional` de `UserRestController` doivent partager le même gestionnaire de transactions (c'est le cas).
