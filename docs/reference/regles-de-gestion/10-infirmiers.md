# 10 — Infirmiers : référentiel, roulement, présence, absences, remplaçants, charge, comptes

> Préfixe `RG-INF`. Le système **calcule l'effectif requis** à partir du ratio de sécurité, **détecte à l'avance** les
> créneaux en sous-effectif, **propose et classe des
> remplaçants** et **équilibre la charge**. La présence, les alertes, le classement des remplaçants, la charge, l'impression et
> le contrôle de 07:15 sont servis par le contexte `planification` (slice P4) : service de domaine `PresenceDeLaSemaine`,
> `ClassementDesRemplacants`, `ChargeDesInfirmiers`, cas d'usage `ConsulterLaPresence`, `ClasserLesRemplacants`,
> `ConsulterLaChargeDesInfirmiers`, `ImprimerLaPresence`, `ControlerLaPresence`, contrôleur `PresenceControleur`, job
> `ControleDeLaPresenceDe0715`. Le référentiel des fiches et
> des comptes (10.1 et 10.2) est servi par le contexte `planification` (slice P2) : agrégat `Infirmier`, cas d'usage
> `EnregistrerUnInfirmier`, `ModifierUnInfirmier`, `DesactiverUnInfirmier`, `ReactiverUnInfirmier`, `LierUnCompte`,
> `CreerUnCompte`, `DelierLeCompte`, contrôleur `InfirmiersControleur`. Le **roulement, les absences, « Mon planning »
et les
> remplacements** (10.3, 10.4 et 10.6) sont servis par le même contexte (slice P3) : agrégats `RoulementDeLInfirmier`,
> `AbsenceInfirmier`, `Remplacement`, cas d'usage `AffecterAuRoulement`, `ModifierLAffectation`, `RetirerLAffectation`,
> `DeclarerUneAbsenceDInfirmier`, `RetirerUneAbsence`, `ConsulterMonPlanning`, `DeclarerMonAbsence`,
> `RetirerMonAbsence`,
> `AffecterUnRemplacant`, `AnnulerLeRemplacement`, contrôleurs `RoulementControleur`, `AbsencesDesInfirmiersControleur`,
> `MonPlanningControleur`, `RemplacementsControleur`.

## 10.1 Référentiel du personnel soignant

