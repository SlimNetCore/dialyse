# 11 — Dossier médical : antécédents, allergies, sérologies, examens, ordonnances, prescriptions, anémie, greffe, KDIGO, export

> Préfixe `RG-MED`. Le dossier médical **n'est jamais saisi en double** : les constantes viennent du cahier de dialyse,
> les évaluations KDIGO sont recalculées à la demande. Le système
> **compare** chaque résultat aux cibles KDIGO, **contrôle chaque jour** l'observance des traitements EPO/fer, **replace
en isolement** un patient devenu à risque et **alerte** le médecin.
> Sources : agrégats de `bc-medical` (`Allergie`, `Antecedent`, `Serologie`, `DemandeExamen`, `ObservationBiologique`,
> `Ordonnance`, `AdministrationTraitement`,
> `AlerteObservance`, `BilanPreGreffe`, `EtapeBilanPreGreffe`, `DonneurVivant`, `DecisionRcp`), `Kdigo*Policy`,
> `PrescriptionMedicaleDomainService`, `ResultatAnalyseDomainService`,
> `DossierMedicalPatientDomainService`, `AbordVasculaireDomainService`, `ObservancePrescriptionScheduler`,
> `ObservancePeriodMath`, `SuiviAnemieQueryService`,
> `KdigoGreffeQueryService`, `FhirExportService`, contrôleurs du dossier.

## 11.1 Principes d'accès

- **RG-MED-001** — **Écriture réservée au médecin** (rôle `MEDECIN`) pour : antécédents, allergies, sérologies, demandes
  d'examen, observations biologiques, ordonnances, prescriptions
  médicales, résultats d'analyse, abords vasculaires, dossier médical, bilan et donneurs de greffe (le volet médical de
  séance est ouvert à `MEDECIN` et `ADMIN`, RG-SEA-031). **Lecture** : `MEDECIN` et `ADMIN` (+ `INFIRMIER` pour dossier
  médical, abords vasculaires, prescription active, observance, administrations, étapes de greffe). L'administrateur lit
  mais n'écrit pas.
- **RG-MED-002** — Toute donnée est rattachée à un patient **et** à un centre ; un patient ou une pièce d'un autre
  centre est introuvable (RG-TRV-001/002).
- **RG-MED-003** — Toute consultation du dossier médical d'un patient est inscrite au journal d'audit (RG-SEC-050) ; les
  écritures aussi.
- **RG-MED-004** — Les listes cliniques sont paginées (taille par défaut 20, plafond 200) ; les périodes de recherche
  exigent « du ≤ au » (« La date from doit être <= à la date to »).
- **RG-MED-005** — Les codes cliniques sont des **concepts codés** : système (`CIM10` diagnostics, `LOINC` analytes,
  `ATC` médicaments, `LOCAL` dernier recours), code obligatoire (`CONCEPT_CODE_SYSTEM_REQUIRED`,
  `CONCEPT_CODE_REQUIRED`) et format valide (`CONCEPT_CODE_INVALID_CIM10`, `CONCEPT_CODE_INVALID_LOINC`). Une période
  clinique exige une date de début (`PERIODE_DEBUT_REQUIS`) et une fin non antérieure (`PERIODE_FIN_ANTERIEURE`).

## 11.2 Antécédents

- **RG-MED-010** — Types : médical, chirurgical, familial, obstétrical, comorbidité. Statuts : `ACTIF`, `RESOLU`,
  `INACTIF`. Patient, centre et type obligatoires (`ANTECEDENT_CHAMPS_REQUIS`) ; un code
  CIM-10 **ou** un libellé est obligatoire (`ANTECEDENT_DIAGNOSTIC_REQUIS`) ; la date de début n'est pas future
  (`ANTECEDENT_DATE_FUTURE`).
- **RG-MED-011** — Un même code CIM-10 ne peut pas être **actif deux fois** chez un patient ; un antécédent résolu ou
  inactif n'est pas en conflit (« Un antécédent actif avec ce diagnostic
  existe déjà pour ce patient »).
- **RG-MED-012** — « Résoudre » fixe la date de fin et le statut `RESOLU` ; un antécédent déjà résolu ne se résout pas
  deux fois (`ANTECEDENT_DEJA_RESOLU`).

