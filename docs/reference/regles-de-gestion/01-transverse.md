# 01 — Règles transverses

> Règles qui s'appliquent à **tous** les modules : isolation par centre, erreurs, pagination, cache,
> internationalisation,
> temps réel, traçabilité technique. Les identifiants `RG-TRV-nnn` sont stables : ne jamais les renuméroter.

## 1.1 Isolation multi-centres

- **RG-TRV-001** — Toute donnée métier appartient à un centre (`center_id`, UUID). Aucune donnée n'est créée, lue,
  modifiée ou
  supprimée sans être rattachée à un centre. *Sources :* `CenterAccessGuard`, `AGENTS.md §2`.
- **RG-TRV-002** — Le centre utilisé par une requête est **toujours celui de la session** (porté par le jeton). Si le
  client
  fournit un `centerId` différent, la requête est refusée (403 `ACCESS_DENIED`). Une session sans centre de rattachement
  est
  refusée sur les routes de centre. *Source :* `CenterAccessGuard.requireCenter`.
- **RG-TRV-003** — Exception : le propriétaire de la plateforme (SUPERADMIN) est transverse aux centres ; il doit alors
  préciser le centre visé (sinon 403 « centerId requis pour un accès transverse »). *Source :* `CenterAccessGuard`.
- **RG-TRV-004** — Les clés de cache incluent systématiquement le centre (aucune fuite inter-centres). Les écritures qui
  invalident une donnée mise en cache déclenchent l'éviction correspondante. *Sources :* `CacheConfig`, `AGENTS.md §6`.
- **RG-TRV-005** — Les canaux temps réel d'un centre sont `/topic/center/{centerId}/events` ; les canaux d'une société
  sont
  `/topic/societe/{societeId}/…` et réservés à la direction de cette société (voir RG-SEC-040). *Source :*
  `NotificationService`.

## 1.2 Contrat d'erreur de l'API

- **RG-TRV-010** — Une règle métier violée renvoie **HTTP 422** avec un corps
  `{title:"Regle metier violee", detail, code}`
  où `code` est un identifiant stable (ex. `ABSENCE_EXISTANTE`) que l'interface traduit. *Source :*
  `ApiExceptionHandler`.
- **RG-TRV-011** — Une requête invalide (argument illégal) renvoie **HTTP 400** `BAD_REQUEST` ; une validation de
  formulaire
  échouée renvoie **400** `VALIDATION_ERROR`. *Source :* `ApiExceptionHandler`.
- **RG-TRV-012** — Un état incohérent signalé par une exception d'état renvoie **HTTP 422** `BUSINESS_RULE_VIOLATION` et
  est
  journalisé côté serveur (aucun échec silencieux). *Source :* `ApiExceptionHandler`.
- **RG-TRV-013** — Un refus d'accès renvoie **HTTP 403** `ACCESS_DENIED` (et non 500). *Source :* `ApiExceptionHandler`.
- **RG-TRV-014** — Une erreur d'accès aux données renvoie **HTTP 500** `DATA_ACCESS_ERROR` ; toute autre erreur imprévue
  **HTTP 500** `INTERNAL_ERROR`. Une déconnexion du client en cours de réponse n'est jamais journalisée comme une
  erreur. *Source :* `ApiExceptionHandler`.
- **RG-TRV-015** — Deux erreurs de séance ont un code dédié (HTTP 422) : `SEANCE_STOCK_EXIT_DATE_IMMUTABLE` (la date
  d'une sortie de stock rattachée à une séance est immuable) et `SEANCE_FACTUREE_NON_MODIFIABLE` (toute modification
  d'une séance facturée est refusée, y compris son stock ; remplace depuis la slice F6 les anciens codes
  `BUSINESS_RULE_VIOLATION` et `SEANCE_BILLED_STOCK_EXIT_IMMUTABLE` pour ce cas). *Source :* `ApiExceptionHandler` (voir
  RG-SEA-002 et RG-SEA-023).
- **RG-TRV-016** — Toute erreur affichée à l'utilisateur est traduite à partir de son `code` (jamais le texte brut du
  serveur quand une clé de traduction existe) ; l'erreur s'affiche **à l'endroit de l'action** (formulaire, panneau),
  pas
  seulement en haut de page. *Source :* stores front, `AGENTS.md §19`.

## 1.3 Pagination

- **RG-TRV-020** — Toute liste est paginée : l'API accepte `page` (0-indexé, défaut 0) et `size` (défaut 20) et répond
  `{items, total, page, size}`. Aucune liste ne charge toutes les données sans limite. *Source :* `PagedResult`,
  `AGENTS.md §9`.
- **RG-TRV-021** — Côté interface, toute table de liste porte un paginateur (tailles proposées : 10, 20, 50, 100) ; le
  centre
  actif fait toujours partie du filtre de pagination. *Source :* `PagedListState`.
