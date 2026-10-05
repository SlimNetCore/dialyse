# 07 — Séances de dialyse (cahier de dialyse)

> Préfixe `RG-SEA`. Sources : `Seance`, `SeanceStatus`, `SeanceDomainService`, `SeanceApplicationService`,
> `SeanceBillingEligibilityJdbcAdapter`, `VoletParamedicalDomainService`, `VoletMedicalDomainService`,
> `TensionArterielle`,
> `SeanceRestController`, `VoletMedicalRestController`, `VoletParamedicalRestController`, `BonSortieService`.

## 7.1 Cycle de vie

- **RG-SEA-001** — Statuts d'une séance : `CREE` → `VALIDEE` (validation infirmière) → `SIGNEE` (signature médecin) →
  `FACTUREE`
  (facturation). Aucun retour en arrière. Statut particulier `ABSENT` : séance exclue de la facturation depuis la
  simulation (RG-FAC-023) ; elle n'est plus validable ni facturable, ne compte pas comme réalisée et n'accepte pas de
  volet médical.
- **RG-SEA-002** — Une séance **facturée** ne peut plus être modifiée : date, forfait, volets paramédical et médical,
  consommables (« La seance facturee ne peut plus etre modifiee »). L'entrée de stock liée à une séance facturée est
  également immuable (`SEANCE_BILLED_STOCK_EXIT_IMMUTABLE`).
- **RG-SEA-003** — Une séance appartient à un patient et à un centre ; une séance inconnue du centre : « Seance
  introuvable ».

## 7.2 Création

- **RG-SEA-010** — Création : `ADMIN` ou `INFIRMIER`. Le patient doit exister dans le centre. La date par défaut est
  aujourd'hui.
- **RG-SEA-011** — **Le patient doit être facturable à la date de la séance** : une prise en charge au statut `VALIDEE`,
  avec un forfait (effectif sinon demandé), dont la période (effective sinon demandée) couvre la date, **et** une
  attestation de droits couvrant la date (« Le patient
  doit avoir une prise en charge valide pour être facturé »). Sans cela, la séance ne se crée pas.
- **RG-SEA-012** — Création par **scan de QR** (`ADMIN`, `INFIRMIER`, `SECRETAIRE`) : le code est résolu, dans cet
  ordre, comme identifiant patient (UUID,
  éventuellement après un préfixe `xxx:`), code patient, numéro d'assurance, puis par comparaison tolérante (sans
  espace, tiret ni ponctuation) sur code
  patient, numéro d'assurance du patient ou de l'assuré ; QR vide ou introuvable : refus explicite. Si le patient a déjà
  une séance ce jour-là, **elle est
  renvoyée** (pas de doublon). **Scan par un infirmier (`INFIRMIER`, `ADMIN`) : la séance est validée directement** —
  si une séance `CREE` existe ce jour-là (par exemple créée par la secrétaire) elle passe à `VALIDEE`, sinon elle est
  créée directement à l'état `VALIDEE` (RG-SEA-011 reste exigée ; le patient doit en outre être programmé ce jour-là,
  voir RG-SEA-048), sans passer par « Modifier → Enregistrer » ; la
  signature de l'infirmier est celle de l'utilisateur qui scanne. Un second scan d'une séance déjà `VALIDEE`, `SIGNEE`
  ou `FACTUREE` ne change rien (réponse `alreadyValidated`). Le scan de la **secrétaire** crée seulement la séance
  (`CREE`) : elle ne peut pas valider (RG-SEA-020). La réponse indique `created`, `validatedNow`, `alreadyValidated` et
  l'identité du patient.
- **RG-SEA-013** — La création notifie le centre en temps réel (séance créée) ; la réponse indique le générateur affecté
  au patient et son état (statut GMAO) pour
  avertir l'équipe d'un générateur indisponible.
- **RG-SEA-014** — Modification de la date d'une séance : `ADMIN` seul, interdite si facturée ; sans date, aujourd'hui.

## 7.3 Validation infirmière et consommables

- **RG-SEA-020** — La validation (`ADMIN`, `INFIRMIER`) fait passer la séance de `CREE` à `VALIDEE`, date la validation
  et enregistre la signature de l'infirmier. Une
  séance déjà validée ou signée n'est pas rejetée mais ne change pas de statut ; une séance facturée est refusée. Autre
  statut que `CREE` : « La seance n'est pas en statut CREE ».
