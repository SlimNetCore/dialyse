# 13 — Direction de société : tableaux de bord consolidés, anonymat, alertes, instantanés, temps réel

> Préfixe `RG-DIR`. La direction voit **tous les centres de sa société** sans jamais voir un patient : le système
> **consolide**, **compare** à la période précédente, **lève des alertes** et **diffuse
> les changements en temps réel**. Sources : `DirectionDashboardRestController`, `DirectionSnapshotRestController`,
> `AnonymityPolicy`, `Direction*QueryService`, `DirectionReportPdfService`, `DirectionAccessGuard`, et, dans le contexte Pilotage
> direction : `PolitiqueDAlertes`, `RapprochementDesAlertes`, `EnregistrerLesAlertes`, `ConsulterLHistoriqueDesAlertes`,
> `ConstatDesIndicateurs`, `SuivreLeTempsReel`, `TempsReelPlanifie` (ex `DirectionAlertPolicy`, `DirectionAlertHistoryService`,
> `DashboardDiff`, `DirectionRealtimeService`, `DirectionRealtimeScheduler`, retirés).

## 13.1 Périmètre et accès

- **RG-DIR-001** — Les routes `/api/v1/direction/**` sont réservées au rôle `DIRECTION`, en **lecture seule** ; la
  société est déduite de la session (jamais d'un paramètre) et revérifiée à chaque
  appel (RG-SEC-021, RG-SEC-030). Aucune requête n'est exécutée hors des centres de la société (filtre
  `center_id IN (centres de la société)`).
