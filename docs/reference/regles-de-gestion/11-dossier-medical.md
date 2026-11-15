# 11 — Dossier médical : antécédents, allergies, sérologies, examens, ordonnances, prescriptions, anémie, greffe, KDIGO, export

> Préfixe `RG-MED`. Le dossier médical **n'est jamais saisi en double** : les constantes viennent du cahier de dialyse,
> les évaluations KDIGO sont recalculées à la demande. Le système
> **compare** chaque résultat aux cibles KDIGO, **contrôle chaque jour** l'observance des traitements EPO/fer, **replace
en isolement** un patient devenu à risque et **alerte** le médecin.
> Sources : agrégats de `bc-medical` (`Allergie`, `Antecedent`, `Serologie`, `DemandeExamen`, `ObservationBiologique`,
> `Ordonnance`, `AdministrationTraitement`,
> `AlerteObservance`, `BilanPreGreffe`, `EtapeBilanPreGreffe`, `DonneurVivant`, `DecisionRcp`), `Kdigo*Policy`,
> `PrescrireUnTraitement` (DM2), `Analyse` et `ObservationBiologique` (DM6, contexte `dossier-medical`),
> `EnregistrerLeDossierMedicalDeBase` et `ExporterLeDossierMedical` (DM8), `ControleDeNuitDeLObservance`,
> `PeriodeDObservance`, `ConsulterLeSuiviDeLAnemie`,
> `KdigoGreffeQueryService`, contrôleurs du dossier.

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
- **RG-MED-035** — *Antécédents, reprise sur place DM5 (contexte `dossier-medical`, `AntecedentsControleur`, ADR 0084 ;
  décisions déléguées, à confirmer avec les métiers et le DPO)* :
  (a) **droits** : MEDECIN et ADMIN lisent, le MEDECIN seul écrit ; l'INFIRMIER n'a aucun accès (reproduit, à confirmer) ;
  DIRECTION et SUPERADMIN 403 `ROLE_SCOPE` ; garde de centre sur les 5 routes ;
  (b) **patient** : un patient inconnu ou d'un autre centre est refusé en 404 `PATIENT_INTROUVABLE` à l'enregistrement
  (avant : une fiche orpheline était créée) ; les lectures d'un tel patient restent une page vide en 200 (reproduit) ;
  (c) **entrées illisibles** : corps, type, système de codage, statut, date, identifiant, `centerId`, `page`/`size`
  absents ou illisibles : 400 stable `BAD_REQUEST` sans nom de classe interne (avant : 500), et 403 avant 400 pour un profil
  sans droit ; code sans système : 400 ;
  (d) **bornes de colonne** : sévérité 50 caractères, libellé libre et affichage du code 255, code 20 : 400 stable
  (avant : 500) ;
  (e) **diagnostic** : tous les systèmes CIM10, LOINC, ATC et LOCAL sont acceptés avec un simple contrôle de forme (CIM-10
  et LOINC), code mis en majuscules (reproduit) ; l'unicité du diagnostic actif est protégée par un verrou de transaction
  pendant l'enregistrement et la correction, sans contrainte en base ; une écriture qui n'obtient pas le verrou en 5 s
  répond 409 `ANTECEDENT_ECRITURE_CONCURRENTE` ; jamais d'unicité pour un libellé libre (reproduit) ;
  (f) **correction** : elle remplace diagnostic, libellé, dates, sévérité et note (absents = effacés), ignore le type,
  conserve le statut s'il est absent, ne contrôle pas le futur et peut poser `RESOLU` sans fin ou rouvrir un antécédent
  (reproduit, à confirmer) ; antécédent introuvable : 400 `BAD_REQUEST` (reproduit) ;
  (g) **résolution** : la date peut être absente ou future (reproduit) ;
  (h) **suppression** : un antécédent inexistant répond toujours 204 (reproduit) mais n'est plus inscrit au journal
  d'audit ; aucun événement « antécédent ajouté / résolu » n'est publié (reproduit, rien inventé) ; les lectures ne sont
  pas tracées (à décider avec le DPO) ;
  (i) **jour** : la limite « pas dans le futur » lit le jour UTC de la JVM (reproduit, à confirmer : jour du centre).

## 11.3 Allergies

- **RG-MED-020** — Catégories : médicament, aliment, environnement, biologique ; criticité `BASSE` ou `HAUTE` ; type de
  réaction allergie ou intolérance ; vérification `SUSPECTEE` (défaut),
  `CONFIRMEE`, `REFUTEE`. Substance codée, catégorie, criticité et type obligatoires (`ALLERGIE_CHAMPS_REQUIS`) ; date
  de constatation par défaut aujourd'hui, jamais future (`ALLERGIE_DATE_FUTURE`).
- **RG-MED-021** — Une allergie de criticité **haute** doit décrire ses manifestations
  (`ALLERGIE_MANIFESTATIONS_REQUISES`) ; déclarer ou passer une allergie en criticité haute émet l'événement
  « allergie critique déclarée ». Une consultation dédiée liste les allergies critiques du patient. (Reprise DM5 :
  l'événement n'est plus levé, le legacy ne le publiait jamais, voir RG-MED-036 g.)
