# 06 — Absences des patients

> Préfixe `RG-ABS`. Le système **détecte** chaque nuit les séances prévues non réalisées, **valorise** la perte de
> chiffre d'affaires,
> **alerte** sur les absences à qualifier et sur celles qui sont en retard, et **clôture** les corrections avec la
> facturation.
> Sources : `AbsencePatient` (contexte `absences-patients`), `MotifAbsence`, `StatutAbsence`, `Valorisation`, `DetectionAbsences`,
> `ReconciliationAbsences`, `NotificationDesAbsencesAQualifier`, `ControlerLesAbsences`, `ControleDeNuitDesAbsences`, `DetectionControleur`,
> `PatientsAttendusJdbc`.

## 6.1 Définition et cycle de vie

- **RG-ABS-001** — Une absence est celle d'un patient à une séance prévue ; il y a **au plus une absence par patient et
  par jour**
  (`ABSENCE_EXISTANTE`, y compris si la précédente a été annulée).
- **RG-ABS-002** — Source : `AUTOMATIQUE` (détectée par le contrôle nocturne) ou `DECLAREE` (saisie par un utilisateur).
- **RG-ABS-003** — Statuts : `A_QUALIFIER` → `JUSTIFIEE` ou `NON_JUSTIFIEE` (qualification par un motif) → `RATTRAPEE`
  (séance de
  remplacement) ; `ANNULEE` possible à tout moment. Une absence **rattrapée ou annulée n'est plus modifiable**
  (`ABSENCE_NON_MODIFIABLE`) ; l'annulation est définitive. **Une absence rattrapée ne s'annule pas** (`ABSENCE_NON_MODIFIABLE`, sa date de
  rattrapage est conservée ; était : annulable par `ADMIN` et `MEDECIN`, KB-ABS-10, corrigé à la slice A2).
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
- **RG-ABS-015** — **Qui déclare.** La déclaration d'une absence est réservée à `ADMIN`, `SECRETAIRE` et `INFIRMIER`
  (refus d'accès côté serveur pour tout autre profil). **Le médecin ne déclare pas l'absence d'un patient** : son
  planning
  est en **lecture seule** (aucun bouton de déclaration, ni sur le planning, ni sur l'écran de suivi des absences,
  RG-PLN-104) ;
  il consulte le suivi et garde la correction d'une absence déjà qualifiée (RG-ABS-020).

## 6.3 Qualification, rattrapage, annulation

- **RG-ABS-020** — Requalifier une absence déjà qualifiée exige un commentaire (`ABSENCE_COMMENTAIRE_REQUIS`) **et** un
  profil autorisé :
  seuls `ADMIN` et `MEDECIN` corrigent une absence déjà qualifiée (`ABSENCE_CORRECTION_INTERDITE`) ; la première
  qualification est ouverte à
  `ADMIN`, `SECRETAIRE`, `MEDECIN`, `INFIRMIER`. Ordre des contrôles (A2, KB-ABS-18 aligné) : absence existante, droit de
  correction, état (non modifiable, déjà annulée), période facturée, puis les valeurs.
- **RG-ABS-021** — Rattrapage : la date est obligatoire (`ABSENCE_RATTRAPAGE_DATE_REQUISE`), **postérieure** à la séance
  manquée (`ABSENCE_RATTRAPAGE_ANTERIEUR`), non future (`ABSENCE_DATE_FUTURE`) et le patient doit avoir une séance
  réalisée à cette date (`ABSENCE_RATTRAPAGE_SANS_SEANCE`). **Une séance ne rattrape qu'une seule absence** du
  patient (`ABSENCE_RATTRAPAGE_DEJA_UTILISEE`). **Le rattrapage est réservé à `ADMIN` et `MEDECIN`**, pour toute absence, même « à
  qualifier » (`ABSENCE_CORRECTION_INTERDITE` sinon ; était ouvert aux quatre profils, KB-ABS-13, corrigé à la slice A2). La
  perte n'est alors plus comptabilisée. À l'écran, l'utilisateur
  **choisit la séance dans une liste** (`GET /absences-patients/{id}/seances-rattrapage` : séances réalisées du patient
  après la séance manquée, jusqu'à aujourd'hui, pas déjà utilisées) au lieu de saisir une date ; sans séance éligible,
  l'écran renvoie au poste infirmier (le patient doit d'abord venir et être scanné). **Cette liste n'est proposée que
  pour une absence encore rattrapable** : pour une absence déjà rattrapée ou annulée (plus modifiable, RG-ABS-003), elle
  est refusée en **422** `ABSENCE_NON_MODIFIABLE` (était : proposée quand même, KB-ABS-04, corrigé à la slice A1).