- **RG-DIR-002** — Toutes les réponses sont sans mise en cache (`no-store`).
- **RG-DIR-003** — **Période** : par défaut du 1er janvier de l'année en cours à aujourd'hui (« aujourd'hui » = le jour du fuseau de
  référence `Africa/Algiers`, **CORRIGÉ le 2026-11-10**, KB-DIR-03, RG-DIR-113) ; la date de début doit
  précéder la date de fin (`PERIODE_INVALIDE`) et l'amplitude ne dépasse pas **5 ans**
  (`PERIODE_TROP_LONGUE`). Tous les chiffres d'un écran (sauf mention contraire) sont ceux de la période choisie.
  Le tableau de bord s'ouvre sur le **mois en cours** (du 1er du mois à aujourd'hui) : le raccourci actif (« Ce mois »,
  « Ce trimestre », « Cette année », « 12 derniers mois ») est mis en évidence (bouton plein, coche, état exposé aux
  lecteurs d'écran) et aucun ne l'est pour une période personnalisée ; le bouton a un contour de focus bien visible au
  clavier. *Source :* `DirectionDashboardComponent`.
- **RG-DIR-004** — Chaque écran présente **par centre et pour l'ensemble de la société** ; un centre inactif apparaît
  marqué comme tel.

## 13.2 Anonymat (k-anonymat)

- **RG-DIR-010** — Seuls des agrégats sortent : aucun nom, identifiant ni ligne de patient. Tout **effectif compris
  entre 1 et 4** est **masqué** (affiché « < 5 ») ; zéro reste zéro. Le seuil est 5.
- **RG-DIR-011** — Tout taux calculé sur moins de 5 patients est masqué, ainsi que toute valorisation ou variation qui
  permettrait de déduire l'effectif. Une alerte sur un taux n'est jamais levée sur une
  valeur masquée ; les variations en temps réel ignorent un indicateur qui devient masqué ou publiable.
- **RG-DIR-012** — **Absences : masquage par centre.** Un centre dont les absences comptabilisées sont au nombre de 1 à 4
  est **masqué** (`null`) dans tous les chiffres qui en découlent : nombre d'absences, répartition justifiées / non
  justifiées / à qualifier, valorisation HT et TTC, taux d'absentéisme et part du chiffre d'affaires ; chaque effectif par
  statut de 1 à 4 est aussi masqué, y compris pour un centre publié, ainsi que le nombre d'absences rattrapées (1 à 4). Les
  séances réalisées et le chiffre d'affaires du centre restent publiés. **Un centre masqué est exclu du total**, des motifs
  consolidés et du mensuel, qui ne portent que sur les centres publiés ; la réponse indique `centresMasques` (nombre de
  centres exclus) et l'écran l'affiche. Ainsi la différence total moins centres publiés ne redonne pas le centre masqué
  (KB-ABS-07). Limite connue : si un seul effectif par statut d'un centre publié est masqué, il se déduit par différence avec le
  nombre d'absences du centre (dix absences dont neuf justifiées...) : le seuil protège les petits effectifs, pas toutes les
  inférences. Décision de l'utilisateur du 2026-10-22 (« masquer par centre seulement ») ; à valider avec la direction.

## 13.3 Vue d'ensemble et finances

- **RG-DIR-020** — Par centre et total : patients (effectif de la période, RG-PAT-037 : un patient sorti avant la
  période n'est pas compté, un patient sorti pendant ou après l'est), patients « sous KT », séances réalisées de la
  période (statuts validée, signée ou facturée), factures, chiffre d'affaires HT et TTC,
  montants encaissés des factures de la période, évolution mensuelle, et **comparaison avec la période précédente** de
  même durée (activité et finances seulement : le nombre de patients est un effectif
  instantané, il n'est pas comparé).

## 13.4 Indicateurs médicaux, stock et alertes

- **RG-DIR-030** — Par centre et total : patients évalués et répartition **dans / sous / au-dessus de la cible KDIGO**
  pour l'hémoglobine, la ferritine, le CST, le Kt/V, le phosphore, le calcium, la PTH
  et l'albumine, en retenant la **dernière valeur de chaque patient sur la période** ; patients porteurs d'une sérologie
  positive (VHB, VHC, VIH — dernier résultat positif à la fin de la période) ; patients avec
  retard d'observance non acquitté ; patients en attente de greffe, en bilan, greffés dans la période ; stock (articles
  actifs, sous seuil, valeur) et lots périmés ou proches de la péremption. Cibles : RG-MED-081.
- **RG-DIR-031** — **Alertes** levées par centre :
    - `KTV_CONFORMITE_BASSE` (avertissement) : moins de **80 %** des patients évalués ont un Kt/V à la cible ;
    - `HB_HORS_CIBLE` (avertissement) : moins de **50 %** des patients évalués ont une hémoglobine dans la cible ;
    - `OBSERVANCE_EN_RETARD` (avertissement) : au moins un patient a un retard d'observance constaté non acquitté ;
    - `STOCK_SOUS_SEUIL` (critique) : au moins un article actif dont le seuil d'alerte est renseigné (> 0) est au niveau
      ou sous ce seuil (le tableau de bord de stock du centre signale en plus les articles à seuil nul dont le stock est
      épuisé, RG-STK-060) ;
    - `LOTS_PERIMES` (critique) : au moins un lot périmé contient encore du stock ;
    - `LOTS_PEREMPTION_PROCHE` (avertissement) : au moins un lot expire dans les **90 jours**.
- **RG-DIR-032** — Un **historique des alertes** journalise leur apparition et leur résolution (en cours puis résolues,
  50 par défaut, borné). Il n'est alimenté que lorsqu'une direction est connectée à la
  société : c'est un historique récent, pas un audit exhaustif.

## 13.5 Répartitions

- **RG-DIR-040** — Par centre : patients par sexe et par tranche d'âge (0-17, 18-29, 30-44, 45-59, 60+, inconnu),
  patients / séances / chiffre d'affaires HT **par caisse d'assurance** (agence figée sur la
  facture ; « sans caisse » regroupé), et traitement de l'anémie (EPO, fer) ; effectifs faibles masqués.

## 13.6 GMAO : coûts, disponibilité et aide à la réforme

- **RG-DIR-050** — Par centre et total : équipements, hors service, en maintenance, réformés, interventions en cours,
  **coût de maintenance** de la période, **heures d'indisponibilité** cumulées, patients
  affectés à un générateur indisponible, équipements dont la réforme est recommandée, et **classement des 5 équipements
  les plus coûteux** de la période (complété par tous ceux dont la réforme est
  recommandée) avec coût de période, coût cumulé, ratio sur le prix d'acquisition et signal de réforme (RG-GMA-062/063).
  Aucune donnée patient n'est exposée.
- **RG-DIR-051** — Alertes GMAO : `GMAO_PATIENT_SUR_EQUIPEMENT_EN_MAINTENANCE` (**critique**, au moins un patient
  affecté à un générateur en maintenance, en attente de pièce, hors service, à réformer ou
  réformé), `GMAO_GENERATEURS_HORS_SERVICE` (avertissement), `GMAO_REFORME_RECOMMANDEE` (avertissement).

## 13.7 Groupes d'articles (kits)

- **RG-DIR-060** — Valorisation du stock des **groupes d'articles** sur la période, par centre et consolidée : stock de
  début, entrées, sorties valorisées au PMP, autres variations (inventaires, ajustements,
  arrondis) et stock de fin (RG-STK-043). Un même nom de groupe (insensible à la casse, aux accents et aux espaces)
  présent dans plusieurs centres est **consolidé en un seul groupe**.

## 13.8 Absences des patients

- **RG-DIR-070** — Par centre et total : nombre d'absences **comptabilisées** (ni rattrapées ni annulées), répartition
  par motif (« non qualifié » pour celles en attente), valorisation (HT et TTC), **taux
  d'absentéisme** = absences / (absences + séances réalisées) en %, et **part du chiffre d'affaires HT** = valorisation
  HT / CA HT facturé de la période. Effectifs et valorisations faibles masqués, y compris dans les totaux (RG-DIR-012). Sources : RG-ABS. La répartition par
  motif est présentée en tableau (nombre, part, valorisation) **et en deux graphiques en anneau** : l'un par **nombre
  d'absences**, l'autre par **valeur HT** ; les motifs masqués (effectif faible) et à zéro n'y figurent pas, et aucun
  graphique n'est affiché s'il ne reste aucun motif publiable. *Source :* `DirectionDashboardComponent`.

## 13.9 Capacité théorique et occupation

- **RG-DIR-080** — Par centre et total, pour la période : générateurs installés au plus tard à la fin de la période
  (hors réformés, désactivés, supprimés), générateurs de secours (1 pour 8), postes actifs,
  séries par jour (créneaux configurés), patients par poste et par série (paramètre du centre, 3 par défaut), **capacité
  théorique**, **file active** (effectif de la période, RG-PAT-037 : patients admis non sortis
  avant la période, hors « en sommeil ») et **taux d'occupation** avec son niveau (marge, proche de la saturation ≥
  90 %, atteinte, sans
  capacité). Le total additionne les centres ; séries et patients par poste n'ont pas de sens
  consolidé. Une file active de 1 à 4 patients est masquée avec son taux. Voir RG-PLN-070 à 074.

