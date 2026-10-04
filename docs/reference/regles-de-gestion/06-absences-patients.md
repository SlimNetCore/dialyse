# 06 — Absences des patients

> Préfixe `RG-ABS`. Le système **détecte** chaque nuit les séances prévues non réalisées, **valorise** la perte de
> chiffre d'affaires,
> **alerte** sur les absences à qualifier et sur celles qui sont en retard, et **clôture** les corrections avec la
> facturation.
> Sources : `AbsencePatient`, `MotifAbsence`, `StatutAbsence`, `ValeurAbsence`, `ValorisationAbsenceService`,
> `AbsencePatientService`, `AbsencePatientScheduler`, `AbsenceDonneesJdbcAdapter`, `AbsencePatientRestController`.

## 6.1 Définition et cycle de vie

- **RG-ABS-001** — Une absence est celle d'un patient à une séance prévue ; il y a **au plus une absence par patient et
  par jour**
  (`ABSENCE_EXISTANTE`, y compris si la précédente a été annulée).
- **RG-ABS-002** — Source : `AUTOMATIQUE` (détectée par le contrôle nocturne) ou `DECLAREE` (saisie par un utilisateur).
- **RG-ABS-003** — Statuts : `A_QUALIFIER` → `JUSTIFIEE` ou `NON_JUSTIFIEE` (qualification par un motif) → `RATTRAPEE`
  (séance de
  remplacement) ; `ANNULEE` possible à tout moment. Une absence **rattrapée ou annulée n'est plus modifiable**
  (`ABSENCE_NON_MODIFIABLE`) ; l'annulation est définitive.
- **RG-ABS-004** — Motifs : hospitalisation, maladie, voyage, transport, familial, refus du patient, décès, autre (tous
  **justifiants**) et
  « non justifiée » (seul motif non justifiant, donne le statut `NON_JUSTIFIEE`).
- **RG-ABS-005** — Le motif « autre » exige un commentaire (`ABSENCE_COMMENTAIRE_REQUIS`). Le motif est obligatoire à la
  qualification (`ABSENCE_MOTIF_REQUIS`).
- **RG-ABS-006** — Une absence est **comptabilisée** (perte de séance et de chiffre d'affaires) tant qu'elle est à
  qualifier, justifiée ou non
  justifiée ; elle ne l'est plus une fois rattrapée ou annulée.

## 6.2 Déclaration

- **RG-ABS-010** — Le patient doit exister dans le centre (`PATIENT_INTROUVABLE`).
- **RG-ABS-011** — La date de la séance manquée ne peut pas être future (`ABSENCE_DATE_FUTURE`) ni plus ancienne que
  **60 jours**
  (`ABSENCE_DELAI_DEPASSE`).
- **RG-ABS-012** — Une absence ne peut être déclarée à une date où le patient a une séance **réalisée**
  (`ABSENCE_SEANCE_REALISEE`) :
  est réalisée une séance au statut `VALIDEE`, `SIGNEE` ou `FACTUREE`.
- **RG-ABS-013** — Une déclaration avec motif est qualifiée d'emblée ; sans motif elle reste « à qualifier ».
- **RG-ABS-014** — La déclaration est possible depuis l'écran de suivi des absences (choix du patient par saisie
  assistée, motif en ligne,
  erreurs affichées dans le formulaire) et depuis le planning de la semaine.

## 6.3 Qualification, rattrapage, annulation

- **RG-ABS-020** — Requalifier une absence déjà qualifiée exige un commentaire (`ABSENCE_COMMENTAIRE_REQUIS`) **et** un
  profil autorisé :
  seuls `ADMIN` et `MEDECIN` corrigent une absence déjà qualifiée (`ABSENCE_CORRECTION_INTERDITE`) ; la première
  qualification est ouverte à
  `ADMIN`, `SECRETAIRE`, `MEDECIN`, `INFIRMIER`.
- **RG-ABS-021** — Rattrapage : la date est obligatoire (`ABSENCE_RATTRAPAGE_DATE_REQUISE`), **postérieure** à la séance
  manquée (`ABSENCE_RATTRAPAGE_ANTERIEUR`), non future (`ABSENCE_DATE_FUTURE`) et le patient doit avoir une séance
  réalisée à cette date (`ABSENCE_RATTRAPAGE_SANS_SEANCE`). La perte n'est alors plus comptabilisée.