- **RG-ABS-022** — Annulation : commentaire obligatoire (`ABSENCE_COMMENTAIRE_REQUIS`) ; déjà annulée :
  `ABSENCE_DEJA_ANNULEE` ; mêmes droits de
  correction que RG-ABS-020 pour une absence déjà qualifiée.
- **RG-ABS-023** — Absence inconnue **ou d'un autre centre** (indistinguables) : `ABSENCE_INTROUVABLE` ; **404** sur la
  consultation (liste des séances de rattrapage, slice A1 ; était 422, KB-ABS-03 corrigé) **et sur les écritures** (slice A2).
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

- **RG-ABS-040** — Contrôle quotidien (02:30, tâche `ControleDeNuitDesAbsences.controlerAbsences`, un seul déclencheur
  serveur) de chaque centre **actif** (un centre inactif n'est pas contrôlé, KB-ABS-26 corrigé), sur les **7 derniers
  jours terminés** du centre (hors aujourd'hui, au fuseau du centre, RG-ABS-059 ; une semaine pour absorber un arrêt du
  serveur) : (1) **réconciliation** — les absences **détectées** (source automatique) encore « à qualifier », **hors
  période facturée**, dont la séance a finalement été réalisée sont annulées avec le commentaire « Séance réalisée à cette
  date » et sans auteur ; une absence déclarée par un utilisateur, qualifiée ou en période facturée n'est jamais touchée
  (KB-ABS-22 corrigé) ; tout est traité en un seul passage, sans page sautée (KB-ABS-23 corrigé) ; (2) **détection** des
  absences. Un mois d'absences clos est sauté et journalisé (aujourd'hui tous les mois sont ouverts, la clôture A6 n'existe
  pas encore). Une absence en incident est isolée, journalisée (compteur par centre au niveau ERROR), et n'arrête ni son centre ni les
  autres (KB-ABS-27 corrigé) ; elle est retentée chaque nuit tant que son jour reste dans la fenêtre de 7 jours, puis relancée par
  le rattrapage manuel (dont la réponse porte `echecs`) une fois la cause corrigée.
- **RG-ABS-045** — **Rattrapage au démarrage** : à chaque démarrage du serveur, le contrôle (réconciliation +
  détection) est rejoué sur les **31 derniers jours** de chaque centre actif, sans notification (la notification suit le
  contrôle de la nuit). Idempotent ; désactivable par la propriété `app.absences.startup-catchup`.