## 13.10 Instantanés mensuels et rapports

- **RG-DIR-090** — Le **dernier jour de chaque mois**, les agrégats anonymes de la société sont **figés** dans un
  instantané (tâche planifiée le 1er du mois à 02:30, ou à la première demande de la direction pour
  un mois écoulé). Un instantané **n'est jamais recalculé** : il garde sa valeur historique même si les données sources
  sont corrigées ensuite.
- **RG-DIR-091** — Le mois est au format `AAAA-MM` (`MOIS_INVALIDE`) ; seul un **mois écoulé** peut être figé
  (`MOIS_NON_TERMINE`) ; au plus 5 ans en arrière (`MOIS_TROP_ANCIEN`) ; un instantané absent :
  `SNAPSHOT_INTROUVABLE`. La liste des instantanés et chaque instantané sont consultables ; un rapport PDF est généré
  par mois.
- **RG-DIR-092** — **Rapport PDF** de la période : généré à la demande à partir des mêmes agrégats anonymes, avec
  l'en-tête (logo, raison sociale, coordonnées) et le pied de page de la société (RG-ORG-022/030).

## 13.11 Temps réel

- **RG-DIR-100** — Les changements d'indicateurs (familles patients, séances, finances, caisses, clinique, anémie,
  stock, alertes) sont **détectés par comparaison d'instantanés recalculés** — donc quelle que
  soit l'origine de la modification (saisie, import, tâche planifiée) — et diffusés à la direction connectée sur le
  canal de la société (RG-SEC-040). Au plus 60 changements par évènement.
- **RG-DIR-101** — Une écriture dans un centre signale la société « à recalculer » : traitement rapide toutes les
  **secondes** pour les sociétés signalées, balayage de sécurité toutes les **10 secondes** pour toutes
  les sociétés suivies. Seules les sociétés ayant au moins une direction connectée sont recalculées.
- **RG-DIR-102** — L'apparition ou la disparition d'une alerte est elle-même un changement diffusé ; les indicateurs
  masqués par l'anonymat ne sont jamais diffusés.

## 13.12 Calcul du tableau de bord et du rapport (contexte Pilotage direction)

