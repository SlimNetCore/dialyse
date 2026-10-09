# 05 — Planification : placement des patients, planning de la semaine, salles, générateurs, capacité

> Préfixe `RG-PLN`. Le système **propose** les meilleures places, **refuse** les placements incohérents, **détecte** les
> conflits
> du planning réel et **replace** automatiquement un patient devenu à risque infectieux. Sources :
> `PlanificationAffectationService`, `PlanningSemaineService`, `PlacementPatientService`, `PlanningParametres`,
> `PlanningParametresService`, `PlanningDonneesJdbcAdapter`, `SalleGenerateursService`, `CapaciteSalleRegle`,
> `CapaciteTheoriqueCalculator`, contrôleurs `Planning*RestController`, `SalleGenerateursRestController`.

## 5.1 Paramétrage du planning (par centre)

- **RG-PLN-001** — Chaque centre paramètre : jours d'ouverture, salles d'isolement, ratio de sécurité « patients par
  infirmier » et
  nombre de « patients par poste et par série ». Valeurs par défaut : tous les jours ouverts, aucune salle d'isolement,
  ratio **4**, **3** patients par poste et par série.
- **RG-PLN-002** — Au moins **un jour d'ouverture** est requis (« Au moins un jour d'ouverture est requis »).
- **RG-PLN-003** — Ratio patients par infirmier : entier de **1 à 20**. Patients par poste et par série : entier de **1
  à 10**
  (sinon refus 400).
- **RG-PLN-004** — Une salle d'isolement doit appartenir au centre (« Une salle d'isolement n'appartient pas à ce
  centre »).
- **RG-PLN-005** — Lecture du paramétrage : `ADMIN`, `SECRETAIRE`, `MEDECIN`, `INFIRMIER` ; modification : `ADMIN` seul.
- **RG-PLN-006** — La semaine de dialyse commence le **dimanche** et se termine le samedi.

## 5.2 Données prises en compte

- **RG-PLN-010** — Un patient occupe une place dès que sa fiche porte une salle, un créneau (position) et au moins un
  jour de
  dialyse. Les patients **transférés, décédés, greffés ou guéris** ne tiennent plus de place **à partir de la date de
  l'évènement** (`date_evenement_etat`) : le patient transféré ou guéri garde sa place **jusqu'à cette date incluse**
  (dernière séance), le patient décédé ou greffé la libère **à cette date** (dernier jour occupé = la veille). Sans date
  d'évènement, la place est libérée immédiatement. Un séjour **occasionnel ou vacancier** occupe la place **de la date
  d'admission à la date de fin de séjour incluse** (sans date de fin : non borné) ; l'état permanent n'a pas de limite
  (règle détaillée RG-PAT-032). Une fois l'échéance dépassée, la fiche perd son affectation (RG-PAT-033). Un patient
  **en sommeil** garde sa place (évite un double placement à son retour). Les propositions de placement restent
  prudentes : une place dont la libération est future, ou dont le séjour n'a pas encore commencé, reste considérée comme
  occupée.
- **RG-PLN-011** — Seuls les générateurs **en service** (statut GMAO `EN_SERVICE`, non supprimés) de type générateur de
  dialyse,
  rattachés à une salle du centre, sont disponibles. Un générateur en maintenance, en attente de pièce, hors service,
  désactivé
  ou réformé n'est jamais proposé.
- **RG-PLN-012** — Un patient est **à risque infectieux** si sa **dernière** sérologie d'au moins un des marqueurs
  AgHBs, anti-VHC,
  ARN du VHC ou VIH est **positive** (la dernière par marqueur, départagée par date de prélèvement puis date de saisie).
- **RG-PLN-013** — Les fermetures datées sont les jours fériés et les jours de fermeture exceptionnelle du centre ; la
  proposition
  de placement signale les fermetures des **90 prochains jours** tombant sur les jours proposés.

## 5.3 Règles de placement

- **RG-PLN-020** — Un patient garde **le même créneau, la même salle et le même générateur** tous ses jours de dialyse.
- **RG-PLN-021** — Un générateur ne sert **qu'un patient** par jour et par créneau.
- **RG-PLN-022** — Une salle n'a de place pour un jour et un créneau que si l'un de ses générateurs en service est
  libre, après
  déduction des patients placés dans la salle sans générateur précis (ou sur un générateur hors service/d'une autre
  salle).
- **RG-PLN-023** — Seuls les jours d'ouverture du centre sont proposés ; des jours imposés fermés donnent l'alerte
  `JOURS_IMPOSES_FERMES` et aucune proposition.