- **RG-ABS-022** — Annulation : commentaire obligatoire (`ABSENCE_COMMENTAIRE_REQUIS`) ; déjà annulée :
  `ABSENCE_DEJA_ANNULEE` ; mêmes droits de
  correction que RG-ABS-020 pour une absence déjà qualifiée.
- **RG-ABS-023** — Absence inconnue du centre : `ABSENCE_INTROUVABLE`.
- **RG-ABS-024** — **Clôture avec la facturation** : toute création, qualification, rattrapage ou annulation est refusée
  si une facture du patient couvre
  la date de la séance (`ABSENCE_PERIODE_FACTUREE`).
- **RG-ABS-025** — Chaque modification enregistre l'auteur et l'horodatage ; la déclaration et la qualification
  enregistrent leurs propres auteur/date.

## 6.4 Valorisation (perte de chiffre d'affaires)

- **RG-ABS-030** — La valeur d'une absence est **figée à sa création** : forfait de la prise en charge applicable à la
  date de la séance (prix **HT**, comme en facturation), taux du type de TVA actif ce jour-là, montant **TTC** = HT ×
  (1 + taux/100), arrondi à deux décimales.
- **RG-ABS-031** — Forfait applicable : PEC dont la période effective (ou, à défaut, demandée) couvre la date ; sinon
  dernier forfait connu de la PEC (effectif sinon demandé). Sans forfait, la valeur est nulle (jamais bloquant). Une TVA
  exonérée ou absente vaut 0 %.
- **RG-ABS-032** — Prix, taux et montant HT ne peuvent être négatifs (valeurs refusées sinon).

## 6.5 Détection automatique et alertes

- **RG-ABS-040** — Contrôle quotidien (02:30, tâche `AbsencePatientScheduler.controlerAbsences`) de chaque centre actif,
  sur les **3 derniers jours**
  (hors aujourd'hui) : (1) **réconciliation** — les absences détectées « à qualifier » dont la séance a finalement été
  réalisée sont annulées avec le
  commentaire « Séance réalisée à cette date » ; (2) **détection** des absences. Un échec sur un centre n'empêche pas
  les autres.
- **RG-ABS-041** — Un patient est **attendu** un jour donné si : le jour de la semaine est coché sur sa fiche, il n'est
  pas « en sommeil », sa date
  d'admission n'est pas postérieure, sa place n'est pas libérée (patient transféré ou guéri : attendu jusqu'à la date de
  l'évènement incluse ; décédé ou greffé : plus attendu à cette date ; séjour occasionnel ou vacancier : attendu jusqu'à
  la fin de séjour incluse ; état de sortie sans date : plus attendu, cf. RG-PAT-032), le centre n'est pas fermé ce jour
  (férié ou fermeture exceptionnelle) et il n'a
  aucune séance réalisée ce jour.
- **RG-ABS-042** — La détection est **idempotente** : aucune absence n'est créée si une absence existe déjà (même
  annulée) ou si la période est facturée.
  Les absences détectées naissent « à qualifier ».
- **RG-ABS-043** — Une absence à qualifier est **en retard** au-delà de **3 jours** après la séance manquée.
- **RG-ABS-044** — Quand des absences sont à qualifier, le centre est notifié en temps réel (`ABSENCES_A_QUALIFIER`,
  nombre à qualifier, nombre en retard)
  aux rôles `ADMIN`, `SECRETAIRE`, `INFIRMIER`, `MEDECIN`. La synthèse (à qualifier / en retard) est consultable à tout
  moment.

## 6.6 Consultation

- **RG-ABS-050** — La liste des absences est paginée et filtrable (statut, motif, patient, période), limitée au centre ;
  accès `ADMIN`, `SECRETAIRE`,
  `MEDECIN`, `INFIRMIER` (y compris profils restreints, voir RG-SEC-022/023).
- **RG-ABS-051** — Le suivi de la semaine (dimanche → samedi) renvoie les absences non annulées et les séances
  réalisées, pour colorer le planning :
  les séances réalisées (validées par l'infirmier à la présence du patient) s'affichent avec un soleil sur fond jaune.
- **RG-ABS-052** — Les absences alimentent le tableau de bord de la direction (volumes, perte valorisée, taux — voir
  RG-DIR) sans donnée nominative.
