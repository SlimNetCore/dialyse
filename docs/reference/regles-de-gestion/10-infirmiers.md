# 10 — Infirmiers : référentiel, roulement, présence, absences, remplaçants, charge, comptes

> Préfixe `RG-INF`. Le système **calcule l'effectif requis** à partir du ratio de sécurité, **détecte à l'avance** les
> créneaux en sous-effectif, **propose et classe des
> remplaçants** et **équilibre la charge**. Sources : `Infirmier`, `AffectationInfirmier`, `AbsenceInfirmier`,
> `RemplacementInfirmier`, `Presence`, `PresenceInfirmierService`,
> `RemplacantService`, `ChargeInfirmierService`, `InfirmierService`, `AffectationInfirmierService`,
> `AbsenceInfirmierService`, `RemplacementInfirmierService`, `CompteInfirmierService`,
> `MonPlanningInfirmierService`, `GenerateurMotDePasseTemporaire`, `PresenceInfirmierScheduler`, contrôleurs
> `Infirmier*`, `PresenceInfirmier*`, `MonPlanningInfirmier*`.

## 10.1 Référentiel du personnel soignant

- **RG-INF-001** — Une fiche infirmier porte : matricule, nom (obligatoires), prénom, téléphone, **qualification**
  (`INFIRMIER`, `MAJOR`, `AIDE_SOIGNANT` — obligatoire), indicateur **habilité isolement**, état actif, et un éventuel
  compte utilisateur lié.
- **RG-INF-002** — Le **matricule est unique par centre** (`INFIRMIER_MATRICULE_EXISTANT`) ; fiche inconnue du centre :
  `INFIRMIER_INTROUVABLE`.
- **RG-INF-003** — Un infirmier **désactivé** n'apparaît plus dans le planning de présence mais reste au référentiel ;
  désactiver ou réactiver une fiche reliée à un compte désactive ou
  réactive aussi le compte, et inversement (un compte activé/désactivé dans l'administration des utilisateurs fait
  suivre sa fiche ; un compte supprimé délie la fiche sans la supprimer).
- **RG-INF-004** — Lecture du référentiel : `ADMIN`, `SECRETAIRE`, `MEDECIN`, `INFIRMIER` ; écriture : `ADMIN`. La liste
  est paginée.

## 10.2 Comptes liés aux fiches

- **RG-INF-010** — **Lier un compte existant** : le compte doit exister dans le centre avec le rôle infirmier
  (`COMPTE_INTROUVABLE`), ne pas être déjà relié à un autre infirmier (`COMPTE_DEJA_UTILISE`), et avoir le même état que
  la fiche (`COMPTE_ETAT_INCOHERENT`) ; la fiche ne doit pas déjà avoir de compte (`COMPTE_DEJA_LIE`).
- **RG-INF-011** — **Créer un compte depuis la fiche** : fiche active uniquement (`COMPTE_ETAT_INCOHERENT`), identifiant
  de 3 à 50 caractères `[A-Za-z0-9._-]`
  (`COMPTE_IDENTIFIANT_INVALIDE`), identifiant libre (`COMPTE_IDENTIFIANT_EXISTANT`) ; un **mot de passe temporaire** de
  14 caractères (majuscule, minuscule, chiffre, symbole, sans
  caractères ambigus) est généré, **affiché une seule fois** à l'administrateur et jamais journalisé ; le compte devra
  être modifié au premier accès (RG-SEC-025).
- **RG-INF-012** — Délier une fiche de son compte est toujours possible ; les comptes pouvant être liés sont listés
  (paginés). Dans la liste des infirmiers, chaque ligne porte une action **« Compte d'accès »** (icône lien, ou gestion
  de compte si déjà lié) qui ouvre la carte permettant de lier un compte existant, d'en créer un ou de le délier.
  *Source :* `InfirmiersComponent`.

## 10.3 Roulement (affectations)

- **RG-INF-020** — Une affectation place un infirmier dans une **salle**, sur un **créneau**, certains **jours de la
  semaine** (au moins un) ; un infirmier peut avoir plusieurs affectations.
- **RG-INF-021** — La salle et le créneau doivent appartenir au centre (`AFFECTATION_SALLE_INCONNUE`,
  `AFFECTATION_CRENEAU_INCONNU`) ; affectation inconnue : `AFFECTATION_INTROUVABLE`.
- **RG-INF-022** — **Jamais deux affectations d'un même infirmier sur le même créneau un même jour**
  (`AFFECTATION_CHEVAUCHEMENT`).