- **RG-ABS-046** — **Rattrapage manuel d'une période passée** (`POST /api/v1/absences-patients/rattrapage-detection`,
  `ADMIN`, centre courant) : réconciliation puis détection jour par jour entre deux dates, pour les absences jamais
  détectées (mois antérieurs au déploiement, serveur arrêté, reprise de données), sans notification. La fin est ramenée à
  hier **et la réponse le signale** (`fin` et `finRamenee`, champs présents seulement dans ce cas) ; le début doit la précéder
  (`ABSENCE_PERIODE_INVALIDE`, y compris un début aujourd'hui ou futur) ; la période ne dépasse pas 366 jours
  (`ABSENCE_PERIODE_TROP_LONGUE`). Réponse : absences créées et annulées (`creees`, `annulees`) et, seulement si des absences
  ont été en incident, leur nombre (`echecs`). Une entrée illisible (JSON, date impossible, `centerId` non UUID) est refusée en
  **400** `BAD_REQUEST`, un corps non JSON en **415** (était 500, KB-ABS-28 aligné). **Limite :** la détection s'appuie sur la
  fiche patient actuelle ; un patient dont les jours de dialyse ont été retirés à la libération de sa place (RG-PAT-033) n'est
  plus détecté pour ses jours passés.
- **RG-ABS-041** — Un patient est **attendu** un jour donné si : le jour de la semaine est coché sur sa fiche, il n'est
  pas « en sommeil », sa date d'admission n'est pas postérieure, sa place n'est pas libérée, le centre n'est pas fermé ce
  jour (férié ou fermeture exceptionnelle) et il n'a aucune séance réalisée ce jour (validée, signée ou facturée). **Sortie
  (KB-ABS-24, jour uniformisé)** : un patient transféré, guéri, décédé ou greffé n'est plus attendu **le jour de l'événement ni
  après** (était : inclus pour transféré et guéri) ; un état de sortie **sans date d'événement** n'est **jamais attendu**
  (comportement conservé, en attendant que Patient et couverture interdise une sortie sans date : prérequis noté dans
  `.ddd/migration/roadmap.md` ; liste des cas : `verifications-production.md` § 25.2) ; séjour occasionnel ou vacancier : attendu jusqu'à la fin de séjour incluse, ou sans fin.
- **RG-ABS-042** — La détection est **idempotente** : aucune absence n'est créée si une absence existe déjà (même
  annulée) ou si la période est facturée. Les absences détectées naissent « à qualifier » ; la détection est une
  transaction par absence (déclaration simultanée du même jour : l'absence existe, rien à créer).
- **RG-ABS-043** — Une absence à qualifier est **en retard** au-delà de **3 jours** après la séance manquée.
- **RG-ABS-044** — Après le contrôle de nuit (jamais au démarrage ni au rattrapage manuel), si des absences sont encore à
  qualifier, le centre est notifié en temps réel (`ABSENCES_A_QUALIFIER`, nombre à qualifier, nombre en retard) aux rôles
  `ADMIN`, `SECRETAIRE`, `INFIRMIER`, `MEDECIN`. **Une seule notification par centre et par jour du centre** (clé
  déterministe : deux contrôles du même jour, même sur deux instances du serveur, n'en créent qu'une ; KB-ABS-25 corrigé).
  Les comptes portent sur **toutes** les absences encore « à qualifier » dont le mois d'absences est **ouvert** (sans limite de
  fenêtre ; les mois clos ne comptent plus). La synthèse (à qualifier / en retard) est consultable à tout moment.
- **RG-ABS-058** — **Réconciliation refusée par le domaine** : annuler par réconciliation une absence déclarée, qualifiée,
  annulée ou en période facturée est refusé (`ABSENCE_NON_RECONCILIABLE`, jamais exposé en HTTP : la politique saute ces
  absences). *Source :* `AbsencePatient.reconcilier`.
- **RG-ABS-059** — **Fuseau du centre** (décision 7, slice A3) : « aujourd'hui », la fenêtre de la nuit, le « en retard »,
  la semaine du planning courante et les contrôles de date des écritures (déclaration du jour, rattrapage) se calculent à la
  date du **fuseau du centre**, `Africa/Algiers` par défaut (`app.absences.fuseau-par-defaut`) : le centre n'a pas encore de
  fuseau en base. Le déclencheur de 02:30 suit ce fuseau. Les instants restent absolus.
  *Source :* `HorlogeDesCentres`, `FuseauParDefaut`, `ControleDeNuitDesAbsences`.

## 6.6 Consultation

- **RG-ABS-050** — La liste des absences est paginée et filtrable (statut, motif, patient, période), limitée au centre ;
  accès `ADMIN`, `SECRETAIRE`,
  `MEDECIN`, `INFIRMIER` (y compris profils restreints, voir RG-SEC-022/023). Tri : date de séance la plus récente
  d'abord, puis identifiant. **Pagination bornée** : `page` et `size` négatifs sont refusés (400 `BAD_REQUEST`),
  `size` est **plafonnée à 100** (la taille effective figure dans la réponse), `size = 0` ne renvoie aucun élément mais
  le total (KB-ABS-02, corrigé à la slice A1). Chaque ligne porte `enRetard` (RG-ABS-043, jour courant au fuseau du centre, RG-ABS-059).