- **RG-TRV-022** — Exceptions bornées : les vues dont le volume est borné par construction (grille d'une semaine de
  planning,
  liste des salles d'une page) renvoient l'ensemble de la période sans pagination. *Sources :*
  `SuivreLaSemaine` (suivi de la semaine des absences).

## 1.4 Valeurs métier (shared kernel)

- **RG-TRV-030** — Un **montant** (`Money`) est obligatoire et ne peut être négatif. *Source :* `Money`.
- **RG-TRV-031** — Une **quantité** (`Quantite`) est obligatoire et ne peut être négative. *Source :* `Quantite`.
- **RG-TRV-032** — Un **poids** (`Poids`) est obligatoire, strictement positif et ne dépasse pas **500 kg**. *Source :*
  `Poids`.
- **RG-TRV-033** — Un **email** est obligatoire, normalisé en minuscules et doit respecter le format `x@y.zz`.
  *Source :* `Email`.
- **RG-TRV-034** — Un **téléphone** est obligatoire ; espaces, tirets et parenthèses sont ignorés, un préfixe `00`
  devient `+`,
  il compte 8 à 15 chiffres. *Source :* `PhoneNumber`.
- **RG-TRV-035** — Un identifiant de centre (`CenterId`) ne peut être nul. *Source :* `CenterId`.
- **RG-TRV-036** — Toutes les clés primaires sont des UUID ; tous les horodatages sont en UTC. *Source :*
  `AGENTS.md §10`.

## 1.5 Cache

- **RG-TRV-040** — Mis en cache (Caffeine, 10 000 entrées max, expiration après écriture) : référentiels (TTL 6 h par
  défaut,
  `CACHE_TTL_REFERENTIALS`), détail patient et associés (15 min, `CACHE_TTL_PATIENT_DETAIL`), listes patient (3 min,
  `CACHE_TTL_PATIENT_LIST`), comptage patient (3 min), valorisation du stock (30 min, `CACHE_TTL_STOCK_VALORISATION`),
  types de TVA (TTL référentiels), modèles d'impression personnalisés compilés (12 h), indicateur « mot de passe
  temporaire » (1 min). *Source :* `CacheConfig`.
- **RG-TRV-041** — **Jamais mis en cache** : données temps réel ou financières nécessitant une fraîcheur garantie —
  planning de
  la semaine, présence des infirmiers, absences, salles et générateurs, tableaux de bord de la direction (réponses
  `Cache-Control: no-store`), journal d'audit, résultats d'authentification. *Sources :* contrôleurs concernés.

## 1.6 Internationalisation et ergonomie

- **RG-TRV-050** — Les langues de l'application sont le **français** (défaut), l' **arabe**, le **kabyle** et l'
  **anglais** ; toute
  chaîne visible est traduite, aucune n'est écrite en dur ; toute nouvelle clé est ajoutée dans les quatre langues.
  L'arabe
  s'affiche de droite à gauche. *Source :* `frontend/public/i18n/*.json`, `AGENTS.md §19`.
- **RG-TRV-051** — Toute interface est responsive (mobile 320–767 px, tablette, bureau) sans débordement horizontal de
  la page ;
  les tableaux denses défilent horizontalement dans leur conteneur. *Source :* `AGENTS.md §4.1`, `§21`.
- **RG-TRV-052** — Un thème d'apparence (clair/sombre et variantes) est proposé à l'utilisateur. *Source :*
  `core/theme`.

## 1.7 Temps réel et notifications

- **RG-TRV-060** — Les évènements métier sont poussés en temps réel (STOMP sur `/ws`) vers les utilisateurs du centre ;
  chaque
  évènement porte un type, le centre, une charge utile et un horodatage ; un évènement peut cibler des rôles
  (`targetRoles`) : seuls ces rôles le voient dans la cloche de notifications. *Source :* `NotificationService`.
- **RG-TRV-061** — Chaque évènement de centre signale aussi au tableau de bord de la direction qu'une donnée a changé
  (rafraîchissement
  temps réel). *Source :* `NotificationService.send` → `TempsReelDeLaDirection.unCentreAChange` (Pilotage direction, RG-DIR-106).
- **RG-TRV-062** — La connexion WebSocket est permise sans authentification (les canaux de centre ne changent pas) ;
  seuls les
  canaux de société exigent une session de direction valide. *Source :* `WebSocketAuthentication`.

## 1.8 Tâches planifiées (vue d'ensemble)

| Tâche                                          | Cadence              | Rôle                                                        | Règles détaillées |
|------------------------------------------------|----------------------|-------------------------------------------------------------|-------------------|
| `AuditScheduler.flush`                         | toutes les 3 s       | écrit le journal d'audit par lots                           | RG-SEC-050        |
| `AuditScheduler.purge`                         | 03:30 chaque jour    | purge au-delà de la rétention (365 j)                       | RG-SEC-052        |
| `LicenseOnlineCheckJob.checkAll`               | 03:15 chaque jour    | contrôle en ligne des licences                              | RG-SEC-032        |
| `ControleDeNuitDesAbsences.controlerAbsences`  | 02:30 chaque jour    | détecte les absences de patients                            | RG-ABS-040        |
| `CaptureDuMoisEcoulePlanifiee`                 | 02:30 le 1er du mois | fige le mois écoulé                                         | RG-DIR-090        |
| `ControleDeNuitDeLObservance`                  | 06:30 chaque jour    | contrôle l'observance des prescriptions                     | RG-MED-073        |
| `ControleDesPeremptionsPlanifie.controlerLesPeremptions` | 07:00 chaque jour (fuseau du serveur, Africa/Algiers) | notifie les lots proches de la péremption (centres actifs) | RG-STK-061 |
| `ReplanificationAutomatiqueScheduler.replanifier` | 02:30 chaque nuit | replanification automatique (centres volontaires)        | RG-PLN-100        |
| `ControleDeLaPresenceDe0715.controlerLaPresence` | 07:15 chaque jour (Africa/Algiers) | alerte de sous-effectif infirmiers                          | RG-INF-046        |
| `TempsReelPlanifie`                            | 1 s et 10 s          | diffusion temps réel de la direction                        | RG-DIR-100        |

## 1.9 Navigation de l'interface

- **RG-TRV-070** — *Barre de module (19/11/2026)* : chaque module présente ses écrans dans une **barre de module**
  ordonnée en groupes séparés discrètement — d'abord le **Tableau de bord** du module, puis les **écrans de travail**,
  puis le **paramétrage** (et, pour l'Administration, la **supervision**). Une seule entrée est active à la fois (le
  plus long préfixe d'URL parmi les entrées du module) ; le fil d'Ariane suit une règle commune à tous les modules
  (identifiants techniques affichés « Fiche »). Une entrée n'est visible que si le profil y a accès (RG-SEC-022 à
  RG-SEC-024). Les libellés sont traduits dans les quatre langues (RG-TRV-050). *Source :* `ShellComponent`
  (`NavItemKind`).
- **RG-TRV-071** — *Tableau de bord et écran d'arrivée de chaque module* : le tableau de bord d'un module est à
  `/<module>/tableau-de-bord` (lecture seule, indicateurs cliquables vers la liste filtrée quand le filtre existe) ;
  cliquer sur le module mène à son **écran de travail**, pas à son tableau de bord (tableau ci-dessous). Le profil
  propriétaire de la plateforme (`SUPERADMIN`) garde sa barre d'administration sans tableau de bord ni groupes.
  *Source :* `ShellComponent`, routes des modules.

| Module | Tableau de bord (1re entrée de la barre) | Arrivée dans le module | Règles détaillées |
|---|---|---|---|
| Patients | `/patients/tableau-de-bord` | liste des patients (`/patients`) | RG-PAT-024, RG-PAT-042, RG-PEC-010 |
| Séances | `/seances/tableau-de-bord` | poste infirmier (`/seances`) | RG-SEA-029 |
| Infirmiers | `/infirmiers/tableau-de-bord` | grille de présence (`/infirmiers`) | RG-INF-062 |
| Stock | `/stock/tableau-de-bord` | état du stock (`/stock`) | RG-STK-042, RG-STK-044 |
| GMAO | `/gmao/tableau-de-bord` | liste des équipements | RG-GMA-067 |
| Facturation | `/facturation/tableau-de-bord` | calcul de la facturation (`/facturation`) | RG-FAC-024 |
| Règlements | `/reglement/tableau-de-bord` | saisie des règlements (`/reglement`) | RG-REG-003 |
| Comptabilité | — (pas de tableau de bord) | journal comptable (`/comptabilite`) | RG-CPT-007 |
| Direction | « Tableau de bord » = vue d'ensemble (`/direction`), puis « Analyses » et « Rapports » | vue d'ensemble | RG-DIR-118, RG-DIR-119 |
| Administration | — (comptes, puis paramétrage, puis supervision) | utilisateurs | RG-DOC-006 |

- **RG-TRV-072** — *Accueil du centre* (`/dashboard`, entrée « Accueil ») : une **tuile par module** accessible au
  profil (même règle que le menu des modules), avec 1 ou 2 chiffres clés lus sur les API des tableaux de bord et un
  lien vers le tableau de bord du module (RG-PEC-010). Une tuile dont la lecture est refusée par le serveur (403) est
  **masquée** plutôt qu'affichée vide ; une nouvelle lecture annule la précédente (une réponse tardive d'un autre
  centre n'écrase jamais l'affichage) ; un évènement temps réel du centre ne recharge que la tuile concernée
  (rafales regroupées). L'action « Ouvrir l'accueil » d'une notification y mène. *Source :*
  `CenterDashboardComponent`, `AccueilStore`, `accueil-tuiles.ts`.
