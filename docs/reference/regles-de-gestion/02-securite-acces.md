# 02 — Sécurité, comptes, rôles, licences et traçabilité

> Préfixe `RG-SEC`. Couvre : authentification, double authentification, mot de passe, périmètres de rôles, gestion des
> comptes
> et des rôles, installation initiale, licences, journal d'audit.

## 2.1 Profils (rôles)

- **RG-SEC-001** — Rôles du système : `ADMIN` (administrateur du centre), `MEDECIN`, `INFIRMIER`, `SECRETAIRE`,
  `PHARMACIEN`
  (stock), `DIRECTION` (direction d'une société, sans centre), `SUPERADMIN` (propriétaire de la plateforme, sans
  centre) ;
  habilitations complémentaires `GMAO_REFORME` (autorise la réforme d'un équipement) et `GMAO_RECTIFICATION` (autorise
  la
  rectification d'une intervention). Des rôles personnalisés peuvent être créés par l'administrateur. *Sources :*
  `seed.sql`,
  `RoleRestController`.
- **RG-SEC-002** — Un compte peut cumuler plusieurs rôles ; les périmètres restreints (RG-SEC-020 à 024) ne s'appliquent
  qu'aux comptes dont le seul rôle de centre est celui concerné. *Source :* `RoleScopeFilter`.
- **RG-SEC-003** — Le rôle `SUPERADMIN` est réservé au propriétaire : il est invisible dans les listes de rôles pour
  tout autre
  profil, ne peut être ni créé, ni modifié, ni supprimé par un non-SUPERADMIN (403 « Rôle réservé »), n'est jamais
  attribuable
  par l'écran de gestion des utilisateurs à moins que l'appelant le détienne déjà, et un non-SUPERADMIN ne peut pas
  retirer
  ce rôle à un compte qui le possède. *Sources :* `RoleRestController`, `UserRestController.filterAssignableRoleIds`.
- **RG-SEC-004** — Le code d'un rôle est unique (« Code rôle déjà existant », 400). *Source :*
  `RoleRestController.createRole`.
- **RG-SEC-005** — La gestion des utilisateurs et des rôles est réservée à l'`ADMIN`. *Source :* `@PreAuthorize` de
  `UserRestController`, `RoleRestController`.

## 2.2 Connexion et sessions

- **RG-SEC-010** — La connexion se fait en deux étapes d'annuaire publiques (sociétés actives disposant d'au moins un
  centre
  actif ; puis centres actifs de la société choisie) puis identifiant/mot de passe. Les annuaires n'exposent que
  identifiant
  et nom. *Sources :* `AuthService.listLoginSocietes/listLoginCentres`, `AuthRestController`.
- **RG-SEC-011** — Un identifiant ou un mot de passe erroné renvoie le même message « Identifiants invalides » (on ne
  révèle pas
  lequel est faux). Le mot de passe (BCrypt) est vérifié avant tout contrôle de rôle ou de rattachement. *Source :*
  `AuthService`.
- **RG-SEC-012** — Un compte désactivé ne peut pas se connecter (« Compte utilisateur désactivé »). *Source :*
  `AuthService.login`.
- **RG-SEC-013** — La connexion à un centre exige que l'utilisateur soit rattaché à ce centre, que le centre soit actif,
  que sa
  société soit active et, si une société est sélectionnée, que le centre lui appartienne. *Source :*
  `AuthService.login`,
  `assertCentreEtSocieteUtilisables`.
- **RG-SEC-014** — Trois types de session : `CENTRE` (utilisateur d'un centre), `SOCIETE` (direction : rôle `DIRECTION`,
  compte
  rattaché à la société, société active) et `PLATEFORME` (propriétaire : rôle `SUPERADMIN`, sans société). *Source :*
  `AuthService.loginScoped`.
- **RG-SEC-015** — Le jeton d'accès (JWT) est stocké dans un cookie `HttpOnly` (`HEMO_AUTH`, durée 8 h par défaut) et un
  jeton de
  rafraîchissement opaque dans un cookie distinct (`HEMO_REFRESH`, 7 jours par défaut, restreint à
  `/api/v1/auth/refresh`) ;
  jamais dans le stockage local du navigateur. Le serveur est sans état. *Sources :* `AuthRestController`,
  `application.yml`.