- **RG-PLN-024** — **Isolement** : un patient à risque n'est placé qu'en salle d'isolement et jamais sur un générateur
  servant un
  patient sans risque ; un patient sans risque n'est ni placé en salle d'isolement ni sur un générateur dédié à un
  patient à risque.
  Aucun générateur ne mélange jamais patients à risque et patients sans risque.
- **RG-PLN-025** — Patient à risque sans aucune salle d'isolement paramétrée : alerte `AUCUNE_SALLE_ISOLEMENT`, aucune
  proposition.
- **RG-PLN-026** — Nombre de séances par semaine : de 1 à 7 (sinon 400) ; si des jours sont imposés, leur nombre fait
  foi.

## 5.4 Proposition de placement (aide à la décision)

- **RG-PLN-030** — Le système classe les propositions de **0 à 100**. Le score combine : espacement des jours (poids
  55 %), créneau
  souhaité (+20), salle souhaitée (+15), charge de la salle (+10 : répartition équilibrée) ; normalisé sur le maximum
  atteignable pour
  la demande (100 = placement idéal).
- **RG-PLN-031** — Espacement des jours : pénalité de 30 points par paire de jours consécutifs au-delà du minimum
  inévitable (`2n − 7`), 8 points par jour d'écart au-delà de l'écart idéal (`⌈7/n⌉`) ; léger avantage (−3 aux autres
  schémas) aux schémas usuels
  lundi-mercredi-vendredi, mardi-jeudi-samedi, lundi-jeudi, mardi-vendredi, mercredi-samedi.
- **RG-PLN-032** — Chaque proposition est accompagnée de **raisons** lisibles : jours imposés libres, jours bien espacés
  (score
  ≥ 95), salle d'isolement, créneau préféré, salle préférée, salle peu chargée (≥ 70 % libre), et des fermetures datées
  à venir.
- **RG-PLN-033** — Au plus **deux** propositions par salle et créneau (variété), au plus **30** au total (5 par défaut
  côté
  réplacement automatique, nombre demandé côté écran) ; départage : score, nom de salle, ordre du créneau, code du
  générateur.
- **RG-PLN-034** — Pour modifier le placement d'un patient, son placement actuel est **libéré** dans le calcul. Le
  risque est
  déduit de ses sérologies, sauf si l'utilisateur force l'option « isolement ».
- **RG-PLN-035** — Aucune mise en cache : la réponse reflète l'instant (RG-TRV-041). Accès : `ADMIN`, `SECRETAIRE`,
  `MEDECIN`,
  `INFIRMIER`, sous réserve des périmètres restreints (RG-SEC-022/023).
- **RG-PLN-036** — La **grille de disponibilité** donne, pour chaque salle, créneau et jour, la capacité (générateurs en
  service) et
  l'occupation ; un jour de fermeture hebdomadaire a une capacité nulle.

## 5.5 Planning réel de la semaine et détection de conflits

- **RG-PLN-040** — Le planning d'une semaine liste, par salle, créneau et jour, les patients présents (nom, générateur,
  risque) et la
  capacité (nulle un jour fermé). Un patient dont la place est libérée à une date de la semaine (RG-PLN-010) n'apparaît
  plus à partir de cette date (de même qu'un séjour avant sa date d'admission) et porte l'indication « place libérée le
  jj/mm/aaaa » (infobulle de la grille et détail du
  patient). Les jours fermés (fermeture hebdomadaire, férié, fermeture exceptionnelle) sont
  signalés avec leur
  motif.
- **RG-PLN-041** — Conflits détectés automatiquement : `GENERATEUR_DOUBLE` (générateur réservé deux fois),
  `GENERATEUR_INDISPONIBLE`
  (générateur hors service ou d'une autre salle), `SANS_GENERATEUR`, `ISOLEMENT_NON_RESPECTE` (patient à risque hors
  isolement, ou
  patient sans risque en isolement dès qu'une salle d'isolement existe), `SALLE_SURCHARGEE` (plus de patients que de
  générateurs),
  `GENERATEUR_MIXTE` (mélange à risque / sans risque, quel que soit le jour).
- **RG-PLN-042** — Le planning compte les patients **à replanifier** : ceux qui sont placés sur un jour fermé de la
  semaine.
- **RG-PLN-043** — Le planning de la semaine est borné par construction (une semaine) : il n'est pas paginé (RG-TRV-022)
  ni mis en
  cache. Accès `ADMIN`, `SECRETAIRE`, `MEDECIN`, `INFIRMIER` ; le médecin « seul » peut le lire (RG-SEC-023).
  L'interface explique la
  notation « N/M » (N patients pour M générateur (s)).
- **RG-PLN-044** — Les absences de patients de la semaine, les séances validées par l'infirmier et le détail d'un
  patient sont
  affichés sur le planning (voir RG-ABS-0xx).
