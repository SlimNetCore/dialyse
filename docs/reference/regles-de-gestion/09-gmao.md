# 09 — GMAO : parc d'équipements, interventions, plans de maintenance, coûts et aide à la décision

> Préfixe `RG-GMA`. La GMAO ne se contente pas d'enregistrer : elle **calcule** le coût de possession et
> l'indisponibilité de chaque équipement, **recommande la réforme**
> quand la maintenance cumulée devient excessive, **avertit** quand un patient est affecté à un générateur indisponible
> et **relance** les interventions et plans en retard.
> Le générateur de dialyse du parc est l'équipement GMAO : il n'existe pas de second référentiel de générateurs.
> Sources : `Equipement`, `Intervention`,
> `PlanMaintenance`, `Intervenant`, `LigneCoutIntervention`, `DocumentIntervention`, `IndisponibiliteCalculator`,
> `IndicateursIntervention`, `AideDecisionMaintenance`,
> `InterventionEquipementStatutService`, contrôleurs `web/rest/gmao/*`.

## 9.1 Accès

- **RG-GMA-001** — Tous les écrans et API GMAO sont réservés à `ADMIN` et limités au **centre de la session** : un
  équipement, une intervention, un plan ou un intervenant d'un autre centre est
  « non trouvé ». La **réforme** d'un équipement exige l'habilitation `GMAO_REFORME`, la **rectification** d'une
  intervention l'habilitation `GMAO_RECTIFICATION` (droits distincts de
  l'administration GMAO).
- **RG-GMA-002** — Toute liste GMAO (équipements, interventions, plans, intervenants) est paginée (RG-TRV-020) ; les
  statistiques du tableau de bord sont des comptages serveur, jamais
  recalculés côté interface.

## 9.2 Équipements

- **RG-GMA-010** — Types : générateur de dialyse, station de traitement d'eau, osmose inverse, ultrafiltre, charbon
  actif, adoucisseur, désinfectant chimique, filtre à particules, pompe
  d'eau, compresseur d'air, alarme de surveillance, autre.
- **RG-GMA-011** — Création : code, désignation, type, date d'installation obligatoires ; prix d'acquisition non
  négatif ; le **code est unique par centre** (« Un équipement avec ce code existe
  déjà dans ce centre ») ; le code, le type et la date d'installation sont **immuables** ensuite. La salle de
  rattachement est facultative.
- **RG-GMA-012** — Statuts : `EN_SERVICE`, `EN_MAINTENANCE`, `EN_ATTENTE_PIECE`, `HORS_SERVICE`, `DESACTIF`,
  `A_REFORMER`, `REFORME`.
- **RG-GMA-013** — Mise hors service : refusée si déjà hors service ou réformé ; réactivation : uniquement depuis
  `HORS_SERVICE` (jamais depuis `REFORME`) ; observation obligatoire non vide.
- **RG-GMA-014** — **Réforme** : fin de vie **définitive** (aucun retour en arrière, aucun changement de statut
  ensuite), motif obligatoire ; réservée à `GMAO_REFORME`. Un équipement
  réformé n'est plus compté dans la capacité théorique (RG-PLN-071) ni proposé au placement.
- **RG-GMA-015** — Chaque changement de statut (création, hors service, réactivation, réforme, état saisi par une
  intervention) est inscrit dans l' **historique de statuts** (statut précédent,
  nouveau, motif, auteur, date), source du calcul d'indisponibilité.
- **RG-GMA-016** — Capacité de salle : l'affectation d'un générateur à une salle respecte la capacité de la salle
  (RG-PLN-061). Toute écriture sur un équipement vide le cache du référentiel des
  générateurs (le wizard patient voit immédiatement le nouvel état).
- **RG-GMA-017** — **Avertissement de sécurité patient** (non bloquant) : affecter un patient à un générateur
  `EN_MAINTENANCE`, `EN_ATTENTE_PIECE`, `HORS_SERVICE` ou `REFORME` affiche un rappel dans
  le wizard patient et à l'affichage de la séance ; l'enregistrement reste possible.

## 9.3 Interventions

- **RG-GMA-020** — Types : préventive, curative, urgente, contrôle technique, installation, désinstallation,
  remplacement de pièce, révision complète. Priorités : normale (défaut), haute, urgente.
- **RG-GMA-021** — Création (statut `PLANIFIEE`) : équipement du centre, type, date de début, description obligatoires ;
  **état de l'équipement au moment de l'intervention** obligatoire parmi
  `EN_SERVICE`, `EN_MAINTENANCE`, `EN_ATTENTE_PIECE`, `HORS_SERVICE` ; l'échéance éventuelle ne précède pas la date de
  début ; symptôme facultatif ; intervenant facultatif.
- **RG-GMA-022** — Démarrage : uniquement depuis `PLANIFIEE` ; l'équipement prend alors l'état constaté à la création
  (avec historique), sauf s'il est déjà dans cet état. Un équipement réformé
  ne change plus d'état.
- **RG-GMA-023** — Clôture : uniquement depuis `EN_COURS` ; **actions réalisées** obligatoires ; **date et heure de
  fin** obligatoires et postérieures au début ; **état de l'équipement après**
  obligatoire parmi `EN_SERVICE`, `EN_ATTENTE_PIECE`, `HORS_SERVICE`, `A_REFORMER` ; cause de panne facultative.
  L'équipement prend l'état « après ».
- **RG-GMA-024** — Une intervention **terminée ne peut plus être annulée** ; annuler fixe le statut `ANNULEE` avec motif
  et auteur. Les états `REFORME` et `DESACTIF` ne peuvent jamais être fixés par
  une intervention.
