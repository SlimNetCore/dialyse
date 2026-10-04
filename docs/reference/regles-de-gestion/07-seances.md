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
  créée directement à l'état `VALIDEE` (RG-SEA-011 reste exigée), sans passer par « Modifier → Enregistrer » ; la
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
  (strictement positive) annule la sortie existante et en recrée
  une au FEFO. Interdit si la séance est facturée. Chaque changement notifie le centre.
- **RG-SEA-025** — **Ajout d'un consommable à une séance déjà validée** (`ADMIN`, `INFIRMIER` ; `POST
  /seances/{id}/consommables`) : seule la ligne ajoutée sort du stock (FEFO, article actif, quantité strictement
  positive) sans retraiter les consommables existants ; autorisé pour une séance `VALIDEE` ou `SIGNEE`, refusé si
  facturée ou non validée. Chaque saisie notifie le médecin (RG-NOT-004).
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
  `systolique/diastolique` ; chaque valeur entre **20 et 400 mmHg**,
  systolique ≥ diastolique. Enregistrement notifié ; interdit si facturée.
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