- **RG-ABS-051** — Le suivi de la semaine (dimanche → samedi) renvoie les absences non annulées et les séances
  réalisées, pour colorer le planning :
  les séances réalisées (validées par l'infirmier à la présence du patient) s'affichent avec un soleil sur fond jaune.
- **RG-ABS-052** — Les absences alimentent le tableau de bord de la direction (volumes, perte valorisée, taux — voir
  RG-DIR) sans donnée nominative. Le contexte les publie par le langage `AbsencesDuPilotage` (A4, RG-ABS-060), jamais par sa table.
- **RG-ABS-053** — **Paramètres illisibles de la consultation** : un statut ou un motif inconnu (ou en minuscules), un
  identifiant (centre, patient, absence) qui n'est pas un UUID, une date qui n'est pas au format `AAAA-MM-JJ`, une
  `page` ou une `size` non numérique est refusé en **400** `BAD_REQUEST` (était : 500 `INTERNAL_ERROR`, KB-ABS-01,
  corrigé à la slice A1). Un filtre vide (`statut=`) est ignoré ; `du` après `au` ne retient aucune absence.
  *Source :* `AbsencesControleur`, `EntreesDesAbsencesAdvice` (contexte `absences-patients`).

- **RG-ABS-054** — **Écritures concurrentes** (slice A2, KB-ABS-11 corrigé) : chaque écriture est une transaction qui verrouille
  l'absence (un rattrapage verrouille toutes les absences du patient). Une écriture qui arrive pendant une autre attend, puis
  voit l'état validé (donc `ABSENCE_DEJA_ANNULEE` plutôt qu'une double annulation). Deux déclarations du même patient et du même
  jour : la perdante reçoit **409** `ABSENCE_ECRITURE_CONCURRENTE` (était : erreur technique 500 le plus souvent) ; une
  déclaration qui lit l'absence déjà créée reçoit toujours 422 `ABSENCE_EXISTANTE`, y compris quand cette absence est annulée.
  Une séance de rattrapage ne sert qu'à une absence **rattrapée** (index unique partiel, migration V43 ; une absence annulée
  ne réserve rien).
  *Source :* `AbsencesJdbc`, `ErreursMetierDesAbsencesAdvice`, `V43__absences_patients_unicite_du_rattrapage.sql`.
- **RG-ABS-055** — **Entrées illisibles des écritures** (KB-ABS-17 aligné, slice A2) : JSON illisible, date ou identifiant du
  corps illisible, `centerId` ou identifiant du chemin qui n'est pas un UUID, motif inconnu, commentaire de plus de **1000**
  caractères : **400** `BAD_REQUEST` (étaient 500) ; corps qui n'est pas du JSON : **415** `UNSUPPORTED_MEDIA_TYPE` (était 500) ;
  champ obligatoire absent : 400 `VALIDATION_ERROR` (inchangé).
  *Source :* `AbsencesEcrituresControleur`, `EntreesDesAbsencesAdvice`, `Commentaire`.
- **RG-ABS-056** — **Valeur figée** (slice A2) : forfait, montants et taux ne s'écrivent qu'à la création, aucune commande ne les
  écrit ensuite. La règle de choix du forfait (effectif avant demandé, même hors période ; repli sur la prise en charge la plus
  récente portant un forfait) et le taux (règle `HEMODIALYSE` exacte, exonérée = 0 %) sont reproduits du legacy (KB-ABS-16,
  KB-CPT-19). Le taux est renvoyé avec l'échelle de la base (`0.00`, était `0`, KB-ABS-15). Un commentaire donné sans motif à la
  déclaration est **conservé** (était perdu, KB-ABS-12), et une qualification sans commentaire **garde** le commentaire déjà présent
  (était : effacé, KB-ABS-14 aligné) ; une annulation ou une requalification **remplace** le commentaire par le nouveau.
  *Source :* `Valorisation`, `ForfaitsDesPatientsJdbc`, `TauxDeTvaComptabilite`.