- **RG-PLN-045** — Le planning de la semaine est recalculé à chaque affichage à partir des jours et de la place
  **actuels** des patients. Une **séance réalisée** (validée, signée ou facturée) que ce calcul ne montre plus — jours
  ou place du patient modifiés depuis — reste affichée dans la case où elle a eu lieu (place mémorisée à la validation,
  RG-SEA-049 ; à défaut, place actuelle du patient), avec le repère « réalisée hors du planning actuel ». Elle ne crée
  pas de conflit (RG-PLN-041) et ne compte pas parmi les patients à replanifier (RG-PLN-042). Une séance réalisée
  qu'aucune case ne peut accueillir (place non mémorisée, patient plus placé) est listée à part avec sa date. Les
  séances encore **prévues plus tard dans la même semaine** pour ce patient, et pas encore réalisées, portent
  l'alerte « séance déjà réalisée le jj/mm/aaaa » : le patient risque une séance en trop (par exemple séance du lundi
  validée puis jours passés au mercredi).

## 5.6 Contrôle du placement enregistré sur une fiche patient

- **RG-PLN-050** — Tout placement saisi sur une fiche (création ou modification) est vérifié **côté serveur** avec les
  mêmes règles que
  les propositions. Le premier manquement lève `PLACEMENT_<règle>`, parmi : `INCOMPLET` (salle, créneau, générateur ou
  jours
  manquants), `SALLE_INCONNUE`, `CRENEAU_INCONNU`, `JOUR_FERME`, `ISOLEMENT_REQUIS`, `GENERATEUR_INCONNU`,
  `GENERATEUR_INCOMPATIBLE`,
  `GENERATEUR_OCCUPE`.
- **RG-PLN-051** — Exceptions : fiche sans aucun placement (acceptée) ; fiche dont le placement n'a pas changé
  (acceptée, une anomalie
  héritée ne bloque pas une autre modification).
- **RG-PLN-052** — Un patient placé en salle d'isolement est traité comme à risque pour la compatibilité du générateur.
- **RG-PLN-053** — **Réaffectation automatique** : quand la sérologie d'un patient placé devient positive, il est
  replacé
  automatiquement en salle d'isolement (mêmes jours, créneau conservé si possible, meilleure proposition) et le centre
  est notifié (`patient replacé en isolement`). Sans place d'isolement, le patient **reste en place** et
  l'administration est alertée (`isolement impossible`) pour replanifier. Issues : aucun risque, non placé, déjà
  conforme, déplacé, à replanifier.

## 5.7 Salles et générateurs

- **RG-PLN-060** — Vue d'ensemble des salles (générateurs affectés, capacité, places restantes) : paginée, limitée au
  centre, non
  mise en cache, réservée à `ADMIN` et `SECRETAIRE`.
- **RG-PLN-061** — Capacité d'une salle : le nombre de générateurs affectés ne dépasse **jamais** la capacité fixée
  (`SALLE_CAPACITE_DEPASSEE`) ;
  sans capacité, aucune limite. Le contrôle s'applique à l'affectation d'un générateur à une salle
  (création/modification d'un équipement
  GMAO) en ignorant le générateur déjà présent dans la salle.
- **RG-PLN-062** — Une salle d'un autre centre est introuvable (aucune limite appliquée, jamais d'accès inter-centres).

## 5.8 Capacité théorique du centre

- **RG-PLN-070** — Générateurs de secours : **1 pour 8** générateurs, arrondi à l'entier supérieur (16 → 2 ; 17 → 3 ;
  0 → 0).
- **RG-PLN-071** — Postes actifs = générateurs installés (hors réformés) − générateurs de secours.
- **RG-PLN-072** — Capacité théorique (file active maximale) = postes actifs × séries par jour × patients par poste et
  par série (paramétrable, défaut 3). Exemple : 16 générateurs, 2 séries → 2 secours, 14 postes, 14 × 2 × 3 = **84
  patients**.
- **RG-PLN-073** — Niveau d'occupation : `SANS_CAPACITE` (aucune capacité calculable), `MARGE` (< 90 %), `PROCHE` (≥
  90 %),
  `ATTEINTE` (file active ≥ capacité). Taux = file active / capacité en % (une décimale).
- **RG-PLN-074** — Consolidation de plusieurs centres : générateurs, postes, capacités et files actives s'additionnent ;
  séries et
  patients par poste n'ont pas de sens consolidé (valent 0). La restitution à la direction est détaillée par centre
  (voir RG-DIR).

## 5.9 Optimisation du planning (moteur Timefold)