## 10.4 Absences des infirmiers

- **RG-INF-030** — Types : congé, maladie, formation, autre. Une absence couvre une période (bornes incluses) qui se
  superpose au roulement ; la fin ne précède pas le début ; **durée maximale 366
  jours** ; type obligatoire.
- **RG-INF-031** — Déclaration par l'administration ou le secrétariat (`ADMIN`, `SECRETAIRE`) pour un infirmier du
  centre ; suppression de même ; la liste est paginée et lisible par
  `ADMIN`, `SECRETAIRE`, `MEDECIN`, `INFIRMIER`. Absence inconnue : `ABSENCE_INTROUVABLE`.
- **RG-INF-032** — **« Mon planning »** : un infirmier connecté déclare lui-même ses absences **à venir ou en cours**
  (fin non antérieure à aujourd'hui, sinon `ABSENCE_PASSEE`) et l'administration et
  le secrétariat sont prévenus ; il ne retire que ses absences **non commencées** (début strictement postérieur à
  aujourd'hui, sinon `ABSENCE_NON_ANNULABLE`). Il ne voit ni ne modifie jamais
  les données d'un autre ; sans fiche liée à son compte : `INFIRMIER_NON_LIE`. Son planning de la semaine se présente
  comme le planning des séances : une **grille semaine** (salles et créneaux en lignes, jours du dimanche au samedi en
  colonnes) ou une **vue jour** (une carte par salle et créneau, onglets de jours), **limitées aux salles et créneaux
  où il est affecté**. Chaque case indique sa situation (prévu, remplaçant, absent), le nombre de patients de la salle,
  le nombre de collègues prévus avec lui (sans nom, un autre infirmier n'est jamais identifié), la salle
  d'isolement et le **détail des patients placés** dans cette salle et ce créneau ce jour-là (nom, générateur,
  signalement du risque infectieux, libération prochaine de la place) — uniquement ceux de ses propres cases ; les jours
  de fermeture du centre (fermeture hebdomadaire, férié, fermeture exceptionnelle) sont grisés.
  L'écran est responsive : défilement horizontal local de la grille, cartes en une colonne sur mobile. *Source :*
  `MonPlanningInfirmierService`, `MonPlanningComponent`.

## 10.5 Présence et effectif requis

- **RG-INF-040** — **Effectif requis** d'une case (salle, créneau, jour) = ⌈patients / patients par infirmier⌉ (0 sans
  patient) ; le ratio vient du paramétrage du centre (RG-PLN-001, 4 par
  défaut). Les patients sont ceux placés sur la case (RG-PLN-010).
- **RG-INF-041** — Les infirmiers prévus d'une case sont ceux du roulement, **absences déduites**, plus les remplaçants
  ajoutés ; en **salle d'isolement**, seuls les infirmiers **habilités** comptent dans l'effectif.
- **RG-INF-042** — Statut d'une case : `FERME` (fermeture hebdomadaire, férié ou fermeture exceptionnelle),
  `SANS_PATIENT`, `COUVERT`, `SOUS_EFFECTIF` ; le **manque** = requis − effectif compté (jamais négatif). Les absents
  normalement prévus sont listés avec leur type d'absence.
- **RG-INF-043** — **Conflits** : `DOUBLE_AFFECTATION` (un infirmier prévu dans deux salles sur le même créneau le même
  jour) et `NON_HABILITE` (infirmier non habilité prévu en salle d'isolement).
- **RG-INF-044** — La semaine de présence (dimanche → samedi) donne le nombre de cases en sous-effectif ; elle est
  calculée à la demande, non mise en cache, bornée au centre ; lisible par
  `ADMIN`, `SECRETAIRE`, `MEDECIN`, `INFIRMIER` (le médecin « seul » la lit, RG-SEC-023) et **imprimable**.
- **RG-INF-045** — **Alertes de sous-effectif** : liste des cases en sous-effectif sur un horizon donné (14 jours par
  défaut), de la plus proche à la plus lointaine, avec les absents
  responsables.
- **RG-INF-046** — **Contrôle quotidien à 07:15** (`PresenceInfirmierScheduler.controlerPresence`) : pour chaque centre
  ayant des infirmiers actifs, si au moins un créneau est en sous-effectif dans
  les 14 jours à venir, l'administration et le secrétariat reçoivent une notification temps réel
  (`INFIRMIER_SOUS_EFFECTIF` : nombre de créneaux et première date). Un centre en erreur
  n'empêche pas les autres.

- **RG-INF-047** — **Sur-effectif.** Chaque case porte aussi le nombre d'infirmiers **en trop** (`surplus`) : infirmiers
  comptés (en salle d'isolement, seuls les habilités comptent) moins l'effectif requis, jamais négatif ; dans une salle
  sans patient, tous les infirmiers prévus sont en trop. Le sur-effectif n'est **pas** un manque : le statut de la case
  reste `COUVERT` (ou `SANS_PATIENT`), aucune alerte n'est envoyée ; il est signalé en jaune avec « +N » sur le planning
  de
  présence et en « N infirmier (s) en trop » sur le planning des séances, pour réaffecter ces ressources aux cases en
  sous-effectif. C'est toutefois du personnel payé sans activité utile : le **contrôle quotidien de 07:15** (RG-INF-046)
  prévient aussi l'administrateur (`INFIRMIER_SUREFFECTIF`) dès qu'une case des 14 prochains jours est en sur-effectif —
  **y compris une case avec patients dont les infirmiers dépassent le ratio** (3 infirmiers pour 4 patients avec un
  ratio de 4) — avec le nombre de cases, de vacations en trop et les **heures payées sans activité utile** (vacations en
  trop × durée d'une vacation des réglages du centre, RG-PLN-098). Ce contrôle est indépendant de celui du
  sous-effectif : l'échec de l'un n'empêche pas l'autre. À l' **affectation** d'un infirmier à une salle (RG-INF-020),
  la
  réponse indique les jours de la semaine en cours où elle crée un sur-effectif ; l'écran affiche un avertissement, sans
  jamais refuser ni annuler l'affectation (un échec du calcul de cet avertissement est ignoré).

## 10.6 Remplaçants

- **RG-INF-050** — Pour une case, les candidats **exclus** sont : les infirmiers absents ce jour-là, ceux déjà prévus
  **sur ce créneau** (dans n'importe quelle salle), et, en salle d'isolement,
  les non habilités.
- **RG-INF-051** — Les autres sont **classés de 0 à 100** : base 40 ; +15 salle connue ; +10 créneau habituel ; +max
  (0 ; 20 − 4 × séances de la semaine) selon la charge ; +10 jour libre ou −25 si double vacation (déjà prévu sur un
  autre créneau le même jour) ; +10 habilité isolement pour une salle d'isolement. Les **raisons** sont affichées :
  salle connue, créneau habituel, charge faible (≤ 2 séances),
  jour libre, habilité isolement, double vacation. Départage par nom.
- **RG-INF-052** — **Affecter un remplaçant** (`ADMIN`, `SECRETAIRE`) : salle et créneau du centre, remplacé éventuel du
  centre (`INFIRMIER_INTROUVABLE`), et le remplaçant doit figurer parmi les candidats
  éligibles (`REMPLACEMENT_INELIGIBLE`) ; un infirmier ne peut se remplacer lui-même ; un remplaçant devenu absent n'est
  plus compté ; l'annulation d'un remplacement est possible (`REMPLACEMENT_INTROUVABLE` sinon).

## 10.7 Charge de travail

- **RG-INF-060** — **Charge mensuelle** d'un infirmier = séances du roulement (jours d'ouverture du centre, hors jours
  d'absence) + remplacements effectués dans le mois ; les jours d'absence sont
  comptés à part ; la **moyenne** du centre (une décimale) sert de repère d'équité pour répartir les remplacements. La
  liste est paginée.

## 10.8 Roulement et remplacements issus de l'optimisation

- **RG-INF-070** — Le roulement des infirmiers peut être **conçu** et les cases en sous-effectif **couvertes** par
  l'optimisation du planning (RG-PLN-080 à 100). L'application d'une proposition remplace le roulement des infirmiers
  actifs (`ROULEMENT`, `COMPLET`) ou crée des remplacements (`COUVERTURE`) ; elle respecte les mêmes règles que la saisie
  manuelle (jamais deux salles au même créneau le même jour, RG-INF-022 ; absents et non habilités exclus, RG-INF-050).
- **RG-INF-071** — Le **profil de planification** d'un infirmier (taux d'activité, compétences pédiatrie et cathéter,
  RG-PLN-097) et les **réglages** du centre (durée d'une vacation, temps plein, repos hebdomadaire, RG-PLN-098) sont
  pris en compte par l'optimisation : un temps partiel ne dépasse pas son quota d'heures, chacun garde son repos
  hebdomadaire et les patients qui le demandent sont suivis par un infirmier compétent (RG-PLN-099). Ils n'imposent
  rien à la saisie manuelle du roulement.