## 11.3 Allergies

- **RG-MED-020** — Catégories : médicament, aliment, environnement, biologique ; criticité `BASSE` ou `HAUTE` ; type de
  réaction allergie ou intolérance ; vérification `SUSPECTEE` (défaut),
  `CONFIRMEE`, `REFUTEE`. Substance codée, catégorie, criticité et type obligatoires (`ALLERGIE_CHAMPS_REQUIS`) ; date
  de constatation par défaut aujourd'hui, jamais future (`ALLERGIE_DATE_FUTURE`).
- **RG-MED-021** — Une allergie de criticité **haute** doit décrire ses manifestations
  (`ALLERGIE_MANIFESTATIONS_REQUISES`) ; déclarer ou passer une allergie en criticité haute émet l'événement
  « allergie critique déclarée ». Une consultation dédiée liste les allergies critiques du patient.

## 11.4 Sérologies

- **RG-MED-030** — Marqueurs : VIH (Ac), AgHBs, AcHBs, AcHBc, AcVHC, ARN VHC, TPHA, CMV IgG/IgM, toxoplasmose IgG/IgM,
  EBV IgG/IgM ; résultats : positif, négatif, douteux, en cours. Patient, centre,
  marqueur, résultat et date de prélèvement obligatoires (`SEROLOGIE_CHAMPS_REQUIS`) ; date non future
  (`SEROLOGIE_DATE_FUTURE`).
- **RG-MED-031** — **Un résultat positif impose de documenter la conduite à tenir** (`SEROLOGIE_CONDUITE_REQUISE`), à la
  saisie comme à la correction.
- **RG-MED-032** — **Un seul résultat par patient, marqueur et date de prélèvement** (« Un résultat existe déjà pour ce
  marqueur à cette date de prélèvement »). Le résumé du patient présente le **dernier
  résultat de chaque marqueur**.
- **RG-MED-033** — **Conséquence automatique** : après enregistrement ou correction d'une sérologie, le patient est
  évalué « à risque infectieux » (dernier résultat positif pour AgHBs, anti-VHC,
  ARN VHC ou VIH, RG-PLN-012) et, si besoin, **replacé en salle d'isolement** ou signalé à l'administration
  (RG-PLN-053). Un positif émet l'événement « sérologie positive détectée ».

## 11.5 Demandes d'examen et observations biologiques

- **RG-MED-040** — Une demande d'examen a une catégorie (biologie, imagerie, fonctionnel, anapath), un caractère urgent
  éventuel, un motif et **au moins une ligne** (`DEMANDE_EXAMEN_LIGNE_REQUISE`) ;
  patient, centre et catégorie obligatoires (`DEMANDE_EXAMEN_CHAMPS_REQUIS`) ; date non future
  (`DEMANDE_EXAMEN_DATE_FUTURE`).
- **RG-MED-040b** — Chaque ligne d'une demande d'examen exige un code LOINC ou, à défaut, un libellé
  (`LIGNE_DEMANDE_EXAMEN_REQUISE`).
- **RG-MED-041** — **Machine à états** : `DEMANDE → PRELEVE → RESULTAT_DISPONIBLE → VALIDE` ; `ANNULE` possible
  **uniquement avant** que le résultat soit disponible ; `VALIDE` et `ANNULE` sont
  terminaux. Toute autre transition : `DEMANDE_EXAMEN_TRANSITION_INVALIDE`. La validation enregistre la conclusion du
  médecin.
- **RG-MED-042** — Une **observation biologique** est codée **LOINC** (`OBSERVATION_ANALYTE_NON_LOINC`), porte une
  valeur **numérique avec unité** (`VALEUR_MESUREE_REQUISE`,
  `VALEUR_MESUREE_UNITE_REQUISE`) **ou** textuelle, jamais les deux ni aucune (`OBSERVATION_VALEUR_INVALIDE`) ; patient,
  centre, analyte et date obligatoires (`OBSERVATION_CHAMPS_REQUIS`) ; date non
  future (`OBSERVATION_DATE_FUTURE`). Statuts préliminaire, final (défaut), corrigé ; sources saisie directe, import,
  dérivée d'un bilan.
