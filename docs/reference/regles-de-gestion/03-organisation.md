# 03 — Organisation : sociétés, centres, comptes d'encadrement

> Préfixe `RG-ORG`. Le propriétaire de la plateforme (SUPERADMIN) administre les sociétés, leurs centres, les comptes
> administrateur de centre et les comptes direction. Sources : `Societe`, `Centre`, `Coordonnees`,
> `OrganisationService`, `SocietesControleur`, `CentresControleur`, `SallesControleur`, `CreneauxControleur`,
> `ReferentielsControleur`, `ReferentielsLegacyControleur` (contexte Organisation), `SocieteAdminAccountService`,
> `DirectionAccountService`.

## 3.1 Société (agrégat racine)

- **RG-ORG-001** — Une société chapeaute un ou plusieurs centres. Elle ne peut être créée **qu'avec son premier centre**
  (`SOCIETE_SANS_CENTRE`). Une société reconstituée sans centre est refusée.
- **RG-ORG-002** — Code de société : 2 à 30 caractères (lettres majuscules, chiffres, tiret, souligné ; normalisé en
  majuscules,
  commence par une lettre ou un chiffre) ; sinon `SOCIETE_CODE_INVALIDE`. Le code est **unique**
  (`SOCIETE_CODE_DEJA_UTILISE`).
- **RG-ORG-003** — Raison sociale obligatoire, 200 caractères maximum (`SOCIETE_NOM_INVALIDE`).
- **RG-ORG-004** — Identifiants légaux NIF, NIS, RC facultatifs, 40 caractères maximum chacun
  (`SOCIETE_IDENTIFIANT_INVALIDE`).
- **RG-ORG-005** — Pied de page des documents : 500 caractères maximum (`SOCIETE_PIED_PAGE_TROP_LONG`) ; vide = absent.
- **RG-ORG-006** — Une société désactivée n'est plus proposée à la connexion ; ses centres sont conservés ; elle peut
  être
  réactivée. Un centre de société désactivée ne peut pas se connecter (RG-SEC-013).
- **RG-ORG-007** — Société introuvable : `SOCIETE_INTROUVABLE`.
- **RG-ORG-008** — La liste des sociétés est paginée, filtrable par recherche, taille plafonnée à 100.

## 3.2 Centre

- **RG-ORG-010** — Code de centre : mêmes règles que le code de société (`CENTRE_CODE_INVALIDE`) ; unique sur toute la
  plateforme (`CENTRE_CODE_DEJA_UTILISE`).
- **RG-ORG-011** — Nom de centre obligatoire, 150 caractères maximum (`CENTRE_NOM_INVALIDE`).
- **RG-ORG-012** — Un centre appartient à exactement une société. Le rattacher une seconde fois à la même société est
  refusé (`CENTRE_DEJA_AFFECTE`) ; un centre inconnu de la société : `CENTRE_INTROUVABLE`.
- **RG-ORG-013** — **Invariant** : une société conserve toujours au moins un centre **actif**. On ne peut ni désactiver
  ni
  retirer (transférer) son dernier centre actif (`SOCIETE_DERNIER_CENTRE`). Désactiver un centre déjà inactif est sans
  effet.
- **RG-ORG-014** — Transfert d'un centre vers une autre société : refusé si source = cible
  (`CENTRE_TRANSFERT_MEME_SOCIETE`),
  si la société cible est désactivée (`SOCIETE_INACTIVE`) ou si la source perdrait son dernier centre actif ; le
  transfert des
  deux sociétés est **atomique** (tous deux enregistrés ou aucun).
- **RG-ORG-015** — Un centre inactif ne peut plus être sélectionné à la connexion (RG-SEC-010/013) mais ses données sont
  conservées.

- **RG-ORG-016** — Les écritures qui touchent à l'ensemble des centres d'une société (désactiver, transférer) prennent le
  **verrou de la société** (et, pour un transfert, celui de la société cible, dans l'ordre des identifiants) dans une
  transaction, avant de relire les centres actifs : deux désactivations simultanées ne peuvent pas laisser la société sans
  centre actif. Un verrou non obtenu dans les 5 secondes répond **409** `ORGANISATION_ECRITURE_CONCURRENTE` (à réessayer).
- **RG-ORG-017** — L'ordre des centres d'une société dans les réponses n'est pas garanti (le legacy ne l'était pas non plus,
  KB-ORG-04).

## 3.3 Coordonnées (société et centre)

- **RG-ORG-020** — Tous les champs sont facultatifs et nettoyés (vide = absent) : adresse (250), ville (100), wilaya
  (100),
  téléphone (30), email (150), site web (200) ; dépassement : `COORDONNEES_TROP_LONG`.
- **RG-ORG-021** — Téléphone : 6 à 25 caractères parmi chiffres, espace, point, parenthèses, tiret, `+` initial
  (`COORDONNEES_TELEPHONE_INVALIDE`). Email normalisé en minuscules (`COORDONNEES_EMAIL_INVALIDE`). Site web au format
  `[http(s)://]domaine.tld[/chemin]` (`COORDONNEES_SITE_WEB_INVALIDE`).
- **RG-ORG-022** — Ces coordonnées alimentent l'en-tête et le pied de page de **tous** les documents imprimés (voir
  RG-DOC).

## 3.4 Logo de la société