- **RG-GMA-025** — **Rectification** (`GMAO_RECTIFICATION`) : seule une intervention terminée se rectifie ; motif
  obligatoire ; l'intervention est rouverte (`EN_COURS`), la clôture précédente est
  conservée dans la chronologie, la date de fin, les états et les lignes de coût automatiques sont réinitialisés
  (recalculés à la nouvelle clôture) ; une nouvelle date de début peut être donnée.
- **RG-GMA-026** — **Lignes de coût** : types pièce, main d'œuvre, intervenant, autre ; libellé obligatoire, quantité >
  0, prix unitaire valide ; une pièce peut référencer un article du stock. Ajout
  possible sauf sur une intervention annulée ; suppression uniquement tant que l'intervention est ouverte (planifiée ou
  en cours — une terminée se rectifie d'abord). Le coût d'une intervention est la
  somme de ses lignes.
- **RG-GMA-027** — **Valorisation automatique du temps de l'intervenant** à la clôture : durée (début → fin, en heures
  arrondies au centième) × tarif horaire par défaut de l'intervenant ; ligne
  « Honoraires intervenant » pour un prestataire externe, « Main d'œuvre » pour un interne ; sans effet si le tarif est
  absent ou nul, si la durée est nulle, ou si une ligne de même nature existe
  déjà (jamais de double comptage). Pour un intervenant **externe**, saisir manuellement de la main d'œuvre est refusé.
- **RG-GMA-028** — **Pièces jointes** d'une intervention : bon d'intervention, facture, photo ou autre ; PDF, PNG ou
  JPEG **reconnus à leur contenu**, 5 Mo maximum, fichier non vide, 50 documents au
  plus par intervention ; nom nettoyé (sans chemin, 150 caractères maximum).
- **RG-GMA-029** — **Tâches** d'une intervention : `A_FAIRE` → `EN_COURS` → `COMPLETEE` ; description obligatoire ; une
  tâche ne se démarre que « à faire » et ne se complète qu'« en cours ».
- **RG-GMA-030** — Une intervention est **en retard** (relance) si elle est planifiée avec une date de début passée, ou
  ouverte (planifiée ou en cours) avec une échéance dépassée.
- **RG-GMA-031** — **Indicateurs d'une intervention** : délai de prise en charge (création → début), durée (début → fin,
  ou → maintenant si en cours), indisponibilité de l'équipement pendant l'intervention
  et sa **part dans l'indisponibilité des 12 derniers mois** (plafonnée à 100 %, vide s'il n'y a eu aucune
  indisponibilité). Un bon d'intervention imprimable et une chronologie sont disponibles.

## 9.4 Intervenants

- **RG-GMA-040** — Un intervenant porte nom (obligatoire), type `INTERNE` ou `EXTERNE` (obligatoire), téléphone, email,
  tarif horaire par défaut (non négatif), état actif ; il se désactive (jamais
  supprimé). Référentiel propre au centre, distinct des fournisseurs du stock.

## 9.5 Plans de maintenance préventive

- **RG-GMA-050** — Un plan relie un équipement à une désignation, une fréquence et une prochaine date prévue (tous
  obligatoires) ; fréquences : hebdomadaire (7 j), bimensuelle (15 j), mensuelle,
  trimestrielle, semestrielle, annuelle. Statuts : `ACTIF`, `INACTIF`, `ARCHIVE`.
- **RG-GMA-051** — Exécuter un plan (uniquement `ACTIF`) enregistre la date et le nombre d'exécutions et **recalcule la
  prochaine date** à partir d'aujourd'hui (+1 semaine, +15 jours, +1/3/6 mois, +1 an).
  Désactiver : uniquement un plan actif ; réactiver : uniquement un plan inactif, avec une nouvelle date prévue.
- **RG-GMA-052** — Un plan **en retard** est un plan actif dont la prochaine date prévue est atteinte ou dépassée ; la
  liste des plans en retard et le compteur du tableau de bord servent de relance.

## 9.6 Indisponibilité, coût de possession et aide à la réforme

- **RG-GMA-060** — **Indisponibilité** d'un équipement sur une période = durée cumulée passée en `EN_MAINTENANCE`,
  `EN_ATTENTE_PIECE` ou `HORS_SERVICE`, calculée par rejeu de l'historique de statuts ;
  `DESACTIF` et `REFORME` (fin de vie) ne comptent pas.
- **RG-GMA-061** — **Fiche équipement** (12 derniers mois) : nombre d'interventions, indisponibilité en heures, coût de
  maintenance sur la période et cumulé depuis l'installation, dernière intervention,
  prochain plan de maintenance, et analyse de décision.
- **RG-GMA-062** — **Analyse de décision** : coût de possession = prix d'acquisition + maintenance cumulée ; **ratio de
  maintenance** = maintenance cumulée / prix d'acquisition (4 décimales ; vide si le prix
  est inconnu) ; coût par heure d'indisponibilité = coût de la période / heures d'indisponibilité (vide si aucune).
- **RG-GMA-063** — **Réforme recommandée** quand l'équipement est encore actif (ni réformé ni désactivé) et soit au
  statut `A_REFORMER`, soit son ratio de maintenance atteint ou dépasse le **seuil de
  60 %** (paramètre serveur `hemodialyse.gmao.reforme-seuil-ratio`, défaut 0,60, strictement positif). La recommandation
  est une aide : la réforme reste une décision humaine d'un détenteur de `GMAO_REFORME`.
- **RG-GMA-064** — Les indicateurs GMAO de la direction (coûts, indisponibilité, réformes recommandées, alertes
  critiques) sont décrits au chapitre direction (RG-DIR).