> Sources : `domain/planning/optimisation` (modèle, ports, `IndicateursOptimisationService`, `EmpreinteOptimisation`,
> `VerificationDeplacementsService`, `SchemasJoursService`, `MaintenanceGenerateursService`, `MotifProposition`),
> `infrastructure/optimisation/timefold` (`TimefoldOptimiseurAdapter`, `PatientsConstraintProvider`,
> `InfirmiersConstraintProvider`, `CompletConstraintProvider`, `MaintenanceConstraintProvider`, `PoidsOptimisation`),
> `OptimisationPlanningService`, `OptimisationApplicationService`, `PreferencesPlanningService`,
> `ReplanificationAutomatiqueService`, `OptimisationDonneesJdbcAdapter`, `OptimisationRunJdbcAdapter`,
> `PreferencePatientJdbcAdapter`, `ProfilInfirmierJdbcAdapter`, `ReglagesOptimisationJdbcAdapter`,
> `DeplacementTemporaireJdbcAdapter`, `OptimisationPlanningRecovery`, `ReplanificationAutomatiqueScheduler`,
> `PlanningOptimisationRestController`, `PlanningPreferencesRestController`, `CalendrierPropositionService`,
> `CalendrierPropositionJdbcAdapter`, `PlanningOptimiseReportService`.

- **RG-PLN-080** — L'optimisation **propose** un planning qui consomme le moins de ressources possible (générateurs,
  salles ouvertes, vacations d'infirmiers) sans enfreindre les règles de la planification. Cinq **périmètres** :
  `PATIENTS` (replace les patients : salle, créneau, générateur, et choisit les jours de ceux qui le demandent,
  RG-PLN-094), `ROULEMENT` (conçoit le roulement hebdomadaire des infirmiers face aux placements actuels),
  `COUVERTURE` (comble les cases en sous-effectif sur 1 à 8 semaines avec des remplaçants), `COMPLET` (patients et
  roulement **dans un seul modèle**, RG-PLN-095) et `MAINTENANCE` (déplacements temporaires des séances dont le
  générateur est indisponible, RG-PLN-096). Une proposition **n'est jamais appliquée automatiquement**, y compris
  celles de la replanification nocturne (RG-PLN-100).