- **RG-INF-001** — Une fiche infirmier porte : matricule, nom (obligatoires), prénom, téléphone, **qualification**
  (`INFIRMIER`, `MAJOR`, `AIDE_SOIGNANT` — obligatoire), indicateur **habilité isolement** (facultatif à la saisie, faux par
  défaut), état actif, et un éventuel compte utilisateur lié. Bornes (refus 400 `BAD_REQUEST`, 26/10/2026) : matricule 50
  caractères, nom et prénom 255, téléphone 50. Un corps illisible, un champ obligatoire absent, une qualification inconnue ou une
  pagination négative sont refusés en 400 (un corps qui n'est pas du JSON : 415) ; une taille de page est plafonnée à 100 et la
  taille effective est renvoyée.
- **RG-INF-002** — Le **matricule est unique par centre, sans tenir compte de la casse** (`INFIRMIER_MATRICULE_EXISTANT`) ;
  il est enregistré **en majuscules** (26/10/2026 ; les fiches existantes gardent leur casse jusqu'à leur modification) ; fiche
  inconnue du centre : `INFIRMIER_INTROUVABLE`. Deux écritures du référentiel d'un même centre sont sérialisées (verrou du
  centre) ; une écriture qui n'obtient pas le verrou en 5 secondes est refusée en 409 `PLANIFICATION_ECRITURE_CONCURRENTE`.
  Chaque cas d'usage est **une seule transaction** (fiche et compte atomiques).
- **RG-INF-003** — Un infirmier **désactivé** n'apparaît plus dans le planning de présence mais reste au référentiel ;
  désactiver ou réactiver une fiche reliée à un compte désactive ou
  réactive aussi le compte, et inversement (un compte activé/désactivé dans l'administration des utilisateurs fait
  suivre sa fiche, **dans le centre de l'administrateur seulement** ; désactiver une fiche n'agit que sur le centre de la
  fiche). Aucune opération n'impose à un compte un état que la fiche n'a pas : réactiver une fiche déjà active dont le compte
  est désactivé, ou délier une fiche inactive dont le compte est actif, est refusé en 422 (`COMPTE_ETAT_INCOHERENT`,
  `COMPTE_DELIAISON_INCOHERENTE`). Un compte **lié à une fiche active ne se supprime pas** (422
  `COMPTE_LIE_A_UNE_FICHE_ACTIVE`) ; sinon ses fiches inactives sont déliées et le compte supprimé dans la même
  transaction. Les noms de la fiche et du compte ne sont pas synchronisés (à décider, KB-PLN-14).
- **RG-INF-004** — Droits (corrigé le 26/10/2026 : la règle disait « tous les rôles de centre peuvent lire », ce que le
  filtre de périmètre n'a jamais permis). Lecture du référentiel : `ADMIN` et `SECRETAIRE` ; le `MEDECIN` seul et
  l'`INFIRMIER` seul sont refusés (403 `ROLE_SCOPE`, seule la combinaison `MEDECIN` + `INFIRMIER` lit) ; le `SUPERADMIN` et la
  `DIRECTION` sont refusés aussi. Écriture : `ADMIN` seul ; la liste des comptes liables : `ADMIN` seul. Le refus de rôle est
  rendu avant toute lecture du corps (403 avant 400 et 415). La liste est paginée. *Garde :* matrice routes × profils de
  `AutorisationsDesInfirmiersCharacterizationIntegrationTest`.

## 10.2 Comptes liés aux fiches

- **RG-INF-010** — **Lier un compte existant** : le compte doit exister dans le centre avec le rôle infirmier
  (`COMPTE_INTROUVABLE`), ne pas être déjà relié à un autre infirmier (`COMPTE_DEJA_UTILISE`) ni à une fiche d'un autre centre
  (`COMPTE_LIE_A_UN_AUTRE_CENTRE` : un compte ne se lie qu'à un seul centre, 26/10/2026), et avoir le même état que
  la fiche (`COMPTE_ETAT_INCOHERENT`) ; la fiche ne doit pas déjà avoir de compte (`COMPTE_DEJA_LIE`).
- **RG-INF-011** — **Créer un compte depuis la fiche** : fiche active uniquement (`COMPTE_ETAT_INCOHERENT`), identifiant
  de 3 à 50 caractères `[A-Za-z0-9._-]`
  (`COMPTE_IDENTIFIANT_INVALIDE`), identifiant libre tous centres confondus et sans tenir compte de la casse
  (`COMPTE_IDENTIFIANT_EXISTANT`), adresse e-mail facultative mais au format valide et de 150 caractères au plus (400) ; un
  **mot de passe temporaire** de
  14 caractères (majuscule, minuscule, chiffre, symbole, sans
  caractères ambigus) est généré, **affiché une seule fois** à l'administrateur et jamais journalisé ; le compte devra
  être modifié au premier accès (RG-SEC-025). Le compte et la liaison sont créés dans une seule transaction.
- **RG-INF-012** — Délier une fiche de son compte est possible (le compte est conservé et redevient liable), sauf pour une
  fiche inactive dont le compte est actif (`COMPTE_DELIAISON_INCOHERENTE`) ; les comptes pouvant être liés sont listés
  (paginés). Dans la liste des infirmiers, chaque ligne porte une action **« Compte d'accès »** (icône lien, ou gestion
  de compte si déjà lié) qui ouvre la carte permettant de lier un compte existant, d'en créer un ou de le délier.
  *Source :* `InfirmiersComponent`.

## 10.3 Roulement (affectations)

- **RG-INF-020** — Une affectation place un infirmier **actif** dans une **salle**, sur un **créneau**, certains **jours
  de la
  semaine** (au moins un) ; un infirmier peut avoir plusieurs affectations. Affecter ou modifier le roulement d'un
  infirmier **désactivé** est refusé (`INFIRMIER_INACTIF`) ; retirer une de ses affectations reste possible. Écriture
  réservée à `ADMIN`.
  Ordre des contrôles : infirmier (`INFIRMIER_INTROUVABLE`), état, salle, créneau, chevauchement ; à la modification,
  l'affectation (`AFFECTATION_INTROUVABLE`) d'abord. Les entrées illisibles (corps, jour inconnu ou en minuscules,
  identifiant)
  sont refusées en 400 `BAD_REQUEST` après la vérification des droits (403 avant le corps).
- **RG-INF-021** — La salle et le créneau doivent appartenir au centre (`AFFECTATION_SALLE_INCONNUE`,
  `AFFECTATION_CRENEAU_INCONNU`) ; affectation inconnue ou d'un autre infirmier : `AFFECTATION_INTROUVABLE`.
- **RG-INF-022** — **Jamais deux affectations d'un même infirmier sur le même créneau un même jour**, même dans deux
  salles (`AFFECTATION_CHEVAUCHEMENT`). Le contrôle se fait **sous le verrou de la fiche de l'infirmier** : deux
  écritures simultanées
  du même infirmier sont départagées, la seconde est refusée (`AFFECTATION_CHEVAUCHEMENT`, ou 409
  `PLANIFICATION_ECRITURE_CONCURRENTE` si le verrou n'est pas obtenu en 5 s). Des chevauchements hérités d'avant ce
  verrou restent
  lisibles et supprimables ; on ne peut modifier l'une des affectations tant qu'elle chevauche l'autre.
- **RG-INF-023** — **Avertissement de sur-effectif** : la réponse d'une affectation ajoutée ou modifiée liste les jours
  de la **semaine courante du centre** où la case dépasse l'effectif requis (`joursEnSureffectif`, RG-INF-047) ; il
  n'est jamais
  bloquant et son calcul en échec est ignoré. « Aujourd'hui » est le jour du **fuseau du centre** (Africa/Algiers par
  défaut).
- **RG-INF-024** — Chaque écriture du roulement est tracée au journal d'audit sous un libellé métier
  (`AFFECTATION_AJOUTEE`,
  `AFFECTATION_MODIFIEE`, `AFFECTATION_RETIREE`) avec l'identifiant de l'affectation.

## 10.4 Absences des infirmiers

- **RG-INF-030** — Types : congé, maladie, formation, autre (en majuscules). Une absence couvre une période (bornes
  incluses)
  qui se superpose au roulement ; la fin ne précède pas le début ; **durée maximale 366 jours** ; type obligatoire ;
  **motif de
  255 caractères au plus** (blanc = sans motif, espaces de bord retirés). Une période, un type ou un motif inutilisable
  est
  refusé en 400 `BAD_REQUEST`. **Deux absences du même infirmier ne partagent jamais un jour** (doublon, chevauchement
  partiel,
  absence incluse : `ABSENCE_CHEVAUCHEMENT`, y compris par « Mon planning » ; des absences contiguës sont acceptées) ;
  le contrôle
  se fait sous le verrou de la fiche. Écrire une absence pour un infirmier désactivé est refusé (`INFIRMIER_INACTIF`).
- **RG-INF-031** — Déclaration par l'administration ou le secrétariat (`ADMIN`, `SECRETAIRE`) pour un infirmier du
  centre (`INFIRMIER_INTROUVABLE` sinon) ; suppression de même, **quelle que soit la date de l'absence ni son auteur**.
  Droits de
  lecture de la liste (paginée, du début le plus récent au plus ancien, 100 par page au plus, page ou taille négative
  refusée en
    400) : `ADMIN`, `SECRETAIRE`, et la **combinaison `MEDECIN` + `INFIRMIER`** ; le médecin seul et l'infirmier seul
         sont refusés (403 `ROLE_SCOPE`) par le filtre de rôles (règle corrigée le 26/10/2026 : elle disait « lisible
         par `ADMIN`, `SECRETAIRE`,
         `MEDECIN`, `INFIRMIER` », ce que le filtre n'a jamais permis aux profils seuls ; le test d'autorisation des 12
         routes verrouille
         la matrice). Absence inconnue : `ABSENCE_INTROUVABLE`. Le médecin est toujours prévenu d'une déclaration,
         l'administrateur
         aussi quand la saisie ne vient pas de lui ; **le retrait d'une absence est lui aussi notifié et diffusé** (type
         `INFIRMIER_ABSENCE_RETIREE`, les écrans de planification se rafraîchissent). L'auteur de la saisie est
         conservé.
- **RG-INF-032** — **« Mon planning »** : un infirmier connecté déclare lui-même ses absences **à venir ou en cours**
  (fin non antérieure à aujourd'hui **au jour du centre**, sinon `ABSENCE_PASSEE`) et l'administration, le secrétariat
  et le
  médecin sont prévenus ; il ne retire que ses absences **non commencées** (début strictement postérieur à
  aujourd'hui au jour du centre, sinon `ABSENCE_NON_ANNULABLE`) **et déclarées par lui** : une absence saisie par
  l'administration ne peut être retirée que par l'administration (`ABSENCE_NON_ANNULABLE`) ; une absence sans auteur
  connu (antérieure à la conservation de l'auteur) reste retirable par l'infirmier concerné. Il ne voit ni ne modifie
  jamais
  les données d'un autre (l'absence d'un collègue est `ABSENCE_INTROUVABLE`) ; sans fiche liée à son compte :
  `INFIRMIER_NON_LIE`. **Droits** : « Mon planning » est ouvert à `ADMIN`, `SECRETAIRE`, `INFIRMIER` (seul) et
  `MEDECIN` + `INFIRMIER` ; le médecin seul
  est refusé (403 `ROLE_SCOPE`) ; un compte `ADMIN` ou `SECRETAIRE` lié à une fiche l'utilise comme un infirmier (règle
  corrigée
  le 26/10/2026 : elle ne précisait pas ces droits). Ordre des contrôles : entrées illisibles (400), fiche liée, état,
  passé,
  chevauchement. Son planning de la semaine (semaine courante du **fuseau du centre** par défaut) se présente
  comme le planning des séances : une **grille semaine** (salles et créneaux en lignes, jours du dimanche au samedi en
  colonnes) ou une **vue jour** (une carte par salle et créneau, onglets de jours), **limitées aux salles et créneaux
  où il est affecté**. Chaque case indique sa situation (prévu, remplaçant, absent), le nombre de patients de la salle,
  le nombre de collègues prévus avec lui (sans nom, un autre infirmier n'est jamais identifié), la salle
  d'isolement et le **détail des patients placés** dans cette salle et ce créneau ce jour-là (nom, générateur,
  signalement du risque infectieux, libération prochaine de la place) — uniquement ceux de ses propres cases ; les jours
  de fermeture du centre (fermeture hebdomadaire, férié, fermeture exceptionnelle) sont grisés. Une fiche désactivée
  reste
  lisible. L'écran est responsive : défilement horizontal local de la grille, cartes en une colonne sur mobile.
  *Source :*
  `ConsulterMonPlanning`, `DeclarerMonAbsence`, `RetirerMonAbsence`, `MonPlanningComponent`.
- **RG-INF-033** — Chaque écriture d'absence est tracée au journal d'audit sous un libellé métier (`ABSENCE_DECLAREE`,
  `ABSENCE_RETIREE`, `ABSENCE_DECLAREE_PAR_L_INFIRMIER`, `ABSENCE_RETIREE_PAR_L_INFIRMIER`) avec l'identifiant de
  l'absence.

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
  calculée à la demande, non mise en cache, bornée au centre. **Droits (corrigé le 27/10/2026 : la règle disait « lisible
  par `ADMIN`, `SECRETAIRE`, `MEDECIN`, `INFIRMIER` ») :** les routes de présence sont annotées pour ces quatre rôles mais
  le filtre de périmètre refuse le `MEDECIN` seul sur tout sauf la **semaine** (403 `ROLE_SCOPE` sur alertes, sur-effectif,
  remplaçants, charge, impression), refuse l'`INFIRMIER` seul partout (il lit son planning par « Mon planning », RG-INF-032),
  et refuse le `SUPERADMIN` et la `DIRECTION` ; seuls `ADMIN`, `SECRETAIRE` et la combinaison `MEDECIN` + `INFIRMIER` lisent
  tout. **Entrées (27/10/2026) :** un paramètre illisible ou manquant (`centerId`, `date`, `jours`, `page`, `size`, `mois`,
  `salleId`, `creneauId`) est refusé en 400 `BAD_REQUEST` ; sans date, **le jour du centre** (fuseau du centre, défaut
  Africa/Algiers) et non le jour UTC. *Garde :* matrice routes × profils de
  `AutorisationsDeLaPresenceCharacterizationIntegrationTest`.
- **RG-INF-045** — **Alertes de sous-effectif** : liste des cases en sous-effectif sur un horizon donné (14 jours par
  défaut, de 1 à 60, sinon 400), de la plus proche à la plus lointaine, avec les absents responsables. Le jour de départ est
  « aujourd'hui » au fuseau du centre.
- **RG-INF-046** — **Contrôle quotidien à 07:15** (`ControleDeLaPresenceDe0715.controlerLaPresence`, fuseau Africa/Algiers,
  « aujourd'hui » calculé **par centre**) : pour **chaque centre actif** (avec ou sans infirmier actif ; un centre désactivé
  n'est pas contrôlé), si au moins un créneau est en sous-effectif dans les 14 jours à venir, l'administration, le
  secrétariat et les médecins reçoivent une notification temps réel et durable (`INFIRMIER_SOUS_EFFECTIF` : nombre de
  créneaux et première date). **Une seule alerte par centre, par jour du centre et par type** (identifiant déterministe) :
  une nouvelle exécution le même jour n'envoie rien de plus (un sous-effectif apparu après la première exécution du jour n'est signalé que le lendemain). Un seul cron à 07:15 Africa/Algiers sert tous les centres : un centre d'un autre fuseau est contrôlé à une autre heure locale, mais sur son propre jour. Un **ratio illisible en base** (hors 1 à 20) est lu comme le
  défaut 4 et journalisé ; le ratio est refusé en 400 à l'écriture hors de 1 à 20 (RG-PLN-003). Un centre ou un contrôle en
  erreur n'empêche pas les autres (journalisé).

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
  jamais refuser ni annuler l'affectation (un échec du calcul de cet avertissement est ignoré). **Lecture par HTTP
  (27/10/2026) :** `GET /api/v1/infirmiers/presence/alertes-sureffectif?jours=` (horizon des alertes, RG-INF-045) liste les
  cases en sur-effectif (`sureffectifs`) **et** les infirmiers prévus dans deux salles au même créneau le même jour
  (`doublesAffectations` : date, créneau, infirmier, salles) — un tel infirmier reste compté présent dans chaque salle (la
  couverture peut être surestimée) et la semaine continue de signaler le conflit `DOUBLE_AFFECTATION` (RG-INF-043). Le
  contrôle de 07:15 ne notifie que les surplus par case.

## 10.6 Remplaçants

- **RG-INF-050** — Pour une case, les candidats **exclus** sont : les infirmiers absents ce jour-là, ceux déjà prévus
  **sur ce créneau** (dans n'importe quelle salle), et, en salle d'isolement,
  les non habilités.
- **RG-INF-051** — Les autres sont **classés de 0 à 100** : base 40 ; +15 salle connue ; +10 créneau habituel ; +max
  (0 ; 20 − 4 × séances de la semaine) selon la charge ; +10 jour libre ou −25 si double vacation (déjà prévu sur un
  autre créneau le même jour) ; +10 habilité isolement pour une salle d'isolement. Les **raisons** sont affichées :
  salle connue, créneau habituel, charge faible (≤ 2 séances),
  jour libre, habilité isolement, double vacation. Départage par nom ; **dix candidats au plus**. **La case est validée
  (27/10/2026)** comme pour l'affectation d'un remplaçant (RG-INF-052) : salle et créneau du centre (422
  `AFFECTATION_SALLE_INCONNUE`, `AFFECTATION_CRENEAU_INCONNU`), date non passée au jour du centre (422
  `REMPLACEMENT_DATE_PASSEE`), jour ouvert (422 `REMPLACEMENT_JOUR_FERME`) ; `date`, `salleId` et `creneauId` sont
  obligatoires (400).
- **RG-INF-052** — **Affecter un remplaçant** (`ADMIN`, `SECRETAIRE`). Ordre des contrôles : un infirmier ne peut se
  remplacer lui-même (400 `BAD_REQUEST`, **en premier**) ; salle et créneau du centre (`AFFECTATION_SALLE_INCONNUE`,
  `AFFECTATION_CRENEAU_INCONNU`) ; **date non passée** au jour du centre (`REMPLACEMENT_DATE_PASSEE`) et **jour ouvert**
  (`REMPLACEMENT_JOUR_FERME` : fermeture hebdomadaire, férié, fermeture exceptionnelle) ; remplacé éventuel du centre
  (`INFIRMIER_INTROUVABLE`) qui doit être **absent ce jour-là ou prévu sur la case**
  (`REMPLACEMENT_REMPLACE_NON_CONCERNE`) ; remplaçant **éligible** (`REMPLACEMENT_INELIGIBLE` : inconnu, autre centre,
  désactivé, absent, déjà prévu ou déjà remplaçant sur ce créneau, non habilité en salle d'isolement). L'éligibilité est
  jugée **sous le verrou de la fiche du remplaçant** : deux affectations simultanées du même remplaçant sur un créneau
  sont départagées (la seconde est `REMPLACEMENT_INELIGIBLE` ou 409 `PLANIFICATION_ECRITURE_CONCURRENTE`, jamais une
  erreur 500) ; un remplaçant
  devenu absent n'est plus compté ; l'annulation d'un remplacement est possible (`REMPLACEMENT_INTROUVABLE` sinon).
  Écritures
  tracées au journal d'audit (`REMPLACANT_AFFECTE`, `REMPLACEMENT_ANNULE`) avec l'identifiant du remplacement.

## 10.7 Charge de travail

- **RG-INF-060** — **Charge mensuelle** d'un infirmier = séances du roulement (jours d'ouverture du centre, hors jours
  d'absence) + remplacements effectués dans le mois **un jour de présence et d'ouverture** (un remplacement posé un jour
  d'absence du remplaçant ou de fermeture du centre n'est pas compté, 27/10/2026) ; les jours d'absence sont comptés à
  part ; la **moyenne** du centre (une décimale) sert de repère d'équité pour répartir les remplacements. La liste est
  **triée par nom de famille puis prénom** (27/10/2026) et paginée : page négative 400, taille plafonnée à **100** et
  taille effective renvoyée (`size`), mois illisible 400 ; sans mois, le mois courant du centre.
- **RG-INF-061** — **Impression** du planning de présence d'une semaine (`GET …/presence/imprimer`) par le modèle de
  document `PLANNING_PRESENCE_INFIRMIERS` du centre. Formats servis : `PDF` (par défaut, en ligne), `HTML` (une page qui
  embarque le PDF en base64) et `EXCEL`, `XLS`, `XLSX` (toujours un classeur `.xlsx`, en pièce jointe) ; tout autre format
  de modèle (`CSV`, `RTF`, inconnu) est refusé en 400 (27/10/2026). Un centre **sans** modèle utilise le modèle livré, sans
  que la lecture le crée (aucune écriture dans `modele_document`) ; un modèle **désactivé** refuse l'impression (422
  `DOCUMENT_MODEL_INACTIVE`). Depuis PLN-DOC (13/11/2026, ADR 0167) le rendu passe par le port publié de Documents en
  option « **lecture sans écriture** » (`formatDImpression`, `rendreSansProvisionner`) : la Planification n'écrit ni ne lit plus la
  table des modèles ; comportement inchangé (KB-PLN-51 déjà corrigé le 27/10/2026). **DOC5e (15/11/2026, ADR 0197)** : le modèle livré
  n'embarque plus de requête ; la Planification fournit les lignes du planning (infirmiers actifs au roulement du jour, prévus ou absents,
  et remplaçants posés ce jour-là) et lit les noms de salle et libellés de créneau par organisation.v1 ; contenu imprimé inchangé. Le
  téléversement d'un modèle portant l'ancienne requête est refusé `QUERY_MODIFIED` (KB-DOC5E-1, RG-DOC-004) ; l'ordre des lignes est
  désormais trié par le serveur, par points de code (KB-DOC5E-2).

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