- **RG-SEC-016** — Le jeton de rafraîchissement n'est stocké qu'en empreinte SHA-256, est à **usage unique** (rotation à
  chaque
  rafraîchissement) ; sa réutilisation révoque **tous** les jetons de l'utilisateur. Un jeton révoqué ou expiré est
  refusé. *Source :* `AuthService.rotateFromRefreshToken`.
- **RG-SEC-017** — À chaque rafraîchissement, la session est reconstruite en revérifiant compte actif, rattachement et
  état du
  centre/de la société. La déconnexion révoque le jeton et expire les deux cookies. *Source :*
  `AuthService.rebuildSession`.
- **RG-SEC-018** — Un jeton expiré provoque un rafraîchissement silencieux côté interface ; en cas d'échec, retour à la
  page de
  connexion. *Source :* `authInterceptor`.

## 2.3 Mot de passe

- **RG-SEC-019** — Politique d'un nouveau mot de passe choisi par l'utilisateur (changement de mot de passe) : 10 à 128
  caractères, au moins une lettre et un chiffre, et ne contenant pas l'identifiant de connexion ; sinon
  `PASSWORD_TROP_FAIBLE`. *Source :* `PolitiqueMotDePasse`.
- **RG-SEC-019b** — Politique des mots de passe **posés par le propriétaire** pour les comptes administrateur de centre
  et
  direction : 12 à 100 caractères, au moins une lettre et un chiffre, sans l'identifiant (codes `PASSWORD_TOO_SHORT`,
  `PASSWORD_TOO_LONG`, `PASSWORD_WEAK`, `PASSWORD_CONTAINS_USERNAME`). Politique du **compte propriétaire**
  (installation
  initiale) : 14 caractères minimum avec majuscule, minuscule, chiffre et symbole, sans l'identifiant. *Source :*
  `PasswordPolicy`. **Point d'attention :** trois seuils coexistent (10 / 12 / 14) ; un mot de passe de 10-11 caractères
  est donc accepté au
  changement par l'utilisateur mais refusé à la création par le propriétaire.
- **RG-SEC-025** — Un compte créé avec un mot de passe temporaire est marqué « à changer » : tant que le mot de passe
  n'est pas
  remplacé, toute route `/api/**` hors `/api/v1/auth/**` répond **403** `PASSWORD_CHANGE_REQUIRED`. L'indicateur est mis
  en
  cache 1 minute et vidé au changement. *Sources :* `PasswordChangeRequiredFilter`, `ChangementMotDePasseService`.
- **RG-SEC-026** — Changement de mot de passe : le mot de passe actuel doit être exact (`PASSWORD_ACTUEL_INVALIDE`), le
  nouveau
  doit différer de l'actuel (`PASSWORD_IDENTIQUE`) et respecter la politique (`PASSWORD_TROP_FAIBLE`) ; compte
  introuvable ou
  inactif : `PASSWORD_COMPTE_INTROUVABLE`. *Source :* `ChangementMotDePasseService.changer`.

## 2.4 Double authentification (TOTP, facultative, par utilisateur)

- **RG-SEC-027** — L'inscription génère un secret chiffré au repos, confirmé par un premier code valide ; la
  confirmation produit **8 codes de secours** à usage unique (10 caractères, affichés une seule fois, stockés hachés).
  Une inscription inachevée est
  remplacée. Déjà activée : `MFA_ALREADY_ENABLED` ; confirmation sans inscription : `MFA_NOT_ENROLLED` ; code faux :
  `MFA_INVALID`. *Source :* `MfaService`.
- **RG-SEC-028** — Si elle est activée, la connexion exige un code après le mot de passe (`MFA_REQUIRED` si absent). Un
  code TOTP
  déjà accepté ne peut pas être rejoué (dernier pas mémorisé) ; un code de secours est consommé à l'usage. *Source :*
  `MfaService`.
- **RG-SEC-029** — Après **5 échecs consécutifs** le compte est verrouillé **5 minutes** (`MFA_LOCKED`). La
  désactivation exige un
  code valide. *Source :* `MfaService`.

## 2.5 Périmètres restreints de rôles (appliqués côté serveur et dans les menus)