- **RG-DIR-103** — La société, la vue d'ensemble, les répartitions (sexe, âge, caisse, anémie) et le **rapport de synthèse** sont calculés
  par le contexte **Pilotage direction** (langage publié `TableauDeBordDeLaDirection` et `RapportDeSyntheseDeLaDirection`) ; la plateforme
  ne fait que garder le jeton, porter les routes et rendre le PDF (HTML vers PDF). Règles reproduites à l'identique du fonctionnement
  antérieur : la période par défaut et ses refus (RG-DIR-003, réponse 422 avec le même code) sont contrôlés **avant** toute lecture ;
  la période précédente (même durée) n'est pas soumise à la limite de 5 ans ; l'effectif d'une période est celui de RG-PAT-037 ;
  l'âge d'une tranche est l'âge révolu **au dernier jour de la période demandée** (**CORRIGÉ le 2026-11-10**, KB-DIR-08, lot L6, ADR 0119,
  RG-DIR-113 ; avant, c'était le jour de l'appel) ; **CORRIGÉ le 2026-11-09 (KB-DIR-01, lot L1, ADR 0112, validation DPO
  attendue au plus tard le 2026-12-15)** : un total de patients (société, total d'une caisse, total du rapport) est publié nul dès que l'un
  des centres qui le composent est masqué (RG-DIR-111) ; avant, les totaux étaient calculés sur les effectifs bruts et la différence avec la
  somme des centres publiés révélait l'effectif masqué ; « aujourd'hui » est le jour du fuseau de référence `Africa/Algiers` (**CORRIGÉ le
  2026-11-10**, KB-DIR-03, RG-DIR-113 ; avant, la date du serveur en UTC). Le rapport mensuel imprime l'instantané figé (RG-DIR-090) et le rapport de la période le calcul en direct, avec
  les mêmes libellés d'alerte ; un code d'alerte inconnu est imprimé tel quel. *Source :* `ConsulterLaVueDEnsemble`,
  `ConsulterLaRepartition`, `ConstituerLeRapportDeSynthese`.

## 13.13 Instantanés mensuels (contexte Pilotage direction)

- **RG-DIR-104** — Les instantanés mensuels (RG-DIR-090 à 091) sont gérés par le contexte **Pilotage direction** (langage publié
  `InstantanesMensuelsDeLaDirection`, table `direction_snapshot` dont il est le seul propriétaire) ; la plateforme garde le jeton, la route
  et le rendu PDF. Règles reproduites à l'identique du fonctionnement antérieur : le mois est contrôlé **avant** toute lecture (illisible,
  puis non terminé, puis trop ancien) ; le mois d'il y a exactement 60 mois est capturable, celui d'il y a 61 mois est refusé ; un instantané
  contient la vue d'ensemble, les indicateurs et les répartitions du mois (bornes du premier au dernier jour) ; la capture est
  **idempotente** (un instantané existant n'est ni recalculé ni remplacé, même `generated_at` ; une capture simultanée perdue est ignorée) ;
  la **lecture** d'un mois futur répond `SNAPSHOT_INTROUVABLE` (aucun contrôle du futur en lecture, KB-DIR-06 b) ; un instantané antérieur
  aux répartitions est servi avec `breakdown` nul (KB-DIR-06 a) ; l'horodatage de capture est celui de la base (KB-DIR-06 c) ; un contenu
  illisible en base est **listé** mais sa lecture répond **422 `INSTANTANE_ILLISIBLE`** sans détail technique (**CORRIGÉ le 2026-11-10**, KB-DIR-06 e,
  RG-DIR-112 ; avant, 500) ; la tâche du 1er du mois à 02:30 (fuseau de référence, RG-DIR-113) fige le mois précédent de chaque
  société **active**, société sans centre comprise (instantané à zéro, KB-DIR-06 d), ignore l'inactive, est rejouable, et un échec sur une
  société n'empêche pas les suivantes. « Mois courant » = le mois du jour du fuseau de référence `Africa/Algiers` (**CORRIGÉ le 2026-11-10**,
  KB-DIR-03, RG-DIR-113 ; avant, la date du serveur en UTC, à confirmer métiers/DPO au plus tard le 2026-12-15). Comportements **clos par
  documentation, sans changement** (KB-DIR-06 a à d, à confirmer métiers/DPO au plus tard le 2026-12-15) : l'ancien instantané sans répartition
  est servi avec `breakdown` nul ; la lecture d'un mois futur répond `SNAPSHOT_INTROUVABLE` ; une société active sans centre est figée vide ; un
  instantané porte trois horodatages (la capture, la vue d'ensemble, les indicateurs). *Source :* `MoisDInstantane`, `InstantaneMensuel`, `CapturerUnInstantaneMensuel`, `ConsulterUnInstantane`,
  `ListerLesInstantanes`, `CapturerLesInstantanesDuMoisEcoule`.

## 13.14 Alertes et temps réel (contexte Pilotage direction)

Les règles RG-DIR-031, RG-DIR-032 et RG-DIR-100 à RG-DIR-102 sont désormais portées par le contexte **Pilotage direction** (lot C, PD3, ADR 0104)
sans changement de comportement ; les cinq règles ci-dessous (RG-DIR-105 à RG-DIR-109) précisent ce que le fonctionnement antérieur faisait sans l'écrire. Tous les
points marqués « statu quo » sont **à confirmer métiers / DPO au plus tard le 2026-12-15**.

- **RG-DIR-105** — La **politique d'alertes** (`PolitiqueDAlertes`, langage publié `AlertesDeLaDirection`) lève, par centre et dans cet ordre,
  les six codes de RG-DIR-031. Les seuils de taux sont **inclusifs du minimum** (Kt/V exactement 80 %, hémoglobine exactement 50 % : aucune
  alerte) ; un taux masqué par l'anonymat ne lève jamais d'alerte de taux ; l'observance en retard ne porte pas de valeur ; la valeur des alertes
  de stock est le nombre d'articles ou de lots. Les alertes de stock sont évaluées à la **date de fin de la période demandée**, pas à
  aujourd'hui (KB-DIR-07, statu quo). Les alertes GMAO (RG-DIR-040 et suivantes) utilisent le même enregistrement mais restent calculées par la
  plateforme. *Source :* `PolitiqueDAlertes`.
- **RG-DIR-106** — Le **temps réel** recalcule une société à chaque signal d'un de ses centres (toutes les secondes) et à chaque balayage (toutes
  les 10 secondes), **seulement si au moins une direction y est abonnée** ; le premier recalcul d'un abonnement n'est qu'un constat de
  référence (rien n'est diffusé) ; le dernier abonné parti, le constat est oublié ; un échec de lecture est journalisé et ignoré (rien n'est écrit,
  ni historique ni diffusion) ; un échec de **diffusion** survient après l'écriture de l'historique et le rangement du constat : l'historique
  est écrit et le changement est perdu, jamais rediffusé (statu quo) ; la diffusion est plafonnée à 60 changements. Un signal pour un centre inconnu est ignoré. Le suivi des
  abonnements STOMP reste dans la plateforme (RG-DIR-109). *Source :* `SuivreLeTempsReel`.