- **RG-ORG-030** — Le logo est un PNG ou un JPEG reconnu à son **contenu** (signature), jamais à l'extension ni au type
  déclaré ;
  le SVG est refusé. Taille maximale 512 Ko (`TOO_LARGE`), dimensions maximales 2000 × 2000 px contrôlées avant décodage
  (`DIMENSIONS`), image décodable (`IMAGE_INVALID`), fichier non vide (`EMPTY`), type valide (`FILE_TYPE`). Un refus
  répond 422
  avec la liste des violations ; l'envoi accepté est journalisé.
- **RG-ORG-031** — Le logo est servi sans mise en cache (`no-store`, `nosniff`, CSP `default-src 'none'`) ; il est
  imprimé dans
  l'en-tête des documents. Sa suppression est possible à tout moment.

## 3.4 bis Droits d'accès, garde de centre et référentiels du centre (décisions du 2026-11-02)

- **RG-ORG-060** — Sociétés et centres (`/api/v1/societes`, `/api/v1/organisation/societes`, `/api/v1/organisation/centres`) :
  réservés au SUPERADMIN. Le SUPERADMIN reste **limité au legacy** : sous `/api/v1/organisation`, il n'atteint que `societes`
  et `centres` ; salles, créneaux, médecins, machines, protocoles et référentiels lui répondent 403 `ROLE_SCOPE`, comme
  `/api/v1/referentials` et `/api/v1/admin/referentials`.
- **RG-ORG-061** — Écritures sur les salles (ouvrir, fermer), les créneaux, les médecins, les machines et les protocoles
  (`/api/v1/organisation/*`) : administrateur du centre (ADMIN) ; lectures : tout utilisateur authentifié. Le centre est celui
  de la **session** : un centre demandé différent, ou une session sans centre, répond 403. Le refus de droits est donné
  **avant** la lecture du corps (403 avant 400 ou 415). Modifier ou désactiver un élément d'un autre centre répond 404.
- **RG-ORG-062** — `GET /api/v1/referentials/*` : le centre demandé doit être celui de la session (403 sinon ; un compte
  ADMIN sans centre n'y a plus accès). Le legacy croyait le centre demandé par le client (KB-ORG-05). Les salles et les
  positions sont triées par code, les médecins par nom (le legacy ne triait pas).
- **RG-ORG-063** — Un identifiant ou un paramètre UUID illisible répond **400** (le legacy répondait 500, KB-ORG-02). Un corps
  absent ou illisible sur la création d'une société répond encore 500 comme le legacy (KB-ORG-01).
- **RG-ORG-064** — Un médecin n'est **jamais supprimé** : il est désactivé (`actif = faux`) et disparaît des listes de
  sélection, dans l'API d'Organisation comme dans l'administration générique des référentiels, car il est référencé par les
  patients et les prescriptions. L'administration générique refuse toujours la désactivation d'un médecin encore référencé
  (`REFERENTIAL_IN_USE`).
- **RG-ORG-065** — Fermer une salle est un indicateur du contexte Organisation : il n'a **aucun effet** sur la planification,
  qui lit toutes les salles (KB-ORG-07).
- **RG-ORG-066** — Les modifications de salles, de créneaux et de médecins invalident les caches de référentiels du centre
  (salles, positions, médecins, générateurs) **après** l'enregistrement de la transaction.

## 3.5 Administrateurs de centre (créés par le propriétaire)

- **RG-ORG-040** — Seul le SUPERADMIN crée, modifie, active/désactive les comptes `ADMIN` d'une société et réinitialise
  leur mot de
  passe ; le mot de passe n'est jamais renvoyé ni journalisé. Le refus (403) d'un profil sans droit passe **avant** la
  validation du corps (400), sur cette route comme sur les comptes direction (KB-IDN-2, corrigé le 2026-11-14, à confirmer
  métiers/DPO avant le 2026-12-15 ; c'était un 400 avant le 403).
- **RG-ORG-041** — Création : le centre est obligatoire (`CENTRE_REQUIS`) et doit appartenir à la société
  (`CENTRE_HORS_SOCIETE`) ;
  identifiant 3 à 50 caractères (minuscules, chiffres, point, tiret, souligné) (`USERNAME_INVALID`) et libre
  (`USERNAME_TAKEN`) ;
  email valide si renseigné (`EMAIL_INVALID`) ; mot de passe selon RG-SEC-019b. Le compte reçoit le rôle `ADMIN`.
- **RG-ORG-042** — Modification : un administrateur est rattaché à **au moins un** centre de la société
  (`CENTRE_REQUIS`) ; chaque
  centre doit appartenir à la société ; les rattachements à des centres d' **autres sociétés** ne sont jamais modifiés ;
  retirer
  un centre **révoque les sessions** ouvertes sur ce centre.
- **RG-ORG-043** — Désactiver un compte ou réinitialiser son mot de passe **révoque toutes ses sessions** ; seul un
  compte portant
  le rôle `ADMIN` et rattaché à la société est concerné (`COMPTE_INTROUVABLE`).

## 3.6 Comptes direction (créés par le propriétaire)

- **RG-ORG-050** — Un compte direction porte le rôle `DIRECTION`, est rattaché à une société (et jamais à un centre) ;
  mêmes règles
  d'identifiant, d'email et de mot de passe que RG-ORG-041.
- **RG-ORG-051** — Activation/désactivation et réinitialisation de mot de passe réservées au SUPERADMIN ; une
  désactivation coupe
  les sessions ouvertes ; un compte hors société ou sans rôle `DIRECTION` est introuvable (`COMPTE_INTROUVABLE`).
- **RG-ORG-052** — Les comptes direction n'ont accès qu'aux indicateurs anonymes (RG-SEC-021 et chapitre direction).