- **RG-SEC-020** — `SUPERADMIN` : accès limité aux sociétés (`/societes`), licences (`/licenses`) et journal d'audit
  (`/audit`) ;
  tout autre route `/api/v1/**` répond 403 `ROLE_SCOPE`. *Source :* `RoleScopeFilter`.
- **RG-SEC-021** — `DIRECTION` : accès limité à `/api/v1/direction/**`, en **lecture seule**, sur des agrégats
  anonymes ; aucune
  donnée de centre (patients, séances, absences, stock, facturation…). *Source :* `RoleScopeFilter`,
  `DirectionAccessGuard`.
- **RG-SEC-022** — Infirmier « seul » (rôle `INFIRMIER` sans autre rôle de centre) : routes `/api/v1/infirmiers/**` et
  `/api/v1/planning/**` interdites, hormis son propre planning `/api/v1/infirmiers/moi/**`. Côté interface il n'accède
  qu'à son
  planning du jour, au tableau de bord des séances et au suivi des absences de patients. *Source :* `RoleScopeFilter`,
  `role-scope.guard`.
- **RG-SEC-023** — Médecin « seul » (rôle `MEDECIN` sans `ADMIN`/`SECRETAIRE`/`INFIRMIER`) : routes `infirmiers`,
  `planning`,
  `facturation`, `reglements`, `comptabilite` et `gmao` interdites, sauf la lecture du planning de la semaine
  (`/planning/semaine`) et de la présence des infirmiers (`/infirmiers/presence/semaine`). Il consulte patients (fiche
  en
  lecture seule), dossier médical, cahier de dialyse, statistiques, son tableau de bord et le suivi des absences.
  *Source :* idem.
- **RG-SEC-024** — Les écrans interdits à un profil restreint le redirigent vers son accueil (`/infirmiers/moi`,
  `/medecin`,
  `/direction`, `/admin/societes`). *Source :* `homeRouteFor`, `roleScopeGuard`.

## 2.6 Direction : périmètre dérivé de la session

- **RG-SEC-030** — La société d'une session direction vient du jeton (jamais d'un paramètre) et est **revérifiée en base
  à chaque
  requête** : compte actif, rôle `DIRECTION`, rattachement à la société, société active. Un compte désactivé ou détaché
  perd
  l'accès immédiatement. *Source :* `DirectionAccessGuard`.

## 2.7 Licences