- **RG-MED-036** — *Allergies, reprise sur place DM5 (contexte `dossier-medical`, `AllergiesControleur`, ADR 0084 ;
  décisions déléguées, à confirmer avec les métiers et le DPO)* :
  (a) **droits** : MEDECIN et ADMIN lisent (liste et `critiques`), le MEDECIN seul écrit ; l'INFIRMIER est refusé partout,
  y compris sur `critiques` (reproduit, à confirmer : bandeau d'allergie pour l'infirmier ?) ; garde de centre ;
  (b) **patient** : inconnu ou d'un autre centre : 404 `PATIENT_INTROUVABLE` à la déclaration (avant : fiche orpheline) ;
  lectures : page ou liste vide en 200 (reproduit) ;
  (c) **entrées illisibles** : corps, système de codage, catégorie, criticité, type de réaction, statut, date, identifiant
  absents ou illisibles : 400 stable sans nom de classe interne (avant : 500), 403 avant 400 pour un profil sans droit ;
  code absent : 422 `CONCEPT_CODE_REQUIRED` (reproduit) ;
  (d) **bornes de colonne** : code de la substance 20 caractères, affichage 255 : 400 (avant : 500) ;
  (e) **déclaration** : aucune unicité, la même substance peut être déclarée plusieurs fois (reproduit, à décider) ;
  (f) **correction** : seuls la criticité (obligatoire), les manifestations (remplacées, même absentes : une criticité basse
  les efface) et le statut (conservé s'il est absent) se corrigent ; substance, catégorie, type et date sont immuables ;
  allergie introuvable : 400 (reproduit) ; écriture concurrente : 409 `ALLERGIE_ECRITURE_CONCURRENTE` ;
  (g) **bandeau « critiques »** : toute criticité haute, y compris une allergie réfutée (reproduit, à confirmer) ; aucun
  événement « allergie déclarée » ni « allergie critique déclarée » n'est publié (reproduit, rien inventé) ;
  (h) **suppression** : une allergie inexistante répond toujours 204 (reproduit) sans ligne d'audit ; lectures non tracées ;
  codes FHIR d'allergie inchangés (DM8).

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
  (RG-PLN-053), dans la **même transaction** que la sérologie (un patient inconnu du centre ou un verrou non obtenu annule
  aussi la sérologie). Aucun événement « sérologie positive détectée » n'est publié : le legacy le levait sans jamais le
  diffuser et la reprise n'en invente pas (RG-MED-034 e).
- **RG-MED-034** — *Sérologies, reprise sur place DM4 (contexte `dossier-medical`, `SerologiesControleur`, ADR 0083 ;
  décisions déléguées, à confirmer avec les métiers et le DPO)* :
  (a) **droits** : MEDECIN et ADMIN lisent (liste, synthèse), le MEDECIN seul écrit (enregistrer, corriger, supprimer) ;
  l'ADMIN ne peut pas écrire ; l'INFIRMIER n'a aucun accès au statut sérologique (reproduit, à confirmer) ; DIRECTION et
  SUPERADMIN 403 `ROLE_SCOPE` ; garde de centre sur les 5 routes ;
  (b) **entrées illisibles** : corps absent ou mal formé, marqueur ou résultat absent ou inconnu, `titre` non numérique,
  date illisible ou impossible, identifiant, `centerId`, `page`/`size` illisibles : 400 stable `BAD_REQUEST` sans nom de
  classe interne (avant : 500, ou le nom d'une classe interne dans le détail), et 403 avant 400 pour un profil sans
  droit ;
  (c) **bornes de colonne** : unité au plus 20 caractères, laboratoire 255, titre arrondi à 3 décimales et d'au plus
  7 chiffres entiers : 400 stable (avant : 500) ; reproduit : titre négatif, prochain contrôle antérieur au prélèvement,
  date très ancienne et conduite sans limite acceptés ;
  (d) **règle de positivité** : seul POSITIF impose une conduite à tenir non blanche (`SEROLOGIE_CONDUITE_REQUISE`) ;
  DOUTEUX et EN_COURS passent sans conduite (reproduit, à confirmer) ; la règle de risque infectieux de la Planification
  (derniers résultats de AgHBs, anti-VHC, ARN VHC, VIH) et celle de la Direction restent séparées (non unifiées ici) ;
  (e) **événement** : `SerologiePositiveDetectee` n'est pas publié (reproduit, rien inventé) ;
  (f) **correction** : seuls le résultat et la conduite à tenir se corrigent ; la conduite du corps remplace l'ancienne,
  même absente (reproduit, à confirmer) ; sérologie introuvable (inconnue, d'un autre patient ou d'un autre centre) : 400
  `BAD_REQUEST` (reproduit) ;
  (g) **suppression** : une sérologie inexistante répond toujours 204 (reproduit) mais n'est plus inscrite au journal
  d'audit `SEROLOGIE_SUPPRIMEE` ; la suppression ne réévalue pas la place du patient (reproduit) ;
  (h) **lectures** : patient inconnu ou d'un autre centre = page ou liste vide en 200 (reproduit) ; les lectures ne sont
  pas tracées dans le journal d'audit et l'audit des écritures désigne le patient (reproduit, à décider avec le DPO) ;
  (i) **concurrence** : l'unicité (patient, marqueur, date) est protégée par un verrou consultatif de transaction posé
  pendant l'enregistrement, sans contrainte en base (aucune migration) ; une écriture qui n'obtient pas le verrou de la
  sérologie ou du couple en 5 s répond 409 `SEROLOGIE_ECRITURE_CONCURRENTE` ; ordre global des verrous : centre,
  fiches, `app_user`, verrous consultatifs ; les doublons déjà en base sont à relever (`verifications-production.md` §48) ;
  (j) **jour** : la date de prélèvement par défaut et la limite « pas dans le futur » lisent le jour UTC de la JVM, comme
  les demandes d'examen (reproduit, à confirmer : jour du centre).

## 11.5 Demandes d'examen et observations biologiques

- **RG-MED-040** — Une demande d'examen a une catégorie (biologie, imagerie, fonctionnel, anapath), un caractère urgent
  éventuel, un motif et **au moins une ligne** (`DEMANDE_EXAMEN_LIGNE_REQUISE`) ;
  patient, centre et catégorie obligatoires (`DEMANDE_EXAMEN_CHAMPS_REQUIS`) ; date non future
  (`DEMANDE_EXAMEN_DATE_FUTURE`).
- **RG-MED-040b** — Chaque ligne d'une demande d'examen exige un code d'examen (nomenclature LOINC, CIM10, ATC ou LOCAL) ou,
  à défaut, un libellé (`LIGNE_DEMANDE_EXAMEN_REQUISE`).
- **RG-MED-041** — **Machine à états** : `DEMANDE → PRELEVE → RESULTAT_DISPONIBLE → VALIDE` ; `ANNULE` possible
  **uniquement avant** que le résultat soit disponible ; `VALIDE` et `ANNULE` sont
  terminaux. Toute autre transition : `DEMANDE_EXAMEN_TRANSITION_INVALIDE`. La validation enregistre la conclusion du
  médecin.
- **RG-MED-045** — *Demandes d'examen, reprise sur place DM3 (contexte `dossier-medical`, `DemandesDExamenControleur`,
  ADR 0082 ; décisions déléguées, à confirmer avec les métiers et le DPO)* :
  (a) **droits** : le MEDECIN écrit (demander, prélever, résultat disponible, valider, annuler), MEDECIN et ADMIN
  lisent ; l'ADMIN ne peut pas écrire (reproduit, à confirmer) ; tout autre profil 403 ; garde de centre sur les
  6 routes ;
  (b) **patient** : demander un examen pour un patient inconnu ou d'un autre centre est refusé en 404
  `PATIENT_INTROUVABLE` (avant : demande orpheline) ; la liste d'un patient inconnu reste une page vide en 200 ;
  (c) **introuvable** : une demande inconnue, d'un autre patient ou d'un autre centre reste un 400 `BAD_REQUEST` (contrat
  conservé, à confirmer) ; une demande dont le statut, la catégorie ou une ligne (nomenclature inconnue, code mal formé)
  est illisible en base est ignorée par la liste (elle ne la casse plus ; avertissement dans le journal avec son seul
  identifiant ; sa transition reste 400 « introuvable ») ; le total de la page compte les demandes aux statut et
  catégorie lisibles, il peut donc dépasser le nombre d'éléments listés (limite consignée) ;
  (d) **entrées illisibles** : corps absent ou mal formé, date illisible, identifiant, `centerId`, `page`/`size`,
  catégorie ou `urgent` absents, nomenclature inconnue ou absente, texte plus long que sa colonne : 400 stable (avant :
  500), et 403 avant 400 pour un profil sans droit ;
  (e) **codes d'examen** : la nomenclature n'est pas limitée à LOINC (CIM10, ATC, LOCAL acceptés), le code est mis en
  majuscules, seuls CIM10 et LOINC ont un format (422 `CONCEPT_CODE_INVALID_*`) ; un code blanc est ignoré ; conclusion
  non obligatoire ni contrôlée, prescripteur libre, aucune date minimale, aucune idempotence (reproduit, à confirmer) ;
  (f) **statut** : « résultat disponible » est une action manuelle du médecin ; la saisie d'une observation liée à la
  demande ne change jamais le statut et aucun événement n'est publié (reproduit, à confirmer) ;
  (g) **concurrence** : deux écritures sur la même demande se suivent (verrou de la demande, délai de 5 s sur
  PostgreSQL, au-delà 409 `DEMANDE_EXAMEN_ECRITURE_CONCURRENTE`) ; une transition ne réécrit plus les lignes ;
  lignes lues dans un ordre déterministe (libellé, code, commentaire, identifiant) ;
  (h) **jour** : date par défaut et limite « pas dans le futur » = jour de la JVM (UTC), reproduit ; audit sous
  `PATIENTS_*`, lectures non tracées (reproduit, à confirmer DPO).
- **RG-MED-042** — Une **observation biologique** est codée **LOINC** (`OBSERVATION_ANALYTE_NON_LOINC`), porte une
  valeur **numérique avec unité** (`VALEUR_MESUREE_REQUISE`,
  `VALEUR_MESUREE_UNITE_REQUISE`) **ou** textuelle, jamais les deux ni aucune (`OBSERVATION_VALEUR_INVALIDE`) ; patient,
  centre, analyte et date obligatoires (`OBSERVATION_CHAMPS_REQUIS`) ; date non
  future (`OBSERVATION_DATE_FUTURE`). Statuts préliminaire, final (défaut), corrigé ; sources saisie directe, import,
  dérivée d'un bilan.
- **RG-MED-043** — Corriger une observation passe son statut à « corrigé » ; une observation **dérivée automatiquement
  d'un bilan est immuable** (`OBSERVATION_DERIVEE_IMMUABLE`).
- **RG-MED-038** — *Observations biologiques, reprise sur place DM6 (contexte `dossier-medical`,
  `ObservationsBiologiquesControleur`, ADR 0085 ; décisions déléguées, à confirmer avec les métiers et le DPO)* :
  (a) **droits** : MEDECIN et ADMIN lisent, le MEDECIN seul écrit (reproduit) ; garde de centre sur les 5 routes ;
  (b) **patient** : enregistrer pour un patient inconnu ou d'un autre centre est refusé en 404 `PATIENT_INTROUVABLE`
  (avant : observation orpheline) ; la liste d'un patient inconnu reste une page vide en 200 ;
  (c) **entrées illisibles et bornes** : corps absent ou mal formé, date, identifiant, pagination, statut ou système de
  codage inconnu ou absent, valeur au-delà de 10 chiffres entiers et 4 décimales, unité au-delà de 20 caractères, texte
  au-delà de 500, libellé du code au-delà de 255 : 400 stable sans nom de classe interne (avant : 500), et 403 avant 400
  pour un profil sans droit ;
  (d) **correction** : seule la valeur change (code, date, statut et demande du corps sont ignorés) ; une observation
  inconnue, d'un autre patient ou d'un autre centre reste un 400 `BAD_REQUEST` « Observation introuvable » (contrat
  conservé, à confirmer) ; deux écritures sur la même observation se suivent (délai de 5 s sur PostgreSQL, au-delà 409
  `OBSERVATION_ECRITURE_CONCURRENTE`) ;
  (e) **suppression** : toujours 204, même sans effet (reproduit), mais une suppression qui n'a rien supprimé n'est plus
  inscrite au journal d'audit ; une observation dérivée d'un bilan reste supprimable (reproduit) ;
  (f) **lecture par demande d'examen** : la demande doit appartenir au centre ET au patient de l'URL, sinon la réponse est
  une liste vide en 200 (avant : les observations de n'importe quel patient de la demande, KB-MED-44) ;
  (g) **reproduit, à confirmer** : aucune règle de plausibilité de valeur, deux envois identiques créent deux
  observations, la référence de demande n'est pas contrôlée à l'écriture et la saisie ne change jamais le statut de la
  demande, aucun événement, jour de la JVM (UTC) pour la limite « pas dans le futur », liste triée du prélèvement le plus
  récent au plus ancien, filtre de code par égalité stricte, page négative = 0, taille nulle ou négative = 20, plafond
  200, lectures non tracées ; une ligne dont le statut, la source ou le système est illisible en base, ou dont la valeur
  numérique n'a pas d'unité, est ignorée par les lectures (avertissement dans le journal avec son seul identifiant).
- **RG-MED-044** — Les **résultats d'analyse** (bilan biologique à colonnes fixes : Hb, Ht, plaquettes, ferritine, CST,
  EPO endogène, urée pré/post, créatinine, Kt/V mensuel, phosphore, calcium,
  PTH, albumine, protéines, CRP) portent une date de prélèvement (aujourd'hui par défaut) ; ils alimentent le suivi de
  l'anémie, l'évaluation KDIGO et les statistiques. La recherche est paginée.
- **RG-MED-039** — *Analyses, reprise sur place DM6 (contexte `dossier-medical`, `AnalysesControleur`, ADR 0085 ;
  décisions déléguées, à confirmer avec les métiers et le DPO)* :
  (a) **droits** : MEDECIN et ADMIN lisent et recherchent, le MEDECIN seul écrit (reproduit) ; garde de centre ;
  (b) **patient** : enregistrer pour un patient inconnu ou d'un autre centre est refusé en 404 `PATIENT_INTROUVABLE` ;
  (c) **modifier n'est plus créer** : une analyse inconnue, d'un autre patient ou d'un autre centre est refusée en 404
  `ANALYSE_INTROUVABLE` ; elle n'est jamais créée par la modification ni réaffectée à un autre patient ou centre
  (avant : trou de sécurité) ; la modification remplace le bilan en entier (une mesure absente est effacée, la date
  absente redevient le jour) et réinitialise la date de création (reproduit, à confirmer) ;
  (d) **entrées illisibles et bornes** : corps, date, identifiant, pagination, plaquettes hors entier ou en texte, mesure
  au-delà de 36 chiffres entiers et 2 décimales : 400 stable (avant : 500), 403 avant 400 ; la recherche (`POST
  /analyses/search`) sans page ni taille : 400 « La page et la taille sont obligatoires », page négative, taille hors de
  1 à 200, patient ou centre absent : 400 `VALIDATION_ERROR` « Invalid request content. » (reproduit) ;
  (e) **suppression** : toujours 204, même sans effet (reproduit), sans ligne d'audit quand rien n'est supprimé ;
  écriture concurrente : 409 `ANALYSE_ECRITURE_CONCURRENTE` ;
  (f) **reproduit, à confirmer** : aucune règle de valeur (négative, nulle, démesurée, date dans le futur acceptées),
  plusieurs bilans le même jour, aucun événement, aucune interprétation normal / anormal (elle vit chez les lecteurs :
  suivi de l'anémie, KDIGO, statistiques, export CSV et FHIR, inchangés) ; la recherche est une lecture que le journal
  d'audit inscrit comme une création ; les observations (RG-MED-038) et les analyses sont deux modèles qui coexistent
  sans projection de l'un vers l'autre.

## 11.6 Ordonnances

- **RG-MED-050** — Une ordonnance exige un patient et un centre (`ORDONNANCE_CHAMPS_REQUIS`) et au moins **une ligne**
  (`ORDONNANCE_LIGNE_REQUISE`) ; chaque ligne exige un code ATC ou un nom de médicament
  (`LIGNE_ORDONNANCE_MEDICAMENT_REQUIS`) et une posologie (`LIGNE_ORDONNANCE_POSOLOGIE_REQUISE`) ; durée et quantité
  éventuelles strictement positives (`LIGNE_ORDONNANCE_DUREE_INVALIDE`, `LIGNE_ORDONNANCE_QUANTITE_INVALIDE`).
- **RG-MED-051** — **Cycle** : `BROUILLON → SIGNEE → IMPRIMEE`, `ANNULEE` depuis tout statut non terminal
  (`ORDONNANCE_TRANSITION_INVALIDE` sinon). La **signature** attribue le numéro définitif (`ORDONNANCE_NUMERO_REQUIS`)
  et **verrouille le contenu** : corriger une ordonnance signée impose de l'annuler et d'en créer une nouvelle. La
  réimpression ne change pas le statut.
- **RG-MED-052** — Le numéro `ORD-nnnnn` est une séquence par centre, attribuée **dans l'ordre des signatures**. Un refus de
  signature (ordonnance annulée, déjà signée…) **consomme un numéro** : la séquence peut avoir des trous (reproduit du legacy,
  ADR 0081, KB-MED-20, à confirmer avec les métiers). Une ordonnance introuvable n'en consomme pas. La date de prescription par
  défaut est le **jour du centre** (fuseau Africa/Algiers), pas celui du serveur. Le compteur (`SEQ_ORD`) appartient à la
  Comptabilité : le Dossier médical le réserve par sa numérotation des documents (S-DDL2, ADR 0185), comportement inchangé.
- **RG-MED-053** — Une ordonnance ou une prescription ne se rédige que pour un patient **du centre de la session** : patient
  inconnu ou d'un autre centre = refus 404 `PATIENT_INTROUVABLE` (ADR 0081, KB-MED-19). Une saisie illisible (corps, date,
  identifiant, code sans nomenclature, ligne vide, texte trop long) est refusée en 400 `BAD_REQUEST`. Les lignes d'ordonnance sont
  rendues à la lecture dans un ordre déterministe (libellé, posologie, identifiant) ; la réponse à la création les rend dans
  l'ordre saisi.
- **RG-MED-054** — **Modifier une prescription** exige qu'elle existe pour CE patient dans CE centre (404 `PRESCRIPTION_INTROUVABLE`
  sinon) : elle n'est ni créée sous un identifiant inconnu ni réaffectée à un autre centre ou patient (ADR 0081, KB-MED-17). La
  modification remplace tout le contenu. La suppression d'une prescription sans correspondance répond 204 sans rien supprimer.
- **RG-MED-055** — **Imprimer une ordonnance** (`POST /documents/print`, type `ORDONNANCE`) exige le profil MEDECIN ou ADMIN et le
  centre de la session (403 sinon, avant tout accès aux données ; ADR 0081, KB-MED-18, à confirmer avec les métiers). **DOC5d (ADR 0196,
  2026-11-15)** : le gabarit n'embarque plus de requête ; le Dossier médical compose les lignes de l'état (une par ligne d'ordonnance : état
  civil du patient du centre, nom du centre, en-tête et médicament, le libellé du code affiché primant sur le nom saisi), sans contrôle de
  statut, dans l'ordre où la base rend les lignes ; rien n'est imprimé (page blanche) si l'ordonnance est inconnue, d'un autre centre ou sans
  ligne ; un modèle déjà stocké avec l'ancienne requête s'imprime sans changement, un nouveau téléversement qui la porte est refusé
  `QUERY_MODIFIED` (KB-DOC5D-1, RG-DOC-004) ; identifiant illisible sur la voie par modèle : échec 500 inchangé (KB-DOC5D-2).

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
- **RG-MED-037** — *Abords vasculaires, reprise sur place DM5 (contexte `dossier-medical`, `AbordsVasculairesControleur`,
  ADR 0084 ; décisions déléguées, à confirmer avec les métiers et le DPO)* :
  (a) **droits** : MEDECIN, ADMIN et INFIRMIER lisent (c'est l'infirmier qui ponctionne l'abord), le MEDECIN seul écrit
  (reproduit) ; garde de centre ;
  (b) **patient** : inconnu ou d'un autre centre : 404 `PATIENT_INTROUVABLE` à l'enregistrement (avant : ligne orpheline) ;
  (c) **le PUT n'est plus un « enregistrer »** : un identifiant inconnu, ou l'abord d'un autre patient ou d'un autre centre,
  est refusé en 404 `ABORD_VASCULAIRE_INTROUVABLE` ; l'abord n'est jamais créé par un PUT ni réaffecté à un autre patient
  ou centre (avant : trou de sécurité) ;
  (d) **remplacement** : tous les champs sont remplacés (absents = effacés), `actif` absent = actif (réactive un abord
  terminé), la date d'enregistrement (rendue comme `updatedAt`) est réécrite (reproduit, à confirmer) ;
  (e) **type libre** : le type est un texte libre obligatoire (vide accepté) ; la Planification n'exige la compétence
  `CATHETER` que pour `KT_AIGU` et `KT_TUNNELISE` en majuscules, abord actif et non terminé (RG-PLN-097, inchangée) ; aucune
  cohérence entre « actif » et la date de fin, aucune limite de date (reproduit, à confirmer : liste fermée partagée) ;
  (f) **entrées illisibles et bornes** : corps, `actif`, dates, identifiants, pagination illisibles, type absent, type
  au-delà de 20 caractères, côté 10, localisation 255, complications 500 : 400 stable (avant : 500), 403 avant 400 ;
  (g) **suppression** : un abord inexistant répond toujours 204 (reproduit) sans ligne d'audit ; écriture concurrente : 409
  `ABORD_VASCULAIRE_ECRITURE_CONCURRENTE` ; liste triée de la création la plus récente, les abords sans date en dernier.
- **RG-MED-063** — Le **dossier médical** du patient (néphropathie initiale, date de mise en dialyse, statuts hépatite
  B/C, observation globale, conclusion) est unique par patient (création puis mise
  à jour du même dossier).

## 11.8 Traitement de l'anémie : administrations, observance, alertes

- **RG-MED-070** — Une **administration** (EPO ou fer) porte patient, centre, type de traitement et date
  (`ADMINISTRATION_CHAMPS_REQUIS`) ; si **administrée**, la dose est obligatoire (`ADMINISTRATION_DOSE_REQUISE`, dose >
  0 `DOSE_ADMINISTREE_INVALIDE`, unité `DOSE_ADMINISTREE_UNITE_REQUISE`) ; si **non administrée**, un **motif** est
  obligatoire (`ADMINISTRATION_MOTIF_REQUIS`) et
  l'événement « écart prescription/administration » est émis. Saisie par `INFIRMIER` ou `MEDECIN`.
- **RG-MED-071** — Une administration rattachée à un article, à une séance et à une dose **sort le stock** au FEFO,
  datée du jour réel de la saisie, via un bon de sortie numéroté (motif
  ADMINISTRATION) ; si le stock est insuffisant, l'administration **n'est pas créée** (pas de trace sans sortie réelle).
  La **quantité sortie est déduite par le serveur** de la dose (UI, mg) et de la fiche article (dose ÷ dosage par
  unité, RG-STK-006) : elle n'est plus saisie à la main et toute quantité envoyée par le client est ignorée. Une unité
  de dose incompatible avec l'article refuse l'administration (`ARTICLE_UNITE_DOSE_INCOMPATIBLE`,
  `ARTICLE_DOSAGE_NON_DEFINI`) ; l'écran de l'infirmier affiche la sortie de stock calculée avant l'enregistrement.
- **RG-MED-075** — À l'enregistrement d'une **prescription**, la dose d'EPO (UI) et la dose de fer (mg) doivent pouvoir
  se convertir dans l'unité de stock de l'article choisi (RG-STK-006) : sinon la prescription est refusée avec les mêmes
  codes (`ARTICLE_UNITE_DOSE_INCOMPATIBLE`, `ARTICLE_DOSAGE_NON_DEFINI`). Le formulaire de prescription affiche la
  sortie
  de stock correspondant à la dose saisie. Ainsi unité prescrite et unité de sortie restent conformes. *Source :*
  `PrescriptionsControleur` (contexte `dossier-medical`, DM2).
- **RG-MED-072** — **Périodes d'observance** : la prescription la plus récente découpe le temps en périodes successives
  ancrées sur sa date (fenêtre : heure/jour = 1 jour, semaine = 7, mois = 30,
  année = 365 jours). Quand la prescription porte une **dose** (UI pour l'EPO, mg pour le fer), le calcul est en
  **quantités** : **attendu** = dose × fréquence ; **administré** = somme des doses effectivement administrées dans la
  période, dans la même unité ; **restant** = attendu − administré (≥ 0) ; **doses restantes** = restant ÷ dose, arrondi
  au supérieur. Une prescription relevée en cours de période (ex. 4000 → 8000 UI après une administration de
  4000 UI) laisse donc 4000 UI à administrer, soit un **complément** : l'infirmier peut le saisir en séance, la dose
  proposée étant exactement le reste. Sans dose prescrite, repli sur le **nombre d'administrations** (attendues =
  fréquence ; administrées = administrations effectives). L'infirmier voit, en séance, ce qu'il reste à administrer et
  l'échéance (même calcul que l'alerte planifiée). Quand le médecin crée, modifie ou supprime une prescription, le
  poste de l'infirmier qui a ce patient à l'écran se met à jour **en temps réel**, sans rechargement (évènement
  `PRESCRIPTION_CHANGED` limité au centre et au patient, absent de la cloche). Il en va de même, pour un médecin qui a
  ouvert le même dossier, de la liste des prescriptions et de l'onglet Anémie (périodes d'observance et alertes).
