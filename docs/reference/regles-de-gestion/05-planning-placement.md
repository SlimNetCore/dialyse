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