- **RG-SEC-031** — Une licence est un jeton signé (RS256) lié à un centre : type, nombre maximal d'utilisateurs, début
  et fin de
  validité. L'expiration, le quota et le centre sont lus **dans le jeton signé**, jamais dans les colonnes de la base
  (modifier
  la base n'a aucun effet). Seule l'instance « autorité » (détenant la clé privée) émet des licences. Seule la
  révocation
  est un fait propre à la base. *Sources :* `LicenseService`, `LicenseTokenService`.
- **RG-SEC-033** — Émission pour une société : une licence par centre **actif** (ou une sélection de ses centres
  actifs), en une
  seule transaction (tous ou aucun) ; société sans centre actif ou centre étranger/inactif : refus. *Source :*
  `LicenseService.issueForSociete`.
- **RG-SEC-034** — Activation hors ligne : la licence doit avoir été émise pour ce centre et ne peut être activée deux
  fois. *Source :* `LicenseService.activate`.
- **RG-SEC-035** — Verdict d'un centre : aucune licence, révoquée, signature/expiration invalide, jeton d'un autre
  centre (falsification), vérification en ligne trop ancienne → licence invalide. *Source :* `LicenseService.verify`.
- **RG-SEC-036** — Tant qu'un centre n'a pas de licence valide, toute route d'un utilisateur non-SUPERADMIN répond
  **402**
  `LICENSE_REQUIRED`, sauf `/auth`, `/licenses/status|activate|authority`, `/system/ping`, actuator, documentation d'API
  et
  `/ws`. Le verdict est mis en mémoire 60 s par centre. *Source :* `LicenseEnforcementFilter`.
- **RG-SEC-032** — Contrôle en ligne quotidien (03:15, instances clientes avec URL d'autorité configurée) : une licence
  révoquée
  chez l'autorité est révoquée localement ; un échec réseau n'est que journalisé. La période de grâce hors ligne
  (`offlineGraceDays`) est décomptée depuis le dernier contrôle réussi (ou l'activation). *Sources :*
  `LicenseOnlineCheckJob`,
  `LicenseService.isOnlineCheckStale`.
- **RG-SEC-037** — Quota de postes : `assertSeatAvailable` refuse un utilisateur actif de plus que le maximum de la
  licence (`LicenseRequiredException`). **Point d'attention :** ce contrôle existe dans `LicenseService` mais son appel
  est
  actuellement **désactivé** (commenté) dans `UserRestController.createUser` : la création d'un utilisateur n'est donc
  pas
  bloquée par le quota. *Source :* `LicenseService`, `UserRestController`.

## 2.8 Gestion des comptes

- **RG-SEC-038** — Création d'un utilisateur : identifiant unique (« Nom d'utilisateur déjà existant »), mot de passe
  haché, rôles
  filtrés (RG-SEC-003), rattachement à un ou plusieurs centres. Modification : le mot de passe n'est remplacé que s'il
  est fourni ;
  l'état actif/inactif est propagé à la fiche infirmier liée ; la suppression supprime aussi le lien infirmier.
  *Source :* `UserRestController`.

## 2.9 Installation initiale

- **RG-SEC-039** — À la première ouverture, tant qu'aucun propriétaire (SUPERADMIN) n'existe, l'écran d'installation
  permet de
  créer ce compte ; la création est **sérialisée** (jamais deux propriétaires), refusée dès qu'un propriétaire existe
  (`SETUP_ALREADY_DONE`), protégée par un jeton d'installation facultatif (`SETUP_TOKEN_INVALID`), avec identifiant
  valide (`USERNAME_INVALID`), identifiant libre (`USERNAME_TAKEN`), email valide (`EMAIL_INVALID`) et mot de passe
  conforme à la
  politique. *Sources :* `InitialSetupService`, `InitialSetupRestController`.

## 2.10 Temps réel — abonnements de société

- **RG-SEC-040** — Un abonnement à un canal de société est refusé (« canal réservé à la direction de la société ») sauf
  pour un
  compte `DIRECTION` rattaché à cette société, revalidé en base. *Source :* `WebSocketAuthentication`.

## 2.11 En-têtes et CORS

- **RG-SEC-041** — Réponses durcies : politique de sécurité de contenu restrictive, interdiction d'affichage en cadre,
  type MIME
  imposé, politique de référent stricte ; CORS limité aux origines configurées, avec cookies. Routes publiques :
  connexion,
  déconnexion, rafraîchissement, annuaires de sociétés/centres, installation initiale, ping système, actuator health,
  documentation d'API et WebSocket ; tout le reste exige une authentification. *Source :* `SecurityConfig`.

## 2.12 Journal d'audit (« qui a fait quoi »)

- **RG-SEC-050** — Toute écriture (`POST`, `PUT`, `PATCH`, `DELETE`) sur `/api/v1/**` est tracée automatiquement
  (utilisateur,
  rôles, centre, société, action, entité, gabarit de route, statut HTTP, durée, adresse IP) ; le **gabarit** de route
  est
  journalisé, jamais l'URL brute (pas d'identifiant nominatif dans le libellé). Seule lecture tracée : la consultation
  du dossier
  médical d'un patient. *Sources :* `AuditRequestFilter`, `AuditActionResolver`.
- **RG-SEC-051** — L'écriture du journal est **asynchrone** : file mémoire bornée à 5 000 évènements (les plus anciens
  sont
  abandonnés au-delà), vidée par lots de 500 toutes les 3 s ; un échec d'écriture est journalisé techniquement sans
  jamais
  affecter la requête. *Sources :* `AuditWriterService`, `AuditScheduler.flush`.
- **RG-SEC-052** — Purge nocturne (03:30) des entrées plus anciennes que la rétention (365 jours par défaut,
  `AUDIT_RETENTION_DAYS`). *Source :* `AuditScheduler.purge`.
- **RG-SEC-053** — La consultation du journal est paginée (taille max 200), filtrable (utilisateur, action, période,
  centre,
  société) et réservée à `ADMIN` (limité à son centre) et `SUPERADMIN` (toute la plateforme) ; jamais à la direction.
  *Sources :*
  `AuditRestController`, `AuditQueryService`.