- **RG-MED-073** — **Contrôle quotidien à 06:30** (`ControleDeNuitDeLObservance.controlerObservance`, cas d'usage `ControlerLObservance`, contexte `dossier-medical` DM2), pour chaque
  patient ayant une prescription EPO/fer avec article et fréquence : (1) **retard
  constaté** — la période close compte moins (en quantité, ou en nombre d'administrations sans dose) que prescrit :
  alerte `RETARD_CONSTATE`, qui **explique** le constat (traitement, période, prescription « dose × fréquence »,
  attendu,
  administré, manque, conduite à tenir ; les champs sont conservés avec l'alerte ; les alertes antérieures gardent leur
  texte d'origine), acquittée
  **manuellement** par le médecin ; (2) **rappel d'échéance** — il reste des doses et il reste
  au plus ⌊fenêtre / 3⌋ jours (1 jour si fenêtre ≤ 2) : alerte `RAPPEL_ECHEANCE`, **résolue automatiquement** dès que le
  retard est rattrapé. Une alerte déjà ouverte du même type n'est pas dupliquée ;
  une notification durable (`OBSERVANCE_NON_RESPECTEE`, RG-NOT-005) est adressée au rôle `MEDECIN`, avec un lien vers
  l'onglet Anémie du patient.
- **RG-MED-074** — Alerte inconnue : `ALERTE_OBSERVANCE_INTROUVABLE`. Consultation et acquittement : `MEDECIN`, `ADMIN`.

## 11.9 Suivi et cibles KDIGO (aide à la décision)

- **RG-MED-080** — Le **suivi de l'anémie** assemble, pour un patient : les 10 derniers bilans (courbe Hb, ferritine,
  CST, albumine), l' **évaluation du dernier bilan face aux cibles**, la
  prescription active et les 10 dernières administrations (les 10 **plus récentes**, de la plus récente à la plus ancienne : le
  legacy rendait les 10 premières lues sans tri, corrigé par l'ADR 0081, KB-MED-26). Une administration saisie pour un patient
  inconnu ou d'un autre centre est refusée en 404 (KB-MED-19).
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
- **RG-MED-046** — *Bilan pré-greffe, reprise sur place DM7 (contexte `dossier-medical`, `BilanPreGreffeControleur`, ADR 0086 ;
  décisions déléguées, à confirmer avec les métiers et le DPO)* :
  (a) **droits** : MEDECIN et ADMIN lisent, le MEDECIN seul écrit (reproduit) ; garde de centre ;
  (b) **patient** : un patient inconnu ou d'un autre centre est refusé en 404 `PATIENT_INTROUVABLE` sur toutes les routes du
  bilan, et la lecture n'ouvre alors AUCUN bilan (avant : la lecture écrivait une ligne orpheline) ; pour un patient valide,
  la première lecture ouvre un bilan `NON_DEBUTE` (la lecture écrit, reproduit, à confirmer) ;
  (c) **un seul bilan par patient** : l'ouverture est sérialisée par un verrou consultatif (aucun changement de schéma) ; un
  doublon déjà présent en base est lu sans erreur (le plus ancien l'emporte, avertissement avec son seul identifiant) ;
  (d) **concurrence** : les écritures du bilan se suivent (délai de 5 s sur PostgreSQL, au-delà 409
  `BILAN_PRE_GREFFE_ECRITURE_CONCURRENTE`) ; les décisions de RCP ne sont plus perdues ; un statut illisible en base répond
  422 `BILAN_PRE_GREFFE_ILLISIBLE` ;
  (e) **entrées illisibles et bornes** : corps, statut, avis, date, identifiant absents ou inconnus, groupe sanguin
  au-delà de 5 caractères, typage au-delà de 500, PRA au-delà de 3 chiffres entiers et 2 décimales : 400 stable sans nom de
  classe interne (avant : 500) ; 403 avant 400 pour un profil sans droit ;
  (f) **reproduit, à confirmer** : machine à états (RG-MED-090), dates d'inscription et de greffe réécrites à chaque appel du
  même statut, jour de la JVM (UTC), PUT immunologique et notes qui remplacent tout (champ absent effacé), aucune règle de
  valeur sur le PRA (négatif, supérieur à 100 acceptés), décisions de RCP en doublon et revue antérieure acceptées, audit
  pauvre `PATIENTS_*`, lectures non tracées, aucun événement ni notification.
- **RG-MED-047** — *Étapes du bilan, reprise sur place DM7 (`EtapesDeBilanControleur`, ADR 0086)* :
  (a) **droits** : lecture MEDECIN, ADMIN et INFIRMIER ; création, génération et mise à jour MEDECIN et INFIRMIER ;
  suppression MEDECIN seul (reproduit) ;
  (b) **patient** : 404 `PATIENT_INTROUVABLE` à la création et à la génération ; **une étape inconnue, d'un autre patient ou
  d'un autre centre est refusée en 404 `ETAPE_BILAN_INTROUVABLE`** (avant : 400) ;
  (c) **suppression** : toujours 204, même sans effet (reproduit), sans ligne d'audit quand rien n'est supprimé ;
  (d) **génération** : deux générations simultanées ne doublent plus les étapes (verrou consultatif du patient) ; une ligne
  existante, lisible ou non, n'est jamais recréée ; une écriture qui n'obtient pas le verrou répond 409
  `ETAPE_BILAN_ECRITURE_CONCURRENTE` ;
  (e) **lecture robuste** : une ligne dont la catégorie ou le statut est illisible en base est ignorée par les lectures
  (avertissement avec son seul identifiant) au lieu de faire échouer toute la liste (avant : 400) ; elle reste supprimable ;
  (f) **entrées et bornes** : catégorie, statut, corps, identifiants illisibles ou absents, libellé au-delà de 255
  caractères : 400 stable (avant : 500) ; 403 avant 400 ;
  (g) **reproduit, à confirmer** : la mise à jour remplace tous les champs, une date de réalisation est acceptée pour tout
  statut, les références d'examen et de sérologie ne sont pas contrôlées, deux étapes identiques sont permises.
- **RG-MED-048** — *Donneurs vivants, reprise sur place DM7 (`DonneursVivantsControleur`, ADR 0086)* :
  (a) **droits** : MEDECIN et ADMIN lisent, MEDECIN seul écrit (reproduit) ;
  (b) **patient** : 404 `PATIENT_INTROUVABLE` à l'enregistrement (avant : donneur orphelin ou croisé entre centres) ; **un
  donneur inconnu, d'un autre patient ou d'un autre centre est refusé en 404 `DONNEUR_VIVANT_INTROUVABLE`** (avant : 400) ;
  (c) **suppression** : 204 même sans effet, sans ligne d'audit quand rien n'est supprimé ;
  (d) **concurrence** : 409 `DONNEUR_VIVANT_ECRITURE_CONCURRENTE` après le délai de 5 s ;
  (e) **entrées et bornes** : lien de parenté, statut de bilan ou crossmatch absents ou inconnus (y compris le crossmatch
  absent à la mise à jour, avant : 500), nom et prénom au-delà de 100 caractères, téléphone de 30, groupe sanguin de 5,
  typage de 500 : 400 stable ; 403 avant 400 ; une ligne illisible en base est ignorée par la liste, reste supprimable et ne
  peut plus être mise à jour (404) ;
  (f) **reproduit, à confirmer** : la mise à jour remplace tout, aucune règle croisée (statut retenu avec crossmatch positif,
  naissance dans le futur, crossmatch positif sans date acceptés).
- **RG-MED-049** — *Évaluation KDIGO et dossier de synthèse PDF, garde DM7 (`EvaluationKdigoControleur` depuis DM9, auparavant
  `KdigoGreffeRestController` ; `GreffeExportRestController`, ADR 0086)* : un patient inconnu ou d'un autre centre est refusé en 404 `PATIENT_INTROUVABLE`
  (avant : 200 avec une évaluation « non évaluable » ou un PDF sans en-tête) ; aucune lecture n'ouvre de bilan. **Reproduit
  et signalé comme risque clinique prioritaire (KB-MED-94, à confirmer par les médecins et le DPO)** : pour un même
  marqueur sérologique, le résultat le plus ANCIEN l'emporte dans l'évaluation KDIGO ; le calcul de la fonction rénale, du
  risque immunologique et le contenu du PDF sont inchangés ; le PDF reste réservé au MEDECIN.
- **RG-MED-049b** — *Dossier médical de base, reprise sur place DM8 (contexte `dossier-medical`,
  `DossierMedicalDeBaseControleur`, ADR 0087 ; complète RG-MED-063)* :
  (a) **droits** : lecture MEDECIN, ADMIN, INFIRMIER ; écriture (POST et PUT, mêmes effets : remplacement complet) MEDECIN
  seul (reproduit) ; garde de centre inchangée ; 403 avant 400 ;
  (b) **patient** : un patient inconnu ou d'un autre centre est refusé en 404 `PATIENT_INTROUVABLE` à l'écriture (avant :
  dossier orphelin ou écrit pour un patient d'un autre centre) ; la lecture d'un patient sans dossier reste un 404 sans corps ;
  (c) **un seul dossier par patient** (unicité globale de la base) : un conflit sur l'unicité ou un verrou non obtenu en 5 s
  répond 409 `DOSSIER_MEDICAL_ECRITURE_CONCURRENTE` (avant : 500) ; un doublon déjà en base est lu sans erreur (le plus ancien) ;
  (d) **bornes** : néphropathie initiale au-delà de 255 caractères, statut d'hépatite B ou C au-delà de 20 : 400
  `VALIDATION_ERROR` ; date illisible ou inexistante, corps absent ou illisible : 400 `BAD_REQUEST` (avant : 500) ;
  (e) **reproduit, à confirmer** : les statuts d'hépatite restent libres (aucune énumération) ; audit inégal (seule la lecture
  du dossier est tracée, KB-MED-100).
- **RG-MED-049c** — *Export FHIR du dossier, reprise sur place DM8 (`ExportFhirControleur`, ADR 0087 ; complète RG-MED-102)* :
  (a) **patient** : inconnu ou d'un autre centre : 404 `PATIENT_INTROUVABLE` ; droit MEDECIN seul ; aucune trace d'audit
  (reproduit, KB-MED-100, à confirmer avec le DPO) ;
  (b) **contenu inchangé** : mêmes ressources, mêmes codes, même ordre (Patient, Condition, AllergyIntolerance, Observation,
  MedicationRequest, Procedure) ; abords vasculaires triés de la création la plus récente, ceux sans date en dernier, à
  égalité par horodatage de création puis identifiant (ordre déterministe, prouvé sur PostgreSQL 16) ;
  (c) **RISQUE CLINIQUE reproduit (KB-MED-101, à confirmer par les médecins et le DPO)** : seules les 500 observations et les
  500 ordonnances les plus récentes sont exportées, sans aucune indication de troncature ;
  (d) **ligne illisible, RISQUE CLINIQUE à trancher par les médecins (KB-MED-102)** : une allergie, une observation, un
  antécédent ou une ordonnance dont la lecture échoue est ignorée EN SILENCE pour le destinataire (journalisée côté serveur
  avec son seul identifiant) au lieu de rendre tout l'export impossible (avant : 400 ou 422 pour tout l'export) ; un antécédent au code
  CIM-10 mal formé est exporté tel quel (le module ne valide pas le code à la lecture).
- **RG-MED-049d** — *Statistiques médicales du patient et leur export, garde DM8 (`stats/medical`, `stats/export`, y compris
  les POST de recherche, ADR 0087 ; complète RG-MED-101)* : calculs, clés de tendance en MAJUSCULES, moyennes à 0.0, formats
  CSV et PDF et droits **inchangés** (reproduit) ; date illisible, paramètre `format` absent ou corps de recherche illisible :
  400 (avant : 500) ; 403 avant 400 ; les POST de recherche étaient journalisés `PATIENTS_CREATION` (KB-MED-100) : **corrigé par PL4**,
  ils portent l'action de lecture `STATISTIQUES_PATIENT_CONSULTATION` (RG-MED-049i, KB-PL4-4).
- **RG-MED-049e** — *Indicateurs Direction, période et totaux (`/api/v1/direction/indicators`, ADR 0087)* : `from` ou `to`
  illisible : 400 (avant : 500) ; 403 avant 400. **KB-MED-104 CORRIGÉ le 2026-11-09 (lot L1, RG-MED-049g, validation DPO attendue au plus
  tard le 2026-12-15)** : les effectifs inférieurs à 5 sont masqués par centre ; avant, les totaux additionnaient les valeurs non masquées et
  la différence entre le total et les centres visibles révélait l'effectif masqué ; désormais un consolidé est supprimé dès qu'un centre est
  masqué. Seuil, formes, règles de comptage des sérologies et droits inchangés.
- **RG-MED-049f** — *Reprise sur place DM9 du calcul médical restant et retrait du module legacy `bc-medical` (ADR 0101 ; décisions
  déléguées, à confirmer par les médecins, les métiers et le DPO au plus tard le 2026-12-15)* :
  (a) **le contexte `dossier-medical` possède tout le calcul, la plateforme ne garde que la présentation** : statistiques médicales du
  patient (`StatistiquesMedicalesPubliees` : moyennes, tendances, lignes d'export, poids sec en vigueur ; JSON, CSV et PDF toujours
  rendus par la plateforme, mêmes URL, mêmes rôles), indicateurs cliniques de la Direction (`IndicateursCliniquesPublies`, déjà masqués
  par `EffectifMasque`, seuil 5), administrations de l'anémie du Pilotage (`TraitementDeLAnemiePublie`), dossier de synthèse greffe
  (`DossierDeGreffePublie`, PDF rendu par la plateforme) ; **plus aucune requête SQL de la plateforme ni d'un autre contexte sur les
  dix-sept tables du Dossier médical** (garde `TableDuDossierMedicalTest`, liste blanche nominative et datée) ;
  (b) **évaluation KDIGO du candidat** (`EvaluationKdigoControleur`, contexte `dossier-medical`, mêmes URL, champs et droits) : cibles
  et seuils de RG-MED-081 et 082 repris à l'identique dans `CiblesKdigo` et `EvaluationKdigoDuCandidat` (valeurs **non validées
  médicalement**, à confirmer) ; la garde du patient (404) est dans le cas d'usage, pour l'évaluation comme pour le PDF ; le résultat
  sérologique le plus ancien l'emporte toujours (KB-MED-94, reproduit) ;
  (c) **risque infectieux et cathéters lus par la Planification** (`RisquesInfectieuxPublies`, `AbordsVasculairesPublies`, fournis à la
  Planification par la plateforme : `RisquesInfectieuxFournis`, `AbordsVasculairesFournis`) : même règle (RG-PLN-012, RG-PLN-097) ;
  **KB-MED-106 CORRIGÉ le 2026-11-09 (lot L2, RG-MED-049h, RISQUE CLINIQUE, validation médicale attendue ≤ 2026-12-15)** : avant, un
  résultat DOUTEUX ou EN_COURS plus récent qu'un positif levait le risque infectieux sans résultat négatif ; désormais seule la dernière
  sérologie conclusive (positive ou négative) décide ; reproduits : à égalité totale le positif l'emporte
  (KB-MED-107), lecture par le centre de la sérologie ou de l'abord et non de la fiche (KB-MED-108) ;
  (d) **reproduits, à confirmer** : KB-MED-104 (CORRIGÉ depuis le 2026-11-09, voir RG-MED-049g), KB-MED-100 (audit inégal, trois `POST stats/*/search` sans appelant
  frontend conservés ; libellé d'action CORRIGÉ par PL4, RG-MED-049i), KB-MED-109 (ordre non défini à date égale dans les tendances, le CSV et le PDF ; double comptage des taux de la
  Direction) ;
  (e) **effacement des administrations d'une séance supprimée** fourni à la Séance par le Dossier médical
  (`Administrations.effacerCellesDeLaSeance`, même unité de travail que la suppression, RG-SEA) ;
  (f) **écart assumé (KB-MED-110, à confirmer avec les métiers)** : le PDF de greffe et l'évaluation KDIGO d'un patient dont une étape
  ou un bilan porte une valeur illisible en base répondaient 400 avec le nom qualifié d'une classe Java du module retiré ; le contexte
  ignore l'étape illisible (le PDF est rendu sans elle, avertissement serveur avec son seul identifiant) et refuse un bilan illisible en
  422 `BILAN_PRE_GREFFE_ILLISIBLE` (règles de RG-MED-046 d et 047 e) ; de même, une **sérologie ou un antécédent illisible** en base
  (marqueur, résultat, type, statut ou système de codage hors des valeurs connues) faisait échouer tout le PDF et l'évaluation KDIGO en
  400 dans le legacy ; le contexte ignore la ligne illisible (avertissement serveur) et rend les lignes lisibles du patient (règles des
  lecteurs DM4/DM5) ; **à confirmer par les médecins (ignorer ou refuser une sérologie illisible)**.
- **RG-MED-049g** — *Suppression complémentaire des consolidés cliniques (KB-MED-104, **CORRIGÉ le 2026-11-09**, lot L1, ADR 0112 ; à la
  demande de l'utilisateur, **validation du DPO attendue au plus tard le 2026-12-15**)* : `IndicateursCliniquesPublies.desCentres` masque par
  centre tout effectif de 1 à 4 (`EffectifMasque`, zéro reste zéro, 5 exactement publié) ; le **consolidé** de la société est publié nul dès qu'au
  moins un centre porte l'effectif masqué, **champ par champ** : porteurs AgHBs, anti-VHC et VIH positifs, patients en retard d'observance,
  inscrits en liste d'attente, bilans de greffe en cours, greffes de la période ; pour chacun des cinq marqueurs (Kt/V, hémoglobine, phosphore,
  PTH, albumine), les patients évalués et les trois taux consolidés sont supprimés ensemble (le taux d'un total dont un centre est masqué
  permettrait la même déduction). Le booléen « observance en retard » (valeur brute servant l'alerte de la Direction, RG-DIR-105) est inchangé.
  Une société sans centre garde des consolidés à zéro. Avant la correction, les consolidés étaient la somme des valeurs brutes puis masquées.
  *Source :* `CalculerLesIndicateursCliniques`, `EffectifMasque.consolider`. Règle commune : RG-DIR-111.
- **RG-MED-049h** — *Dernière sérologie conclusive du risque infectieux (KB-MED-106, **CORRIGÉ le 2026-11-09**, lot L2, ADR 0113 ; à la demande
  de l'utilisateur, RISQUE CLINIQUE, **validation médicale attendue au plus tard le 2026-12-15**)* : seul un résultat **POSITIF ou NEGATIF est
  conclusif**. Pour chaque patient et chaque marqueur surveillé (AgHBs, anti-VHC, ARN du VHC, VIH ; centre de la sérologie), le risque est décidé
  par la **dernière sérologie conclusive** (date de prélèvement, puis horodatage de saisie ; à égalité totale le positif l'emporte, KB-MED-107) :
  le patient est à risque si elle est positive. Un résultat DOUTEUX ou EN_COURS plus récent **n'efface plus** un positif antérieur ; un
  DOUTEUX ou EN_COURS seul, ou précédé d'un seul négatif, ne crée aucun risque ; positif, négatif puis douteux : pas de risque ; les marqueurs
  sont indépendants. La casse reste stricte (un résultat ou un marqueur en casse inattendue n'est ni positif ni conclusif). Même règle sur tous les
  chemins qui décident de l'isolement (placement d'un patient, réaffectation après une sérologie, propositions de place, semaine : RG-PLN-012).
  **Effet** : des patients aujourd'hui hors isolement parce qu'un douteux ou un en cours a succédé à un positif y reviennent (liste à faire valider
  par les médecins avant mise en production : détection §75.1 de `verifications-production.md`). **Non modifié** : la règle propre de l'indicateur
  Direction des porteurs positifs (RG-MED-049e, KB-MED-105), où un douteux plus récent compte encore « non positif ».
  *Source :* `RisqueInfectieux`, `PatientsARisqueInfectieuxJdbc`.
- **RG-MED-049i** — *Statistiques du patient : reprise sur place PL4 (ADR 0139, 2026-11-12)* : les six routes (`GET` et `POST .../search` de
  `stats/paramedical`, `stats/medical` et `stats/export`) sont servies par `contexts/dossier-medical` (`StatistiquesDuPatientControleur`, cas
  d'usage `ConsulterLesStatistiquesParamedicales`, `ConsulterLesStatistiquesMedicales`, `ExporterLesStatistiquesDuPatient`) ; URL, droits
  (volets : `ADMIN`, `INFIRMIER`, `MEDECIN`, `SECRETAIRE` ; exports : `ADMIN` seul), champs JSON, calculs, CSV (six colonnes) et PDF
  **inchangés**. Ordre des gardes : (1) **centre de la session** : 403 `ACCESS_DENIED` (**KB-PL4-5 CORRIGÉ** : avant, une plage inversée
  sur le centre d'un autre donnait 400 avant le 403) ; (2) **période** : début après fin = 400 `INVALID_DATE_RANGE` ; (3) **format**
  (exports) : ni `pdf` ni `csv` = 400 `INVALID_EXPORT_FORMAT` ; (4) **patient du centre** : inconnu **ou d'un autre centre** = 404
  `PATIENT_INTROUVABLE`, même réponse sans divulgation inter-centres (**KB-PL4-2 CORRIGÉ** : avant, 200 avec des zéros). **KB-PL4-1 CORRIGÉ** :
  `centerId` absent ou illisible, `patientId`, `from`, `to` ou `dateFrom` illisibles, corps POST illisible ou absent = 400 `BAD_REQUEST`
  (avant : 500 sur les volets paramédicaux ; le 403 d'un profil sans droit passe avant ce 400) ; centre ou patient absent d'un corps lisible =
  400 `VALIDATION_ERROR`. **KB-PL4-3 REPRODUIT** (dette consignée) : une tension illisible (« bizarre », « 9/5 », « 1000/50 ») est rendue
  `null` sans signal ; seuls 2 ou 3 chiffres de part et d'autre de `/` ou `-` sont lus. **KB-PL4-4 CORRIGÉ** : une consultation par corps
  (`POST stats/paramedical/search`, `stats/medical/search`, `stats/export/search`) est journalisée sous l'action de lecture
  `STATISTIQUES_PATIENT_CONSULTATION` (« Statistiques du patient consultées », entité `patients`), y compris sur un refus 403, et non plus
  `PATIENTS_CREATION` ; le journal historique n'est pas réécrit. Le poids sec cible est celui de la dernière prescription datée au plus tard de
  la fin de la période (sinon du jour UTC).
  *Source :* `StatistiquesDuPatientControleur`, `GardeDesStatistiques`, `Tension`, `PeriodeDeStatistiques`, `FormatDExport`,
  `SeancesDuPatientPubliees`, `StatistiquesDuPatientEnPdf`, `AuditActionResolver`.

## 11.11 Constantes, statistiques et export interopérable

- **RG-MED-100** — Les **constantes** (poids, tension, débit, UF, durée) sont une vue longitudinale **en lecture seule**
  du volet paramédical saisi en séance (source unique de vérité), paginée (`MEDECIN`, `ADMIN`). Valeurs restituées
  telles qu'enregistrées, sans normalisation. Du plus récent au plus ancien (date de séance, puis création), tous statuts
  de séance ; une séance sans volet figure avec des constantes vides ; un patient inconnu ou d'un autre centre donne une
  liste vide. **Garde de centre** : un `centerId` qui n'est pas le centre de la session est refusé (403) ; paramètre
  illisible ou taille de page hors de 1 à 100 : 400 (ADR 0080, à confirmer avec les métiers/RGPD). La lecture n'est pas
  journalisée (question ouverte pour le DPO). Module `contexts/dossier-medical`, slice DM1.
- **RG-MED-103** — **Garde de centre sur les mesures paramédicales** : l'enregistrement du volet paramédical d'une
  séance (`PUT /seances/{id}/volet-paramedical`), le détail, la liste, le journal et le calendrier des séances
  (`GET /seances/{id}`, `/seances`, `/seances/journal`, `/seances/calendar`), la création, le scan et la modification
  d'une séance (`POST /seances`, `POST /seances/scan`, `PUT /seances/{id}`), les statistiques paramédicales et médicales
  du patient et leur export CSV/PDF (`GET /patients/{id}/stats/paramedical`, `stats/medical`, `stats/export` et les
  `POST` de recherche correspondants) confrontent le centre demandé (ou celui du corps) au centre du jeton : 403 sinon
  (avant : fuite et écriture inter-centres, KB-MED-01 et 02). Il en va de même pour les autres actions de la séance :
  forfait, validation, déverrouillage de régularisation, signature du médecin, consommables, jours fériés et fermetures
  du calendrier, volet médical : toute route de `/api/v1/seances` est confrontée au centre de la session. Droits inchangés (le médecin seul lit les constantes mais n'en saisit pas, KB-MED-08).
- **RG-MED-101** — **Statistiques du patient** (`ADMIN`, `INFIRMIER`, `MEDECIN`, `SECRETAIRE`) : moyennes médicales (Hb,
  Kt/V, ferritine…) et paramédicales sur une période ; **UF réelle** = ultrafiltration
  saisie, sinon perte de poids per-dialytique × 1000 ; tension lue au format « sys/dia » (ou « sys-dia ») ; poids sec
  cible en vigueur le jour de la séance ; export CSV et PDF réservé à `ADMIN`.
- **RG-MED-102** — **Export FHIR R4** du dossier d'un patient (`MEDECIN`), en lecture seule, sous forme de `Bundle`
  JSON : `Patient`, `Condition` (antécédents), `AllergyIntolerance`, `Observation`
  (résultats d'analyse et observations LOINC), `MedicationRequest` (ordonnances), `Procedure` (abords vasculaires). Les
  demandes d'examen et les administrations d'anémie **ne sont pas encore
  exportées** (limite connue).