- **RG-PLN-081** — Le calcul est **asynchrone** : le lancement répond immédiatement (`202`) avec une exécution
  `EN_COURS`, suivie ensuite (avancement et score de la meilleure solution). **Un seul calcul à la fois par centre**
  (`OPTIMISATION_DEJA_EN_COURS`). Les 20 dernières exécutions de chaque centre sont conservées (historique) ; au
  démarrage du serveur, toute exécution restée en cours passe en échec (« calcul interrompu par l'arrêt du serveur »).
  Nombre de calculs en parallèle : `PLANNING_OPTIMISATION_WORKERS` (défaut 2).
- **RG-PLN-082** — Paramètres et bornes (sinon refus 400) : début de l'horizon (ramené au dimanche de sa semaine),
  nombre de semaines 1 à 8 (**uniquement** pour `COUVERTURE` et `MAINTENANCE`, 1 sinon ; huit semaines couvrent une
  absence déclarée environ deux mois à l'avance), durée maximale de calcul
  **par phase** 2 à 300
  secondes (défaut 20), **stabilité** 0 à 10 (défaut 5 : 0 = tout peut changer, 10 = changer le moins possible),
  objectif des infirmiers `EQUITE` (défaut) ou `ECONOMIE`, vacations maximales par jour 1 à 3 (défaut 2) et par semaine
  1 à 14 (défaut 6). Les contraintes de personnel (durée d'une vacation, temps plein, repos hebdomadaire) viennent des
  réglages du centre (RG-PLN-098), lus à chaque lancement.
- **RG-PLN-083** — Données lues, toutes bornées au centre : générateurs en service (RG-PLN-011), patients actifs placés
  **et patients en attente de place** (jours prescrits mais salle ou créneau manquant), à l'exclusion des patients sortis
  ou dont le séjour est terminé (RG-PLN-010), ainsi que les patients sans jours dont la préférence demande de choisir
  les jours (RG-PLN-093) ; infirmiers actifs, profils (RG-PLN-097), roulement, absences et remplacements de l'horizon ;
  préférences et transporteurs (aller, retour) des patients ; compétences demandées (RG-PLN-097) ; indisponibilités
  des générateurs et déplacements temporaires déjà enregistrés (RG-PLN-096). Les **jours de dialyse d'un patient ne
  sont modifiés que si sa préférence le demande** (RG-PLN-094). Un patient dont aucun jour n'est ouvert (ou, jours à
  choisir, sans schéma possible) garde sa place. Hors couverture et maintenance, le planning est évalué sur la
  **semaine type** (sans fermeture datée, absence ni remplacement) : on compare des roulements, pas des aléas.
- **RG-PLN-084** — Placement des patients, règles **dures** : un générateur ne sert qu'un patient par jour et par
  créneau (RG-PLN-021) ; un patient à risque n'est placé qu'en salle d'isolement et un patient sans risque jamais
  (RG-PLN-024). Un patient garde la même place tous ses jours (RG-PLN-020). Un patient qu'aucune place ne peut accueillir
  reste « non placé » avec sa cause : `AUCUNE_PLACE`, `ISOLEMENT_IMPOSSIBLE` (aucune salle d'isolement) ou `JOURS_FERMES`.
- **RG-PLN-085** — Placement des patients, objectifs **souples** (poids relatifs) : vacations d'infirmiers exigées par le
  ratio (100 par vacation, une par tranche de patients par infirmier, case par case), salles ouvertes (30 par case salle
  × créneau × jour), générateurs utilisés (10), réserve de générateurs de secours à garder libre à chaque créneau et
  chaque jour (50 par générateur manquant, RG-PLN-070), stabilité : changer un patient de créneau coûte
  `3 × stabilité × 6`, de salle `2 × stabilité × 6`, de générateur seul `1 × stabilité × 6` ; préférences et jours
  choisis : RG-PLN-094. Placer un patient prime sur tous les objectifs souples. La recherche part des places actuelles
  (« ne rien changer » est la solution de départ).
- **RG-PLN-086** — Infirmiers, règles **dures** : jamais deux salles au même créneau le même jour (RG-INF-022), au plus
  N vacations par jour (RG-PLN-082). Un infirmier absent ce jour-là, non habilité pour une salle d'isolement ou déjà
  prévu sur le créneau n'est jamais candidat. Une vacation sans infirmier est signalée (« non pourvue ») et prime sur les
  objectifs souples.
- **RG-PLN-087** — Infirmiers, objectifs **souples** : dépassement hebdomadaire (500 par vacation au-delà du maximum),
  équité (somme des carrés des vacations) ou, avec l'objectif `ECONOMIE`, nombre d'infirmiers mobilisés (300 par
  infirmier), double vacation dans la journée (40), changement de salle dans la semaine (30), connaissance de la salle
  et du créneau d'après le roulement actuel (10 par habitude manquante), stabilité du roulement (`8 × stabilité`, hors
  couverture), au moins un infirmier qui n'est pas aide-soignant par case occupée (200), repos hebdomadaire, quota
  d'heures et compétences (RG-PLN-099).
- **RG-PLN-088** — Couverture : les infirmiers déjà prévus (roulement moins absences, plus remplacements en place) sont
  conservés tels quels ; seules les vacations manquantes sont à pourvoir, par des infirmiers disponibles sur le créneau.
  L'application crée des remplacements (RG-INF-052) ; les vacations déjà tenues sont ignorées.
- **RG-PLN-089** — Chaque proposition comporte les **indicateurs avant / après** : générateurs utilisés, salles
  ouvertes, vacations requises, places d'infirmier inutilisées, patients non placés, vacations non pourvues,
  infirmiers mobilisés, écart de charge, dépassements hebdomadaires. Ils sont **recalculés par le domaine** à partir des
  placements et vacations proposés, indépendamment du solveur ; la proposition liste aussi les déplacements (de →
  vers), les patients non placés, les vacations planifiées (avec l'indication « déjà prévue ») et les vacations non
  pourvues.
- **RG-PLN-090** — Arrêt anticipé : un calcul en cours peut être arrêté ; la meilleure solution trouvée est conservée
  et les phases restantes ne reçoivent que 2 secondes. La graine aléatoire du moteur est fixe, mais la limite de calcul
  étant un temps, deux exécutions identiques peuvent différer légèrement.
- **RG-PLN-091** — **Application** d'une proposition (`ADMIN` seul), tout ou rien : l'exécution doit exister dans le
  centre (`OPTIMISATION_INTROUVABLE`), être terminée avec un résultat (`OPTIMISATION_NON_APPLICABLE`) et ne pas être
  déjà appliquée (`OPTIMISATION_DEJA_APPLIQUEE`). Le centre ne doit pas avoir changé depuis le calcul : l'**empreinte**
  (SHA-256 des données lues) est recalculée, sinon `OPTIMISATION_PERIMEE` et il faut relancer. Chaque nouvelle place est
  revérifiée avec les règles de RG-PLN-050 face à l'état final des autres patients (`OPTIMISATION_PLACEMENT_<règle>`).
  Effets : `PATIENTS` déplace les patients et enregistre sur leur fiche les jours choisis par l'optimisation ;
  `ROULEMENT` remplace le roulement des infirmiers actifs (affectations identiques conservées, autres modifiées, créées
  ou supprimées) ; `COUVERTURE` crée les remplacements ; `COMPLET` fait les deux premiers ; `MAINTENANCE` enregistre
  les déplacements temporaires (RG-PLN-096) sans toucher aux places habituelles.
- **RG-PLN-092** — Accès : lancer, consulter, arrêter et lister (`ADMIN`, `SECRETAIRE`) ; appliquer (`ADMIN`). Interdit
  à l'infirmier « seul » et au médecin « seul » (RG-SEC-022/023). Aucune mise en cache (RG-TRV-041). L'historique est
  paginé (`page`, `size`) et ne charge pas le détail des propositions ; une exécution d'un autre centre est introuvable
  (`OPTIMISATION_INTROUVABLE`).
- **RG-PLN-093** — **Préférences de planification d'un patient** (`/api/v1/planning/preferences/patients`, `ADMIN`,
  `SECRETAIRE`, liste paginée des patients non sortis du centre, sans cache) : créneau préféré (facultatif, du centre,
  sinon `PREFERENCE_CRENEAU_INCONNU`) et, pour un nouveau patient ou des jours à revoir, « jours choisis par
  l'optimisation » avec un nombre de séances hebdomadaires de 1 à 7 (requis dans ce cas). Un patient d'un autre centre
  ou sorti est introuvable (`PREFERENCE_PATIENT_INTROUVABLE`). Une préférence ne modifie rien par elle-même : elle n'est
  prise en compte qu'au prochain calcul.
- **RG-PLN-094** — **Jours choisis et préférences dans le placement** : les schémas de jours candidats d'un patient aux
  jours à choisir sont ceux du bon nombre de séances, parmi les jours d'ouverture, dont l'espacement (même note que
  l'aide au placement, RG-PLN-031) est à au plus 15 points du meilleur (8 schémas au plus) ; ses jours actuels restent
  candidats s'ils ont le bon nombre de séances. Objectifs souples : écart d'espacement au meilleur schéma (2 par
  point), chaque jour actuel abandonné (`20 × stabilité`, au moins 20), créneau préféré non respecté (40), deux patients
  partageant un transporteur qui dialysent un même jour à des créneaux différents (15 par jour commun). Un patient dont
  seuls les jours changent figure parmi les déplacements avec ses nouveaux jours ; les jours sont écrits sur la fiche
  avant la nouvelle place, à l'application.
- **RG-PLN-095** — **Modèle conjoint** (`COMPLET`) : patients et vacations sont résolus ensemble sur la semaine type.
  Chaque case ouverte (salle × créneau × jour) reçoit autant de vacations potentielles que sa salle peut exiger
  d'infirmiers (générateurs de la salle rapportés au ratio) ; le besoin d'une case suit, pendant le calcul, les patients
  qui y sont placés. Règle moyenne : chaque vacation exigée est tenue (comptée avec les patients non placés). Objectifs
  souples : toutes les contraintes du placement (RG-PLN-085, 094) et des infirmiers (RG-PLN-087, 099), une vacation
  tenue au-delà du besoin de sa case (50), au moins un infirmier qui n'est pas aide-soignant par case servie (200) et
  les compétences demandées par les patients de la case. Le calcul part des places actuelles et du roulement actuel. La
  proposition ne garde que les vacations utiles face aux placements retenus (RG-PLN-091) et signale les manques.
- **RG-PLN-096** — **Maintenance des générateurs** (`MAINTENANCE`, 1 à 8 semaines datées) : un générateur est
  indisponible pendant une intervention GMAO `PLANIFIEE` ou `EN_COURS` (non supprimée) qui chevauche l'horizon ; sans
  date de fin, une intervention planifiée immobilise son jour de début, une intervention en cours tout l'horizon. Sont
  touchées les séances datées (jours d'ouverture hors fermetures, séjour en cours) dont la place effective — la place
  habituelle, ou le déplacement temporaire déjà enregistré pour cette date — est sur un générateur indisponible. Places
  possibles d'une séance : générateurs disponibles ce jour-là, de la même catégorie (isolement ou non, RG-PLN-024),
  qu'aucun autre patient n'occupe à ce créneau ce jour-là. Règle dure : deux séances déplacées ne prennent jamais la même
  place le même jour ; règle moyenne : chaque séance trouve une place, sinon elle est listée « sans solution » (à
  organiser) ; objectifs souples : garder le créneau (100), puis la salle (30). L'application enregistre un
  **déplacement temporaire** par séance (patient, date, salle, créneau, générateur, motif), qui remplace un déplacement
  existant du même patient à la même date ; la place habituelle ne change pas. Le planning de la semaine affiche le
  patient dans sa case temporaire à cette date (repère « déplacé temporairement ») et en tient compte pour les conflits
  (RG-PLN-040, RG-PLN-041).
- **RG-PLN-097** — **Profil de planification d'un infirmier** (`/api/v1/planning/preferences/infirmiers`, `ADMIN`,
  `SECRETAIRE`, liste paginée des infirmiers actifs du centre) : taux d'activité de 10 à 100 % (défaut 100 : temps
  plein) et compétences particulières `PEDIATRIE`, `CATHETER`. Un infirmier d'un autre centre ou inactif est introuvable
  (`PROFIL_INFIRMIER_INTROUVABLE`). Quota d'heures hebdomadaire = heures d'un temps plein (RG-PLN-098) × taux, arrondi à
  l'heure. Compétences demandées par un patient : `PEDIATRIE` s'il a moins de 18 ans au début de l'horizon, `CATHETER`
  s'il a un abord vasculaire actif de type `KT_TUNNELISE` ou `KT_AIGU`.
- **RG-PLN-098** — **Réglages de planification du centre** (`/api/v1/planning/preferences/reglages` ; lecture `ADMIN`,
  `SECRETAIRE`, modification `ADMIN`) : durée d'une vacation 1 à 12 h (défaut 5), heures hebdomadaires d'un temps plein
  10 à 60 (défaut 40), jours de repos minimum par semaine 0 à 6 (défaut 1), replanification automatique nocturne
  (défaut non). Hors bornes : refus 400. Un centre sans réglage enregistré a les valeurs par défaut.
- **RG-PLN-099** — **Contraintes de personnel** (objectifs souples, roulement, couverture et modèle conjoint) : chaque jour
  travaillé au-delà de `7 − repos minimum` dans une semaine (300), chaque heure au-delà du quota de l'infirmier, les
  vacations comptant la durée réglée (100 par heure, soit 500 pour une vacation de 5 h), chaque compétence demandée par
  un patient d'une case qu'aucun infirmier de la case n'a (250).
- **RG-PLN-105** — **Couverture d'une absence sur sa période.** L'alerte d'absence d'un infirmier
  (`INFIRMIER_ABSENCE_ENREGISTREE`, `INFIRMIER_ABSENCE_DECLAREE`) ouvre l'optimisation en `COUVERTURE` avec le **début**
  (aujourd'hui, ou le début de l'absence si elle est à venir) et le **nombre de semaines** nécessaires pour atteindre
  sa fin (8 au plus) : le calcul ne se limite plus à la semaine en cours. Le médecin, qui ne lance pas l'optimisation,
  est conduit au planning de la semaine de l'absence. *Sources :* `ParametresOptimisation.semainesJusqua`,
  `horizonPourAbsence` (frontend).
- **RG-PLN-100** — **Replanification automatique nocturne** (`ReplanificationAutomatiqueScheduler`, 02:30 UTC chaque
  nuit), pour les centres qui l'ont activée : enchaîne, chacun démarrant à la fin du précédent, la couverture (2
  semaines au moins, **étendue jusqu'à la fin de la dernière absence d'infirmier à venir, 8 semaines au plus** : une
  absence planifiée dans plusieurs semaines est couverte dès maintenant), la maintenance (2 semaines) à partir de la
  semaine en cours, puis le placement des patients de la semaine
  suivante, 30 secondes par phase, au nom de `SYSTEME`. Un calcul déjà en cours dans le centre interrompt l'enchaînement
  pour la nuit ; un centre en erreur n'empêche pas les autres. Une proposition terminée est notifiée aux administrateurs
  (`OPTIMISATION_PROPOSITION`) si elle apporte quelque chose : vacations à pourvoir ou non pourvues (motif
  `SOUS_EFFECTIF`), séances à déplacer ou sans solution (`MAINTENANCE`), vacations requises économisées ou patients en
  attente placés (`GAIN`). La notification est **enregistrée au journal du centre** : l'administrateur la retrouve à sa
  prochaine connexion, même s'il n'était pas connecté à 02:30 (RG-NOT-005), avec un lien direct vers la proposition.
  Rien
  n'est appliqué d'office : l'administrateur consulte la proposition dans l'historique et l'applique (RG-PLN-091).
- **RG-PLN-101** — **Planning calendaire de la proposition** (`GET /api/v1/planning/optimisations/{id}/calendrier`,
  `ADMIN`, `SECRETAIRE`) : à la fin du calcul, la proposition est **figée** en un calendrier semaine par semaine sur
  l'horizon (`CalendrierPropositionService`, table `planification_calendrier_case`, rattachée au centre et à
  l'exécution, purgée avec elle). Une ligne par salle et créneau ayant de l'activité ; une colonne par jour (dimanche à
  samedi). Chaque case indique les patients **avec leur générateur** (repères : patient à risque, déplacé par la
  proposition, place temporaire), les infirmiers (prévu, remplaçant, absent, nouveau), le nombre d'infirmiers
  **affectés** (absents exclus), le manque éventuel par rapport à l'effectif requis (ratio de la salle) et le
  **sur-effectif** (infirmiers prévus au-delà de l'effectif requis, y compris
  dans
  une salle que la proposition vide, RG-INF-047), à l'écran comme à l'impression ; un jour fermé est signalé avec son
  motif. Les infirmiers viennent des vacations
  de
  la proposition quand son périmètre planifie le personnel (`ROULEMENT`, `COUVERTURE`, `COMPLET`), sinon du roulement
  actuel. Le calendrier reflète l'état du centre **au lancement** : il ne change pas si le centre évolue ensuite. Une
  exécution sans calendrier (calcul antérieur à cette fonction, échec) est refusée par `OPTIMISATION_CALENDRIER_ABSENT`
  (relancer le calcul) ; l'exécution d'un autre centre est introuvable (`OPTIMISATION_INTROUVABLE`). **Impression**
  (`GET /api/v1/planning/optimisations/{id}/impression`) : modèle de document « Planning proposé par
  l'optimisation » (`planning_optimise`, §11.1 des règles du dépôt), A4 paysage, une page par semaine, identité de la
  société
  et du centre, indicateurs avant/après en en-tête. **Isolation par centre** : chaque exécution, historique, calendrier,
  impression et application est borné au centre de la session ; seul le nombre de calculs en parallèle
  (`PLANNING_OPTIMISATION_WORKERS`) est partagé, jamais les données.
- **RG-PLN-102** — **Suppression d'une exécution** (`DELETE /api/v1/planning/optimisations/{id}`, `ADMIN`) : une
  exécution terminée, en échec ou déjà appliquée peut être supprimée de l'historique du centre, avec son planning
  calendaire. La suppression n'annule jamais une proposition déjà appliquée (le planning du centre ne change pas). Un
  calcul encore en cours est refusé par `OPTIMISATION_SUPPRESSION_EN_COURS` (l'arrêter d'abord) ; l'exécution d'un autre
  centre est introuvable (`OPTIMISATION_INTROUVABLE`).
- **RG-PLN-103** — **Infirmiers sur le planning de la semaine et accès du médecin.** Le planning de la semaine
  (RG-PLN-043)
  affiche, dans chaque case salle × créneau × jour, en plus des patients et de leur générateur, les **infirmiers** de la
  case d'après la présence réelle de la semaine (`/api/v1/infirmiers/presence/semaine`, RG-INF-044) : prévus au
  roulement,
  remplaçants, absents (barrés, avec le type d'absence), le nombre d'infirmiers **affectés** (et non le nombre requis,
  qui n'apparaît que dans le manque) et, en rouge, le
  manque
  ou, au contraire, le sur-effectif (infirmiers en trop, RG-INF-047).
  La légende explique chaque repère. Si la présence ne peut pas être chargée, le planning des patients reste affiché
  sans les infirmiers. Le **médecin « seul »** a **le même planning** que les autres profils, avec les deux vues semaine
  et
  jour : c'est son accueil (`/medecin`, menu Planning), le même composant que le planning des séances ; il y lit
  patients, générateurs, infirmiers et légende, sans accès aux autres écrans de planification
  (optimisation, salles et générateurs). Dans le planning proposé par l'optimisation (RG-PLN-101), au survol d'un
  patient **déplacé** s'affiche sa place d'avant (salle · créneau · générateur), et pour une place temporaire sa place
  habituelle.
- **RG-PLN-104** — **Planning en temps réel et lecture seule du médecin.** Le planning de la semaine se **met à jour
  seul**
  pour tous ceux qui ont le droit de le voir (administrateur, secrétariat, médecin) : le scan du QR code d'un patient ou
  la validation d'une séance (`SEANCE_VALIDATED`, `SEANCE_CREATED`), la suppression d'une séance, l'absence d'un patient
  ou d'un infirmier, un déplacement de patients ou de séances, un placement modifié ou un générateur devenu indisponible
  rechargent la semaine affichée, sans action de l'utilisateur (de même la détection nocturne des absences,
  `ABSENCES_A_QUALIFIER`) ; seuls les évènements du centre actif comptent (RG-NOT-001). **Le planning de l'infirmier («
  Mon planning »)** suit la même règle : une absence de patient déclarée, même le jour J, y **barre** aussitôt le
  patient (couleur selon le statut de l'absence) et une séance validée y marque un soleil, comme sur le planning des
  séances. Pour le **médecin**, le planning est en **lecture seule** : il ne peut pas déclarer l'absence d'un
  patient (RG-ABS-015) ; le détail d'un patient reste consultable, avec les liens vers sa fiche et son dossier.
  *Source :* `PlanningSemaineComponent`, `evenementRafraichitPlanning`, `AbsencePatientRestController`.