- **RG-SEA-021** — À la validation, les **consommables** utilisés sont sortis du stock automatiquement : chaque ligne
  doit désigner un article existant du centre et **actif**
  (« Article inactif »), la quantité est prélevée sur les lots disponibles par **FEFO** (lot le plus proche de la
  péremption d'abord) ; si le stock disponible est insuffisant,
  la validation est refusée avec la quantité manquante (« Stock insuffisant pour l'article … »). Un **bon de sortie**
  valorisé au PMP est créé (motif SEANCE), de façon atomique avec
  le changement de statut.
- **RG-SEA-022** — Retirer un consommable d'une séance (`ADMIN`, `INFIRMIER`) restitue le stock ; modifier sa quantité
  (positive) met à jour la ligne du **bon de sortie unique de la séance** (RG-SEA-025), dont les lots sont
  resélectionnés
  au FEFO. Interdit si la séance est facturée. Chaque changement notifie le centre.
- **RG-SEA-025** — **Une séance = un seul bon de sortie « SEANCE »** : à la validation (RG-SEA-021) ou, pour une
  séance déjà validée, dès le **premier article ajouté** (`ADMIN`, `INFIRMIER` ; `POST /seances/{id}/consommables`),
  le bon est créé avec son numéro de pièce ; chaque ajout, modification de quantité ou retrait **met à jour ce même
  bon** (lignes, lots et mouvements recalculés par FEFO, stock rendu restitué) au lieu de créer une sortie par article.
  Article actif exigé pour une quantité qui augmente, quantité d'ajout strictement positive ; autorisé pour une séance
  `VALIDEE` ou `SIGNEE`, refusé si facturée (« La seance facturee ne peut plus etre modifiee ») ou non validée.
  Les administrations EPO/fer gardent leur propre bon numéroté (poste « ADMINISTRATION »). Chaque saisie notifie le
  médecin (RG-NOT-004).
- **RG-SEA-026** — **Poste infirmier** (écran « Séances » ; le médecin seul n'y a pas accès, il passe par son tableau de
  bord) : un seul écran pour scanner, choisir le patient dans la **file du jour** (séances du
  jour avec leur statut : à valider, validée, signée, facturée, absent) et saisir la séance en quatre étapes —
  constantes (avec le poids sec prescrit), consommables, anémie, remarques. Les constantes s'enregistrent à la sortie de
  chaque champ et les remarques après une courte pause de frappe, sans bouton « Enregistrer » ; une séance facturée est
  en lecture seule. Les consommables les plus sortis du jour dans le centre sont proposés en un toucher (+1 unité,
  RG-SEA-025). Une alerte non bloquante signale un générateur affecté dont l'état n'est pas « en service ». Les
  statistiques et le tableau des séances sont dans l' **historique** (RG-SEA-029), accessible depuis le poste. La
  secrétaire voit la file et scanne mais n'ouvre pas la saisie clinique. L'écran est responsive : une
  colonne (file puis séance) sur mobile, deux colonnes sur tablette, toutes les sections visibles sur PC.
  L'habillage suit la charte de l'application (thème actif, titres Fraunces, cartes vitrées, indicateurs du jour).
  Sur PC, un panneau de contexte rappelle les dernières séances du patient (RG-SEA-028). L'en-tête de la séance ouverte
  propose d'afficher et d'imprimer le **badge du patient avec son QR code** (le même qu'à la liste des patients).
  *Source :*
  `SeanceStationComponent`, `QrScannerComponent`.
- **RG-SEA-046** — **Validation oubliée** : une séance restée `CREE` après son jour n'est pas réalisée (RG-ABS-041) :
  la détection de la nuit compte le patient absent (« à qualifier ») et la perte est valorisée. La régularisation se
  fait **en deux temps** : (1) l' **administrateur** consulte, dans « À régulariser » du poste infirmier, les séances
  `CREE` des **7 derniers jours** (les plus anciennes d'abord, jusqu'à 50) et **déverrouille pour régularisation**
  (`POST /seances/{id}/deverrouiller-regularisation`, `ADMIN` seul) celles dont il est sûr qu'il s'agit d'un oubli ;
  seule une séance d'un **jour passé** restée `CREE` peut l'être (`SEANCE_DEVERROUILLAGE_INVALIDE`), l'action est
  idempotente et notifie les infirmiers (`SEANCE_DEVERROUILLEE`) ; (2) la séance déverrouillée devient visible de
  l' **infirmier** dans son bloc « À régulariser » (il ne voit que celles-là, jamais les autres) et il peut la valider.
  L'infirmier ne valide librement que les séances **du jour** : valider une séance d'un jour passé non déverrouillée est
  refusé par le serveur (`SEANCE_REGULARISATION_NON_DEVERROUILLEE`) ; l'administrateur valide toujours. La secrétaire
  ne voit pas le bloc. Un **rappel** est envoyé chaque jour à **07:00** (`SeanceRegularisationScheduler`, après la
  détection des absences de 02:30) à l'administrateur de chaque centre qui a de telles séances **pas encore
  déverrouillées** : nombre et date de la plus ancienne (notification `SEANCES_A_REGULARISER`, `ADMIN` seul ; rien
  n'est envoyé s'il n'y en a aucune). Une fois validée, la séance compte comme réalisée et
  l'absence détectée à tort est annulée à la réconciliation suivante (RG-ABS-040, commentaire « Séance réalisée à
  cette date »). Au-delà de 7 jours, la séance reste consultable dans l'historique (RG-SEA-029).
- **RG-SEA-047** — **Navigation entre patients du poste infirmier** : après un scan réussi, le poste se positionne
  automatiquement sur la séance du patient scanné (étape « constantes »). Depuis une séance ouverte de la file du jour,
  les boutons « Patient précédent » / « Patient suivant » ouvrent le voisin dans l'ordre de la file sans repasser par la
  liste (désactivés aux extrémités, rang « n / total » affiché) ; ils sont masqués pour une séance hors file (ex. séance
  à régulariser) ou lorsqu'il n'y a qu'un patient. La saisie en cours est enregistrée avant le changement. *Source :*
  `SeanceStationComponent`, `PatientPagerComponent`.
- **RG-SEA-048** — **Séance hors planning** : le scan qui doit **créer** la séance du jour (aucune séance n'existe
  encore ce jour-là ; une séance existante est simplement renvoyée) vérifie que le patient est **attendu** à la date,
  avec les mêmes critères que la détection des absences (RG-ABS) : la date est un de ses **jours de dialyse**, le centre
  est **ouvert** (ni jour férié ni fermeture exceptionnelle), le patient n'est ni en sommeil, ni admis plus tard, ni
  sorti (transféré, guéri : attendu jusqu'au jour de sortie inclus ; décédé, greffé : jusqu'à la veille ; occasionnel ou
  vacancier : jusqu'à la fin du séjour incluse). Sinon, **rien n'est créé** et le serveur répond 422 avec le motif :
  `SEANCE_HORS_PLANNING_JOUR` (pas un jour de dialyse), `SEANCE_HORS_PLANNING_PATIENT` (patient non attendu) ou
  `SEANCE_CENTRE_FERME` (centre fermé). L' **infirmier** (et l'administrateur) peut alors **confirmer** en renvoyant le
  scan
  avec un motif — `RATTRAPAGE`, `URGENCE` ou `AUTRE` (précision libre obligatoire :
  `SEANCE_DEROGATION_PRECISION_REQUISE` ; motif absent ou inconnu : `SEANCE_DEROGATION_MOTIF_REQUIS`) : la séance est
  créée
  et validée, marquée **« hors planning »** avec son motif (poste infirmier, historique des séances). Un **jour de
  fermeture** n'est franchi que par l'**administrateur** (la confirmation d'un infirmier reste refusée par
  `SEANCE_CENTRE_FERME`). La **secrétaire** ne peut jamais confirmer : son scan hors planning est refusé avec les mêmes
  codes et l'écran lui demande de prévenir l'infirmier. La création manuelle d'une séance (`POST /seances`) n'est pas
  concernée. *Source :* `SeanceDomainService.verifierProgramme`, `ProgrammationSeance`, `SeancePlanningJdbcAdapter`,
  `HorsPlanningConfirmComponent`.
- **RG-SEA-029** — **Historique des séances** (`GET /seances`, tous profils de centre ; écran « Historique des
  séances ») : statistiques du mois (séances prévues, présences, absences, total, répartitions par sexe et par âge,
  détail et export) et **liste paginée** des séances du centre. La recherche, les filtres et le tri sont appliqués **par
  le serveur** sur toute la liste, jamais sur la seule page affichée : période (`from`, `to`, bornes incluses,
  début ≤ fin), statut (un ou plusieurs), texte libre sur le nom, le prénom ou le code du patient (tous les mots
  doivent correspondre), tri sur la date, le patient, le code, le statut ou la création (liste blanche ; toute autre
  colonne retombe sur la date décroissante, avec un départage stable). Réponse `{items, total, page, size}` ; `size`
  est borné à 500 et la liste est toujours limitée au centre demandé. L'écran est en consultation seule : la saisie se
  fait au poste infirmier (RG-SEA-026). *Point d'attention :* l'ancien écran permettait à l'`ADMIN` de modifier la
  **date** et le **forfait** d'une séance (RG-SEA-014 et suivantes) ; ces actions n'ont plus d'écran depuis la refonte
  (les points d'accès subsistent). *Source :* `SeanceDomainService.search`, `SeanceRepositoryAdapter.search`,
  `SeancesHistoriqueComponent`.
- **RG-SEA-027** — **Raccourcis de consommables du centre** : l'`ADMIN` choisit (au plus 12, sans doublon, articles
  actifs du centre, ordre de sélection conservé) les articles proposés en un toucher à l'infirmier
  (`PUT /seances/raccourcis-consommables`) ; la lecture est ouverte à `ADMIN`, `INFIRMIER` et `SECRETAIRE`. Sans
  raccourci configuré, l'écran propose les articles les plus sortis du jour (6 au plus). La liste est propre au centre
  (isolation stricte), mise en cache par centre et purgée à chaque modification. *Source :*
  `SeanceRaccourciDomainService`, `SeanceRaccourciApplicationService`.
- **RG-SEA-028** — **Rappel des dernières séances** (`GET /seances/patient/{id}/recentes`, `ADMIN`, `INFIRMIER`,
  `MEDECIN`) : les 3 dernières séances du patient antérieures à la séance ouverte (10 au plus, de la plus récente à
  la plus ancienne) avec leurs constantes — poids avant/après, perte de poids, tension, durée, ultrafiltration.
  Limité au centre de la session. *Source :* `SeanceDomainService.recentByPatient`.
- **RG-SEA-023** — La date d'une sortie de stock rattachée à une séance est immuable
  (`SEANCE_STOCK_EXIT_DATE_IMMUTABLE`).
- **Point d'attention (RG-SEA-024)** — Valider à nouveau une séance déjà validée avec une liste de consommables retraite
  ces consommables (nouvelle sortie) : l'idempotence ne
  porte que sur le statut. L'interface n'expose pas ce cas ; à protéger côté serveur. L'ajout d'un consommable
  après validation passe par RG-SEA-025.

## 7.4 Volets de la séance

- **RG-SEA-030** — **Volet paramédical** (`ADMIN`, `INFIRMIER`, `SECRETAIRE`) : poids avant/après, tension avant/après,
  durée, débit sang, ultrafiltration, anticoagulant, type de
  dialysat, incidents. Les poids saisis respectent RG-TRV-032 (> 0, ≤ 500 kg). Une tension est normalisée au format
  `systolique/diastolique` en **mmHg** ; chaque valeur entre **20 et 400 mmHg**,
  systolique ≥ diastolique. La saisie en **centimètres de mercure** est acceptée et convertie : dès que la systolique
  saisie est ≤ 30, les deux valeurs sont multipliées par 10 (« 12/8 » → `120/80`, « 11,5/7 » → `115/70`) ; la virgule
  ou le point décimal sont tolérés. Enregistrement notifié ; interdit si facturée.
- **RG-SEA-031** — **Volet médical** (`ADMIN`, `MEDECIN`) : prescription, tolérance, examen clinique, résultats
  biologiques, ajustements thérapeutiques, conclusion. Il n'est
  accessible **qu'après validation infirmière** (« n'est accessible qu'apres validation infirmiere ») ; interdit si
  facturée.
- **RG-SEA-032** — **Signature médecin** (`ADMIN`, `MEDECIN`) : uniquement après validation infirmière, une seule fois
  (« deja signee par un medecin ») ; statut `SIGNEE`.
- **RG-SEA-033** — Forfait de la séance : par défaut celui de la prise en charge ; l'administrateur (`ADMIN` seul —
  l'infirmier ne le modifie pas) peut le **surcharger** par un forfait du
  centre actif (« Forfait introuvable pour le centre actif » sinon), avec enregistrement de l'auteur et de
  l'horodatage ; interdit si facturée.

## 7.5 Consultation, journal, tableau de bord et calendrier

- **RG-SEA-040** — La liste des séances est paginée et peut être filtrée par mois ; accès `ADMIN`, `INFIRMIER`,
  `MEDECIN`, `SECRETAIRE`.
- **RG-SEA-041** — Le **journal du jour** rassemble les patients dialysés à une date, avec leur statut, et le total des
  articles sortis ce jour-là par code article.
- **RG-SEA-042** — **Tableau de bord mensuel** : séances prévues = somme, sur les jours ouvrés **échus** du mois (hors
  fériés et fermetures exceptionnelles ; le futur n'est compté ni en présence ni en absence), des patients programmés ce
  jour de la semaine, hors patients « en sommeil », à partir de leur date d'admission et jusqu'à la libération de leur
  place (transfert, décès, greffe, guérison, fin de séjour : RG-PAT-032) ; **présences = séances réalisées** (statuts
  `VALIDEE`, `SIGNEE`, `FACTUREE`) du mois ; **absences = absences du suivi des absences (RG-ABS) du mois, hors
  annulées**, qu'elles soient déclarées ou détectées par le contrôle quotidien (plus de calcul « prévues − présences »,
  supprimé) ; **effectif du mois** = patients pris en charge sur la période (admis au plus tard le dernier jour du mois,
  place non libérée avant le 1er du mois, hors « en sommeil ») auxquels s'ajoutent ceux qui ont une séance réalisée
  dans le mois, qu'ils aient déjà dialysé ou non ; répartition par sexe (M/F/autre) et par tranche d'âge (0-17, 18-39,
  40-59, 60+, inconnu) de cet effectif (`effectifPatients`) ; détail par jour de la semaine ; exports PDF, XLSX et CSV.
- **RG-SEA-043** — Le détail du tableau de bord se demande pour `presence` (séances réalisées du mois) ou `absence`
  (absences du suivi du mois, hors annulées, avec leur statut) ; toute autre valeur est refusée
  (« kind must be 'presence' or 'absence' »).
- **RG-SEA-044** — **Calendrier du centre** : jours fériés (libellé) et jours de fermeture exceptionnelle (motif) par
  mois ; ajout et suppression réservés à `ADMIN`, bornés au centre.
  Ces jours bloquent les séances prévues, les absences automatiques et le planning de la semaine. **Point
  d'attention :** l'ajout utilise une instruction `MERGE … KEY` propre à H2 ; elle est à remplacer par une requête
  compatible PostgreSQL (AGENTS.md §10) avant la
  mise en production sur ce moteur, et l'unicité d'un jour férié n'est pas contrôlée.

## 7.6 Impression du cahier de dialyse

- **RG-SEA-045** — **Cahier de dialyse imprimable** (modèle de document `CAHIER_DIALYSE`, `GET
  /api/v1/cahier-dialyse/{patientId}/imprimer`, profils `ADMIN`, `SECRETAIRE`, `MEDECIN`, `INFIRMIER`, patient du centre
  courant uniquement) : **une page par séance validée, signée ou facturée** (jamais une séance seulement créée), de la
  plus récente à la plus ancienne comme à l'écran, éventuellement limitée à une période (`from`, `to` incluses). Chaque
  page reprend **toutes les informations du cahier** : date, statut et forfait de la séance (forfait modifié sur la
  séance, sinon forfait de la prise en charge couvrant la date, sinon dernier forfait connu) ; saisie infirmier (poids
  avant et après, TA avant et après, durée, débit sang, ultrafiltration, type de dialysat, anticoagulant, incidents) ;
  consommables sortis du stock avec quantité, unité, valorisation unitaire (PMP) et totale, et valorisation totale de la
  séance ; saisie médecin (néphropathie initiale, date de mise en dialyse, statuts des hépatites B et C, observation
  globale, conclusion du médecin, prescription en vigueur **à la date de la séance** — poids sec, EPO, fer — et volet
  médical de la séance : prescription, tolérance, examen clinique, résultats biologiques, ajustements, conclusion). Une
  information absente est imprimée « - ». Chaque page porte l'en-tête (logo, société, centre, identité du patient) et
  le pied de page (édition, pagination, centre, pied de page société) du centre. Refus : patient hors du centre
  (`PATIENT_INTROUVABLE`), aucune séance à imprimer (`CAHIER_VIDE`), début après la fin (`CAHIER_PERIODE_INVALIDE`).
