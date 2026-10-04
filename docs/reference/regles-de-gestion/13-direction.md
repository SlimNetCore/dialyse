# 13 — Direction de société : tableaux de bord consolidés, anonymat, alertes, instantanés, temps réel

> Préfixe `RG-DIR`. La direction voit **tous les centres de sa société** sans jamais voir un patient : le système
> **consolide**, **compare** à la période précédente, **lève des alertes** et **diffuse
> les changements en temps réel**. Sources : `DirectionDashboardRestController`, `DirectionSnapshotRestController`,
> `AnonymityPolicy`, `DirectionAlertPolicy`, `DirectionAlertHistoryService`,
> `DashboardDiff`, `Direction*QueryService`, `DirectionSnapshotService`, `DirectionRealtimeService`,
> `DirectionReportPdfService`, `DirectionAccessGuard`, schedulers `Direction*`.

## 13.1 Périmètre et accès

- **RG-DIR-001** — Les routes `/api/v1/direction/**` sont réservées au rôle `DIRECTION`, en **lecture seule** ; la
  société est déduite de la session (jamais d'un paramètre) et revérifiée à chaque
  appel (RG-SEC-021, RG-SEC-030). Aucune requête n'est exécutée hors des centres de la société (filtre
  `center_id IN (centres de la société)`).
- **RG-DIR-002** — Toutes les réponses sont sans mise en cache (`no-store`).
- **RG-DIR-003** — **Période** : par défaut du 1er janvier de l'année en cours à aujourd'hui ; la date de début doit
  précéder la date de fin (`PERIODE_INVALIDE`) et l'amplitude ne dépasse pas **5 ans**
  (`PERIODE_TROP_LONGUE`). Tous les chiffres d'un écran (sauf mention contraire) sont ceux de la période choisie.
- **RG-DIR-004** — Chaque écran présente **par centre et pour l'ensemble de la société** ; un centre inactif apparaît
  marqué comme tel.

## 13.2 Anonymat (k-anonymat)

- **RG-DIR-010** — Seuls des agrégats sortent : aucun nom, identifiant ni ligne de patient. Tout **effectif compris
  entre 1 et 4** est **masqué** (affiché « < 5 ») ; zéro reste zéro. Le seuil est 5.
- **RG-DIR-011** — Tout taux calculé sur moins de 5 patients est masqué, ainsi que toute valorisation ou variation qui
  permettrait de déduire l'effectif. Une alerte sur un taux n'est jamais levée sur une
  valeur masquée ; les variations en temps réel ignorent un indicateur qui devient masqué ou publiable.

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
  HT / CA HT facturé de la période. Effectifs et valorisations faibles masqués. Sources : RG-ABS.

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