- **RG-DIR-107** — L'**historique des alertes** est alimenté par chaque recalcul. L'identité d'une alerte ouverte est le couple **(centre,
  code)**, sans notion de jour : tant qu'elle est ouverte, sa valeur, sa gravité et le nom du centre restent **figés à la première
  apparition** (stock sous seuil 3 puis 7 : reste 3) ; si elle disparaît, elle est résolue ; si elle réapparaît (même le jour même), c'est une
  **nouvelle ligne** ; une alerte du centre A n'affecte jamais celle du centre B ni une autre société (KB-DIR-04, option (a) de R2). **KB-DIR-04
  CLOS par documentation le 2026-11-10, sans changement de comportement** : l'historique est une trace d'**ouverture et de résolution** d'une
  alerte, pas un suivi de sa valeur ; la valeur, la gravité et le nom figés sont ceux de l'ouverture, et l'historique n'est alimenté que
  pendant qu'une direction est connectée (une apparition hors connexion n'est pas datée). Question métier ouverte : garder la valeur à
  l'ouverture ou la plus récente (à confirmer métiers/DPO au plus tard le 2026-12-15). Aucune contrainte d'unicité en base : deux recalculs
  simultanés peuvent ouvrir deux lignes (résiduel PD3-R2). L'horodatage est un instant en UTC pris sur l'horloge injectée du Pilotage
  (KB-DIR-03, RG-DIR-113). *Source :* `RapprochementDesAlertes`, `EnregistrerLesAlertes`.
- **RG-DIR-108** — La **consultation** de l'historique (`GET /api/v1/direction/alerts/history`) rend les entrées de la société de la session, de la
  plus récemment active (`COALESCE(résolue, vue)`) à la plus ancienne ; `limit` vaut 50 par défaut, est **ramené** à 1 (0 ou négatif) ou à 200
  (au-delà) sans refus ; un `limit` illisible répond **400 `BAD_REQUEST`** (**CORRIGÉ le 2026-11-10**, KB-DIR-02, RG-DIR-112 ; avant, 500).
  Le contexte est le seul propriétaire de la table
  `direction_alert_history`. *Source :* `ConsulterLHistoriqueDesAlertes`, `LimiteDHistorique`.
- **RG-DIR-109** — Une société est **suivie** (RG-DIR-106) tant qu'au moins un abonnement de direction au canal
  `/topic/societe/{id}/…` subsiste. Chaque couple (session, abonnement) n'est compté **qu'une fois** (un abonnement répété ne double pas le
  compte) ; le désabonnement d'un abonnement inconnu ne retire personne ; la **déconnexion** d'une session libère tous ses abonnements et eux
  seuls ; une destination qui n'est pas un canal de société, ou dont l'identifiant est illisible, est ignorée sans erreur. Le premier abonné
  d'une société pose son constat de référence au prochain passage rapide (une seconde au plus). Le contrôle des droits d'abonnement
  (direction de cette société seule) précède ce suivi et reste dans la plateforme (`WebSocketAuthentication`). Pas de comptage persistant :
  un redémarrage du serveur oublie les abonnements (les clients se réabonnent). *Source :* `DirectionSubscriptionTracker` (plateforme, filet :
  `LesAbonnementsDeLaDirectionCharacterizationTest`).

## 13.15 Composition des indicateurs de la direction (contexte Pilotage direction)

Les indicateurs servis par `GET /api/v1/direction/indicators` (RG-DIR-030, RG-DIR-031) sont désormais **composés par le contexte Pilotage
direction** (PD6, ADR 0106, langage publié `IndicateursDeLaDirection`) à partir des indicateurs cliniques du Dossier médical et du stock du
Stock, sans changement de comportement. Les points marqués « statu quo » sont **à confirmer métiers / DPO au plus tard le 2026-12-15**.