- **RG-ABS-057** — **Exclusion et réinclusion d'une séance** (Facturation, ADR 0031, slice A2) : l'exclusion déclare l'absence par
  le même cas d'usage, dans la transaction de l'exclusion, et **diffuse la saisie temps réel** `ABSENCE` après validation (KB-ABS-19,
  était : aucune diffusion). **L'absence d'une exclusion ne s'annule pas** (l'origine est lue sur la séance exclue qui y est liée) :
  **422** `ABSENCE_D_EXCLUSION`, « réincluez la séance dans la facturation » ; elle peut en revanche être requalifiée ou rattrapée.
  **Seule la réinclusion la retire**, quel que soit son état (KB-ABS-20 : une séance exclue ne reste plus bloquée) ; une absence annulée
  héritée d'avant A2 n'est jamais supprimée par l'application : la réinclusion est alors refusée (**422**
  `ABSENCE_D_EXCLUSION_MODIFIEE`), à traiter avec l'administrateur (liste : `verifications-production.md` § 24.4). Une absence déjà
  supprimée : la réinclusion réussit.
  *Source :* `ExclusionDeLaFacturation`, `AnnulerUneAbsence`, `RetirerLAbsenceDeLExclusion`, `ExclusionsDeSeancesJdbc`, `SignalementDesSaisiesLegacy`.

- **RG-ABS-060** — **Lectures publiées pour le pilotage** (slice A4, ADR 0041) : le tableau de bord des séances d'un centre (nombre, détail,
  exports) et la vue de la Direction (cumul par centre, statut et motif, mensuel) lisent les absences par le langage publié
  `AbsencesDuPilotage`, avec les mêmes requêtes qu'avant : bornes de dates incluses, **absences annulées jamais comptées**, centres autorisés
  fournis par l'appelant. Le mensuel ne garde que les statuts à qualifier, justifiée et non justifiée. Aucune table d'absence n'est lue hors du contexte.
  *Source :* `AbsencesDuPilotage`, `AbsencesDuPilotageJdbc`, `SeanceRestController`, `DirectionAbsencesQueryService`.
- **RG-ABS-061** — **« Aujourd'hui » du pilotage et absences futures** (KB-ABS-34, 35, corrigés à la slice A4) : « aujourd'hui » est le jour du fuseau des
  centres (RG-ABS-059), pas celui de l'UTC, pour la période par défaut de la Direction (année en cours jusqu'à aujourd'hui) et la fin de période
  du tableau de bord. **Aucune absence postérieure à aujourd'hui n'est comptée**, dans le tableau de bord (nombre, détail, exports CSV, PDF, XLSX) comme dans la vue Direction
  (cumul par centre, motifs, mensuel), **quelle que soit la fin de période demandée** (mois en cours inclus) ; pour un mois futur, le nombre est 0, le détail
  vide et la fin de période du tableau de bord est égale au début (jamais antérieure). La période affichée et les séances, le chiffre d'affaires et le taux
  gardent la fin demandée. *(Était : jour UTC ; absences futures comptées ; fin de période avant le début.)*
- **RG-ABS-062** — **Entrées illisibles du pilotage** (KB-ABS-33, 36, corrigés à la slice A4) : une date `from` ou `to` illisible de
  `GET /api/v1/direction/absences` est refusée en **400** `BAD_REQUEST` (était : 500) ; un `format` d'export du tableau de bord autre que
  `csv`, `pdf` ou `xlsx` (sans égard à la casse) est refusé en **400** `BAD_REQUEST` (était : CSV servi silencieusement). Sans `format` : CSV.
  La période de la Direction reste refusée en 422 si inversée (`PERIODE_INVALIDE`) ou de plus de 5 ans (`PERIODE_TROP_LONGUE`).
  *Source :* `DatesIllisiblesDeLaDirectionAdvice`, `SeanceRestController.exportDashboard`.