- **RG-MED-043** — Corriger une observation passe son statut à « corrigé » ; une observation **dérivée automatiquement
  d'un bilan est immuable** (`OBSERVATION_DERIVEE_IMMUABLE`).
- **RG-MED-044** — Les **résultats d'analyse** (bilan biologique à colonnes fixes : Hb, Ht, plaquettes, ferritine, CST,
  EPO endogène, urée pré/post, créatinine, Kt/V mensuel, phosphore, calcium,
  PTH, albumine, protéines, CRP) portent une date de prélèvement (aujourd'hui par défaut) ; ils alimentent le suivi de
  l'anémie, l'évaluation KDIGO et les statistiques. La recherche est paginée.

## 11.6 Ordonnances

- **RG-MED-050** — Une ordonnance exige un patient et un centre (`ORDONNANCE_CHAMPS_REQUIS`) et au moins **une ligne**
  (`ORDONNANCE_LIGNE_REQUISE`) ; chaque ligne exige un code ATC ou un nom de médicament
  (`LIGNE_ORDONNANCE_MEDICAMENT_REQUIS`) et une posologie (`LIGNE_ORDONNANCE_POSOLOGIE_REQUISE`) ; durée et quantité
  éventuelles strictement positives (`LIGNE_ORDONNANCE_DUREE_INVALIDE`, `LIGNE_ORDONNANCE_QUANTITE_INVALIDE`).
- **RG-MED-051** — **Cycle** : `BROUILLON → SIGNEE → IMPRIMEE`, `ANNULEE` depuis tout statut non terminal
  (`ORDONNANCE_TRANSITION_INVALIDE` sinon). La **signature** attribue le numéro définitif (`ORDONNANCE_NUMERO_REQUIS`)
  et **verrouille le contenu** : corriger une ordonnance signée impose de l'annuler et d'en créer une nouvelle. La
  réimpression ne change pas le statut.

## 11.7 Prescription médicale de séance, abords vasculaires, dossier médical

- **RG-MED-060** — La **prescription médicale** fixe les cibles de séance (débit sang Qb, débit dialysat Qd, UF max,
  durée cible), le type de dialyseur et d'anticoagulant, le **poids sec cible**
  (si renseigné : **20 à 300 kg**, arrondi au centième), et les traitements **EPO** et **fer injectable** (article de
  stock, dose, voie, fréquence = valeur + unité heure/jour/semaine/mois/année). La
  prescription **la plus récente** fait foi ; le poids sec cible en vigueur un jour donné est celui de la dernière
  prescription datée au plus tard ce jour-là.
- **RG-MED-061** — Les articles EPO / fer des listes déroulantes sont ceux du stock marqués `EPO` ou `FER_INJECTABLE`
  (RG-STK-001).
- **RG-MED-062** — Un **abord vasculaire** (type, côté, localisation, dates, actif, complications) ne peut être retiré
  avant d'avoir été créé (`ABORD_DATE_FIN_ANTERIEURE`) ; actif par défaut ; la
  liste est paginée (taille par défaut 20, plafond 200).
- **RG-MED-063** — Le **dossier médical** du patient (néphropathie initiale, date de mise en dialyse, statuts hépatite
  B/C, observation globale, conclusion) est unique par patient (création puis mise
  à jour du même dossier).

## 11.8 Traitement de l'anémie : administrations, observance, alertes

- **RG-MED-070** — Une **administration** (EPO ou fer) porte patient, centre, type de traitement et date
  (`ADMINISTRATION_CHAMPS_REQUIS`) ; si **administrée**, la dose est obligatoire (`ADMINISTRATION_DOSE_REQUISE`, dose >
  0 `DOSE_ADMINISTREE_INVALIDE`, unité `DOSE_ADMINISTREE_UNITE_REQUISE`) ; si **non administrée**, un **motif** est
  obligatoire (`ADMINISTRATION_MOTIF_REQUIS`) et
  l'événement « écart prescription/administration » est émis. Saisie par `INFIRMIER` ou `MEDECIN`.
- **RG-MED-071** — Une administration rattachée à un article, à une séance et à une quantité **sort le stock** au FEFO,
  datée du jour réel de la saisie, via un bon de sortie numéroté (motif
  ADMINISTRATION) ; si le stock est insuffisant, l'administration **n'est pas créée** (pas de trace sans sortie réelle).
- **RG-MED-072** — **Périodes d'observance** : la prescription la plus récente découpe le temps en périodes successives
  ancrées sur sa date (fenêtre : heure/jour = 1 jour, semaine = 7, mois = 30,
  année = 365 jours). **Doses attendues** = fréquence prescrite ; **administrées** = administrations effectives de la
  période ; **restantes** = attendues − administrées (≥ 0). L'infirmier voit,
  en séance, ce qu'il reste à administrer et l'échéance (même calcul que l'alerte planifiée).
- **RG-MED-073** — **Contrôle quotidien à 06:30** (`ObservancePrescriptionScheduler.controlerObservance`), pour chaque
  patient ayant une prescription EPO/fer avec article et fréquence : (1) **retard
  constaté** — la période close compte moins d'administrations que prescrit : alerte `RETARD_CONSTATE`, acquittée
  **manuellement** par le médecin ; (2) **rappel d'échéance** — il reste des doses et il reste
  au plus ⌊fenêtre / 3⌋ jours (1 jour si fenêtre ≤ 2) : alerte `RAPPEL_ECHEANCE`, **résolue automatiquement** dès que le
  retard est rattrapé. Une alerte déjà ouverte du même type n'est pas dupliquée ;
  une notification temps réel (`OBSERVANCE_NON_RESPECTEE`) est adressée au rôle `MEDECIN`.
- **RG-MED-074** — Alerte inconnue : `ALERTE_OBSERVANCE_INTROUVABLE`. Consultation et acquittement : `MEDECIN`, `ADMIN`.

## 11.9 Suivi et cibles KDIGO (aide à la décision)

- **RG-MED-080** — Le **suivi de l'anémie** assemble, pour un patient : les 10 derniers bilans (courbe Hb, ferritine,
  CST, albumine), l' **évaluation du dernier bilan face aux cibles**, la
  prescription active et les 10 dernières administrations.
- **RG-MED-081** — **Cibles** (versionnées avec le code, aucune alerte persistée, tout recalculé) : hémoglobine 10 –
  11,5 g/dL (KDIGO 2012) ; ferritine ≥ 200 ng/mL ; CST ≥ 20 % ; Kt/V ≥ 1,2 ;
  phosphore 2,5 – 4,5 mg/dL, calcium 8,4 – 10,2 mg/dL, PTH 130 – 600 pg/mL (KDIGO 2017 CKD-MBD) ; albumine ≥ 4,0 g/dL.
  Statut : `SOUS_CIBLE` (< borne basse), `AU_DESSUS_CIBLE` (> borne haute),
  `DANS_CIBLE`, `NON_EVALUABLE` (valeur absente — une valeur absente n'est pas une valeur hors cible).
- **RG-MED-082** — **Aide à la décision greffe (KDIGO 2020)** : risque immunologique d'après le **PRA** (le pire de
  classe I et II) : faible < 20 %, intermédiaire 20 – 80 %, élevé > 80 %, non évaluable
  sinon ; DFG estimé par **CKD-EPI 2021** (sans coefficient ethnique) d'après la dernière créatinine, l'âge et le sexe,
  avec stade G1 (≥ 90) à G5 (< 15) — informatif chez un patient dialysé ; **alertes
  sérologiques** pour VIH, AgHBs, AcVHC, ARN VHC et TPHA positifs avec message de conduite. Ces évaluations sont des
  aides : **la décision d'éligibilité reste celle de la RCP et du médecin**.

## 11.10 Greffe rénale

- **RG-MED-090** — Un **bilan pré-greffe** par patient, ouvert au premier accès au statut `NON_DEBUTE`. **Machine à
  états** : `NON_DEBUTE → BILAN_EN_COURS | CONTRE_INDICATION_DEFINITIVE` ;
  `BILAN_EN_COURS → ELIGIBLE | CI_TEMPORAIRE | CI_DEFINITIVE` ;
  `ELIGIBLE → INSCRIT_LISTE_ATTENTE | CI_TEMPORAIRE | CI_DEFINITIVE` ; `INSCRIT_LISTE_ATTENTE → GREFFE_REALISEE | CI_TEMPORAIRE |
  CI_DEFINITIVE` ; `CI_TEMPORAIRE → BILAN_EN_COURS | CI_DEFINITIVE` ; `CI_DEFINITIVE` et `GREFFE_REALISEE` terminaux.
  **L'inscription sur liste d'attente n'est possible que depuis `ELIGIBLE`.**
  Violation : `BILAN_GREFFE_TRANSITION_INVALIDE` ; statut absent : `BILAN_GREFFE_STATUT_REQUIS` ; patient ou centre
  absent à l'ouverture : `BILAN_GREFFE_CHAMPS_REQUIS`.
- **RG-MED-091** — Dates automatiques : début de bilan (au passage en cours, si vide), inscription sur liste d'attente,
  greffe réalisée = date du jour. Le bilan immunologique (groupe sanguin confirmé,
  typage HLA, PRA classes I/II) et les notes (contre-indications, conclusion du néphrologue) se mettent à jour
  séparément.
- **RG-MED-092** — Une **décision de RCP** exige une date de réunion et un avis (`DECISION_RCP_CHAMPS_REQUIS`), date non
  future (`DECISION_RCP_DATE_FUTURE`) ; avis : favorable, défavorable, ajourné ;
  prochaine revue facultative ; une décision absente est refusée (`DECISION_RCP_REQUISE`) ; les décisions s'accumulent
  (historique).
- **RG-MED-093** — **Étapes du bilan** : catégorie, libellé obligatoires (`ETAPE_GREFFE_CHAMPS_REQUIS`) ; statuts
  `A_FAIRE`, `PLANIFIE`, `FAIT`, `NON_APPLICABLE` ; le statut est obligatoire (`ETAPE_GREFFE_STATUT_REQUIS`) ; une étape
  **faite** exige une date de
  réalisation (`ETAPE_GREFFE_DATE_REALISATION_REQUISE`) ; peut lier une demande d'examen ou une sérologie. La
  **checklist standard** (23 étapes : cardiologie, pneumologie, dentaire, ORL, gynécologie,
  urologie, digestif, oncologie, psychiatrie, virologie, immunologie, vaccination, nutrition) se génère à la demande ;
  le médecin ajoute ou retire des étapes ensuite. Saisie : `MEDECIN`, `INFIRMIER`
  (suppression : `MEDECIN`).
- **RG-MED-094** — **Donneur vivant candidat** : nom et lien de parenté (conjoint, parent, enfant, fratrie, autre
  famille, non apparenté) obligatoires (`DONNEUR_VIVANT_CHAMPS_REQUIS`) ; statut de
  bilan `CANDIDAT` (défaut), `BILAN_EN_COURS`, `COMPATIBLE`, `INCOMPATIBLE`, `EXCLU`, `RETENU` ; crossmatch non fait
  (défaut), négatif, positif ; décision finale datée.
- **RG-MED-095** — **Dossier de synthèse greffe** exportable en PDF (receveur + donneurs candidats) pour le centre de
  transplantation (`MEDECIN`).

## 11.11 Constantes, statistiques et export interopérable

- **RG-MED-100** — Les **constantes** (poids, tension, débit, UF, durée) sont une vue longitudinale **en lecture seule**
  du volet paramédical saisi en séance (source unique de vérité), paginée (`MEDECIN`, `ADMIN`).
- **RG-MED-101** — **Statistiques du patient** (`ADMIN`, `INFIRMIER`, `MEDECIN`, `SECRETAIRE`) : moyennes médicales (Hb,
  Kt/V, ferritine…) et paramédicales sur une période ; **UF réelle** = ultrafiltration
  saisie, sinon perte de poids per-dialytique × 1000 ; tension lue au format « sys/dia » (ou « sys-dia ») ; poids sec
  cible en vigueur le jour de la séance ; export CSV et PDF réservé à `ADMIN`.
- **RG-MED-102** — **Export FHIR R4** du dossier d'un patient (`MEDECIN`), en lecture seule, sous forme de `Bundle`
  JSON : `Patient`, `Condition` (antécédents), `AllergyIntolerance`, `Observation`
  (résultats d'analyse et observations LOINC), `MedicationRequest` (ordonnances), `Procedure` (abords vasculaires). Les
  demandes d'examen et les administrations d'anémie **ne sont pas encore
  exportées** (limite connue).