- **RG-DIR-110** — La période est contrôlée **avant** toute lecture (RG-DIR-003, 422 `PERIODE_INVALIDE` ou `PERIODE_TROP_LONGUE`). Les
  indicateurs cliniques de chaque centre et leur consolidé sont ceux du Dossier médical, **déjà masqués** (effectif de 1 à 4 publié nul, zéro
  reste zéro) ; **CORRIGÉ le 2026-11-09 (KB-MED-104, lot L1, RG-MED-049g)** : un consolidé est publié nul dès qu'un centre le porte masqué, et le
  Pilotage ne le recalcule jamais à partir des centres publiés. Le **stock** de chaque centre est évalué à la **date de fin** de la période
  (KB-DIR-07, **CLOS par documentation le 2026-11-10, sans changement** : c'est la sémantique d'un rapport de période, une vue historique
  cohérente ; une consultation d'une période passée montre donc des lots périmés et des alertes relatifs à cette date ; question ouverte
  « rétrospectif ou état courant », à confirmer métiers/DPO au plus tard le 2026-12-15) ;
  sa valeur est la quantité en stock multipliée par le prix moyen pondéré (un article sans prix vaut zéro), arrondie au centime (demi supérieur)
  **par centre**, tandis que les **totaux de la société additionnent les valeurs non arrondies** des centres puis arrondissent une seule fois
  (0,003 et 0,003 donnent 0,00 et 0,00 par centre mais 0,01 au total). Un centre **inactif** est servi comme les autres ; un centre inconnu du
  Stock a un stock à zéro. Une société **sans centre** reçoit des totaux à zéro, aucun centre et aucune alerte (le stock n'est pas lu). Les
  alertes (RG-DIR-105) sont levées centre par centre, dans l'ordre des centres, sur les taux déjà masqués et sur l'observance brute ; le seuil
  d'anonymat servi est 5 (`seuilAnonymat`). Le même résultat est gravé dans les instantanés mensuels (RG-DIR-090) et lu par le rapport de
  synthèse et le temps réel (RG-DIR-106). *Source :* `ConsulterLesIndicateursDeLaDirection`, `IndicateursDeLaSociete`, `StockDuCentre`.

- **RG-DIR-111** — *Suppression complémentaire des totaux (KB-DIR-01 et KB-MED-104, **CORRIGÉS le 2026-11-09**, lot L1, ADR 0112 ; correction
  faite à la demande de l'utilisateur, **validation du DPO attendue au plus tard le 2026-12-15**)* : un effectif de patients de 1 à 4 est masqué
  par centre (RG-DIR-030) ; un **total** est désormais publié **nul** (« < seuil » à l'écran et dans le PDF) dès qu'**au moins un** des effectifs
  qui le composent est masqué, de sorte que « total moins somme des centres publiés » ne révèle plus l'effectif masqué. Règle unique appliquée à :
  `totaux.patients` et `totaux.patientsSousKt` de la vue d'ensemble ; `caisseTotaux[].patients` de la répartition (un total par caisse, selon les
  centres qui portent **cette** caisse) ; les sept effectifs cliniques consolidés (RG-MED-049g) et, pour chacun des cinq marqueurs, les patients
  évalués **et** les taux consolidés ; la synthèse, la ligne « Total société » et le total de chaque caisse du rapport PDF ; la qualité des soins
  du rapport. Zéro reste zéro ; un effectif de 5 exactement n'est pas masqué ; une société à un seul centre masqué n'a pas de total. Les séances,
  le chiffre d'affaires, les factures et les administrations (RG-DIR-030) ne sont pas des effectifs de patients : inchangés. **Instantanés déjà
  figés** (RG-DIR-090) : ils contiennent des totaux bruts d'avant la correction ; ils sont **masqués à la lecture** (règle relue du JSON publié :
  total nul si une valeur de centre de la même colonne est nulle, y compris pour le rapport PDF d'un instantané ancien) sans **jamais réécrire**
  `direction_snapshot.payload` ; un instantané capturé après la correction est déjà conforme. **Résiduels de confidentialité non traités (DPO)** :
  effectif d'un centre moins la somme de sa ventilation sexe, âge ou caisse (une seule cellule masquée se déduit de l'effectif publié du centre) ;
  pourcentages cliniques d'un petit échantillon ; une société dont un centre est petit n'a plus jamais de total patients (effet voulu).
  **Précision du lot L8 (ADR 0120, revue L1-L2)** : « corrigé » ne vaut que pour les **totaux** ; le recoupement « effectif réel d'un centre moins la
  ventilation publiée » reste un **résiduel OUVERT**, **condition de mise en production avec avis du DPO** (2026-12-15). La règle a une seule
  définition dans le domaine (`SeuilDAnonymat.totalPublie` sur effectifs bruts ; `totalPublieDepuis` en est la lecture sur valeurs déjà publiées, test
  de parité) ; un champ absent ou illisible d'un centre d'un instantané figé est lu « masqué » (le total est supprimé : choix prudent, le payload
  figé ne sert plus de preuve du total brut) ; dans le PDF, le total d'une caisse sans code et celui d'une caisse au code vide se comparent à la
  même clé (code nul ou « — » = vide). **Question ouverte médecins** : `porteursPositifs` (RG-DIR-110, `MAX(date)`) peut différer de
  l'isolement (RG-PLN-012, dernier résultat conclusif) ; règle NON changée.
  *Source :* `BilanDuCentre.consolider`, `RepartitionDeLaSociete` (`CaisseDeLaSociete`), `SeuilDAnonymat.totalPublie`, `SeuilDAnonymat.totalPublieDepuis`,
  `MasqueDesTotauxFiges`, `ConstituerLeRapportDeSynthese`.

## 13.16 Qualité d'API, horloge et âge (lot L6, 2026-11-10, ADR 0119)

Corrections faites à la demande de l'utilisateur, **à confirmer métiers / DPO au plus tard le 2026-12-15**.

- **RG-DIR-112** — *Erreurs de requête de l'API de la Direction (KB-DIR-02 et KB-DIR-06 e, **CORRIGÉS le 2026-11-10**)* : une valeur illisible d'un
  paramètre (date `from` ou `to`, entier `limit`) répond **400 `BAD_REQUEST`** (« Paramètre illisible : <nom> ») sur **toutes** les routes de la
  Direction : `/overview`, `/breakdown`, `/report`, `/gmao`, `/alerts/history`, `/indicators`, `/absences`, `/stock-groupes`, `/capacite` (avant,
  500 sur les cinq premières). Sur les routes ajoutées, le **403** d'un profil sans droit passe **avant** le 400. Une **méthode non supportée**
  sous `/api/v1/direction/` répond **405 `METHOD_NOT_ALLOWED`** avec l'en-tête `Allow`, sans le message technique du serveur (avant, 500) ; le reste
  de l'application garde sa réponse. Un instantané mensuel dont le contenu stocké est **illisible** répond **422 `INSTANTANE_ILLISIBLE`** à la
  lecture (route de lecture, rapport PDF du mois, capture d'un mois déjà figé), sans détail technique ; il **reste listé** (la liste n'ouvre pas le
  contenu) et la donnée n'est jamais réécrite (sa réparation est hors périmètre). **Résiduel documenté (L8, ADR 0120)** : le 405 n'est pas précédé du
  contrôle des droits (l'erreur survient avant le choix du contrôleur) : un profil authentifié sans droit reçoit 405 + `Allow`, jamais 403 ; sans
  authentification, 401 comme partout (`anyRequest().authenticated()`). *Source :* `FormesIllisiblesDesRoutesAdvice`
  (et son gestionnaire de méthode non supportée), `InstantanesMensuelsPublies`, `ErreursDuPilotageAdvice`.
- **RG-DIR-113** — *Horloge et âge (KB-DIR-03 et KB-DIR-08, **CORRIGÉS le 2026-11-10**)* : « aujourd'hui » du Pilotage (fin de période par défaut,
  RG-DIR-003 ; mois courant et mois écoulé des instantanés, RG-DIR-104 ; capture du 1er du mois à 02:30) est le **jour du fuseau de référence
  `Africa/Algiers`** (le défaut que les autres contextes appliquent à un centre sans fuseau ; propriété `app.pilotage.fuseau-par-defaut`), et non
  celui de la machine ; l'horloge est **injectée** (les tests la figent) ; les horodatages (alertes, génération) sont des instants en UTC. Entre
  23 h et minuit UTC, il est déjà le lendemain à Alger : la fin par défaut et le mois écoulé suivent Alger. **Résiduel nommé « fuseau par
  centre »** : une société peut porter des centres de fuseaux différents et la table `societes` n'a pas de fuseau ; un fuseau par centre ou par
  société n'est pas tenté ici. Les tranches d'âge de la répartition (et du rapport) sont l'âge révolu au **dernier jour de la période demandée**
  (avant, au jour de l'appel, même pour une période passée) : une naissance postérieure à ce jour ou absente reste `INCONNU`. Le **sexe** reste lu à
  l'initiale (`M`, `F`, la casse et les blancs ignorés ; `''`, `X` et absent = « autre ») : tolérance existante, **documentée sans changement**
  (KB-DIR-08, à confirmer métiers). **Notes L8 (ADR 0120)** : (1) les tranches d'âge d'un instantané déjà figé gardent « l'âge au jour de la capture »,
  celles des instantanés nouveaux sont « à la fin de période » : le contenu figé n'a pas de marqueur de version, ne pas comparer les deux
  populations (question ouverte Direction) ; (2) la route `/gmao` calcule encore sa fin de période par défaut avec le fuseau de la JVM
  (`DirectionGmaoQueryService`) : résiduel déclaré, à traiter avec le fuseau par centre. *Source :* `HorlogeDuPilotage`, `PilotageDirectionConfiguration`, `CaptureDuMoisEcoulePlanifiee`,
  `ConsulterLaRepartition`, `TrancheDAge`.

- **RG-DIR-114** — *Rapport PDF de la direction (PL9, ADR 0148, KB-PL9-1 à 8 **CORRIGÉS le 2026-11-12**, KB-PL9-5 cumul de rôles et KB-PL9-6 polices reproduits)* : `GET /api/v1/direction/report` et `GET /api/v1/direction/snapshots/{mois}/report` sont servis par le Pilotage direction (`RapportDeLaDirectionControleur`, `ImprimerLeRapport`, `MiseEnPageDuRapport`) ; le rendu passe par `documents.v1` (type `RAPPORT_DIRECTION`, gabarit sans requête). Horodatages au fuseau d'Alger ; montants arrondis à la paire, sans décimale (à confirmer avant le 2026-12-15) ; matrice des caisses paginée par 10 centres avec « Total société » ; instantané vide = 422 `INSTANTANE_ILLISIBLE` ; routes voisines 404, `Accept` sans PDF 406 ; jeton illisible = 400 « Jeton de session illisible » ; consultation journalisée `DIRECTION_RAPPORT_IMPRESSION`. Société sans centre : rapport imprimé avec le gabarit standard. *Source :* `ImprimerLeRapport`, `RapportRenduParDocuments`, `rapport_direction.jrxml`.

- **RG-DIR-115** — *Instantanés mensuels de la direction servis par le Pilotage (PL10a, ADR 0150, KB-PL10a-1 à 5 **CORRIGÉS le 2026-11-12**, cumul de rôles reproduit)* : `GET /api/v1/direction/snapshots`, `GET /{mois}` et `POST /{mois}` sont servis par `InstantanesDeLaDirectionControleur` (mêmes URL, rôle DIRECTION, société de la session). Chemins voisins (barre finale, `/snapshots/<mois>/`, casse) : 404 ; `Accept` sans JSON : 406 ; contenu figé vide, non objet ou non fini : 422 `INSTANTANE_ILLISIBLE` ; lignes dont le mois n'est pas `AAAA-MM` : ignorées de la liste (jamais supprimées) ; la relecture masque aussi les effectifs de centre de 1 à 4 stockés en clair (seuil unique du Pilotage, à confirmer avec le DPO avant le 2026-12-15) ; liste et lecture journalisées (`DIRECTION_INSTANTANE_CONSULTATION`) ; horodatage de capture par l'horloge du Pilotage. *Source :* `InstantanesDeLaDirectionControleur`, `InstantanesMensuelsPublies`, `MasqueDesTotauxFiges`, `ListerLesInstantanes`.

- **RG-DIR-116** — *Tableau de bord de la direction servi par le Pilotage (PL10b1, ADR 0151, KB-PL10b-1 à 3 et 6 **CORRIGÉS le 2026-11-12** pour `/societe`, `/overview`, `/breakdown`, `/indicators` et `/alerts/history` ; mêmes URL, rôle DIRECTION, société de la session ; cumul de rôles reproduit)* : le rôle et la société sont vérifiés AVANT la lecture des paramètres (société inactive avec une date illisible : 403, non 400) ; chemins voisins : 404 ; `Accept` sans JSON, XML compris : 406 ; chaque consultation est journalisée (`DIRECTION_TABLEAU_DE_BORD_CONSULTATION`, refus compris) ; `from`/`to` au format `aaaa-mm-jj`, un paramètre répété est refusé (400), vide il est ignoré ; `limit` de l'historique des alertes en entiers décimaux ASCII (vide, répété, hexadécimal, `+7`, espaces, chiffres non ASCII : 400), borné ensuite à [1, 200] ; fin de période par défaut : jour d'Alger. Les quatre autres routes (`/gmao`, `/stock-groupes`, `/capacite`, `/absences`) suivent les mêmes règles depuis PL10b2 (RG-DIR-117). *Source :* `TableauDeBordDeLaDirectionControleur`.
- **RG-DIR-117** — *Lectures de la direction fournies par GMAO, Stock, Planification et Absences de patients, servies par le Pilotage (PL10b2 et PL10c, ADR 0152, KB-PL10b-1 à 5 **CORRIGÉS le 2026-11-12** pour `/gmao`, `/stock-groupes`, `/capacite` et `/absences` ; mêmes URL, rôle DIRECTION, société de la session ; cumul de rôles reproduit)* : mêmes règles que RG-DIR-116 — rôle et société vérifiés AVANT la lecture des paramètres (403 avant 400), chemins voisins 404, `Accept` sans JSON 406 (XML compris), consultation journalisée (`DIRECTION_TABLEAU_DE_BORD_CONSULTATION`), `from`/`to` au format `aaaa-mm-jj` (répété : 400, vide : ignoré), fin de période par défaut : jour d'Alger (et non plus celui du serveur), période de 5 ans au plus. Anonymat : un seul seuil, celui du Pilotage (effectif de 1 à 4 masqué, zéro reste zéro), pour la capacité (file active) et les absences (effectifs, motifs, mensuel) ; valeur 5 inchangée, à confirmer métiers/DPO avant le 2026-12-15. Les chiffres viennent des langages publiés de GMAO, Stock, Planification et Absences de patients. *Source :* `LecturesDeLaDirectionControleur`, `ConsulterLaGmaoDesCentres`, `ConsulterLeStockDesGroupes`, `ConsulterLaCapaciteDesCentres`, `ConsulterLesAbsencesDesCentres`.
