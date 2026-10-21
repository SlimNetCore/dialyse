# 12 — Facturation, TVA, règlements et comptabilité

> Préfixes `RG-FAC` (facturation et TVA), `RG-REG` (règlements), `RG-CPT` (comptabilité). Le système **n'accepte à la
facturation que des séances validées et couvertes**, **fige** prix, TVA et
> payeur sur chaque facture, **calcule le solde** de chaque facture et **génère les écritures comptables équilibrées**.
> Sources : `ConsulterLeTableauDeBord`, `ImprimerLaSynthese`, `SyntheseFinanciereDesCentres`, `Simulation`, `Facture`, `ValiderLaFacturation`, `RegleTva`,
> `GererLesTypesDeTva`, `ParametresDeFacturation`, `ModeleDeNumerotation`,
> `Reglement`, `SituationDeFacture`, `EnregistrerUnReglement`, `RedactionDesEcritures`,
> `EcritureComptable`, `MappingComptable`, `RegleTVA`, listeners comptables,
> contrôleurs `Facturation*`, `TypesTvaControleur`, `ParametresFacturationControleur`, `Reglement*`, `Comptabilite*`.

## 12.1 Types de TVA

- **RG-FAC-001** — Un type (ou règle) de TVA est propre à un centre : libellé obligatoire, **taux de 0 à 100 %**
  (arrondi à deux décimales par la base), type de prestation obligatoire (texte comparé tel que saisi), indicateur
  d'exonération, **date de début de validité obligatoire**, date de fin facultative **égale ou postérieure** au début,
  texte de référence légale facultatif. **Deux types actifs du même centre et du même type de prestation ne se
  chevauchent pas** (périodes [début, fin] bornes incluses, fin absente = période ouverte, types désactivés ignorés).
  C'est **la même règle** que celle des règles de TVA comptables (RG-CPT-006) : une seule table, dont la Comptabilité
  est propriétaire (ADR 0030) ; les types déjà enregistrés ne sont pas revalidés. Refus (400) : taux hors bornes, « La
  date de fin de validité (…) précède la date de début (…) », « La règle de TVA … chevauche la règle existante … ».
  *Source :* `RegleTva`, `TauxDeTva`, `GererLesTypesDeTva` (contexte `comptabilite`) → `TypesTvaControleur`
  (`/tva-types`).
- **RG-FAC-002** — La TVA applicable à une date est le type **actif** couvrant cette date pour la prestation
  `HEMODIALYSE` (le dernier commencé) ; **sans type actif, le taux est 0 %**. Le taux est figé sur chaque facture au
  moment de la validation. La simulation et la validation appliquent le taux en vigueur **à la fin de la période
  facturée** (RG-FAC-022, RG-FAC-030). *Source :* langage publié `ParametrageFiscal` (contexte `comptabilite`), lu par
  la simulation et la validation (`TauxDeTvaComptabilite`, contexte `facturation`).
- **RG-FAC-003** — Les types de TVA se **désactivent** (jamais supprimés) : la désactivation est idempotente et répond
  204 même pour un type inconnu du centre. La **modification** remplace libellé, taux, type, exonération, dates et texte
  selon RG-FAC-001 (le type modifié ne se compare pas à lui-même) ; elle **garde l'état** (un type désactivé le reste)
  et l'auteur d'origine ; un type inconnu dans le centre répond **404** et rien n'est écrit. Création, modification,
  désactivation : `ADMIN` ; consultation : `ADMIN`, `MEDECIN`, `SECRETAIRE` (liste paginée, triée par début décroissant
  puis libellé, relue à chaque appel). Le centre demandé est confronté à celui de la session : un autre centre est
  refusé (403). *Source :* `GererLesTypesDeTva` → `TypesTvaControleur`.

## 12.2 Paramètres de facturation du centre

- **RG-FAC-010** — Chaque centre définit un **modèle de numérotation** des factures (défaut `FAC-{YEAR}-{SEQ}`) : jetons
  `{YYYY}`/`{YEAR}`, `{YY}`, `{CENTER}` (8 premiers caractères de l'identifiant du centre), `{SEQ}` ou `{SEQn}`
  (séquence sur n chiffres, n de 1 à 99, 4 par défaut). Le modèle est obligatoire, contient **un jeton de séquence** et
  **aucun jeton inconnu**, et tient en 120 caractères ; sinon il est refusé (400 : « Le modèle de numérotation doit
  contenir le jeton de séquence {SEQ} ou {SEQn} … », « Jeton inconnu {…} … »). Les modèles déjà enregistrés ne sont pas
  revalidés. La séquence est strictement positive et propre au centre (et à l'année). *Source :* `ModeleDeNumerotation`,
  `ParametresDeFacturation`, `ConfigurerLaFacturation` (contexte `facturation`) → `ParametresFacturationControleur`
  (`/facturation/settings`) ; numéro calculé par `ModeleDeNumerotation.numero`, séquence réservée par
  `SequenceDeFacturation` (RG-FAC-030).
- **RG-FAC-011** — Option « **regroupement multi-forfait** » (défaut oui) : une facture par patient et par période
  (plusieurs lignes de forfaits) ; sinon une facture par patient **et par forfait**. Un centre qui n'a rien enregistré
  lit les valeurs par défaut, sans date de mise à jour : **la lecture n'écrit rien**. Modification des paramètres :
  `ADMIN` ; lecture : `ADMIN`, `SECRETAIRE` (le profil `MEDECIN`, admis par la route, est refusé par le filtre des
  profils : 403 `ROLE_SCOPE`). Le centre demandé est confronté à celui de la session (403 sinon). *Source :*
  `ConfigurerLaFacturation` → `ParametresFacturationControleur`.

## 12.3 Séances facturables et simulation

- **RG-FAC-020** — Est **éligible** une séance du centre, dans la période, au statut `VALIDEE` ou `SIGNEE` (la signature
  médecin n'est pas exigée) et **pas déjà facturée**. Le statut se compare en majuscules exactes (une séance « validee »
  est ignorée). La période est un mois entier (le mois l'emporte sur les dates) ou deux dates (fin non antérieure au
  début, 400 sinon ; période absente : 400 « La periode de facturation est obligatoire »). *Source :*
  `SeanceFacturable`,
  `PeriodeDeFacturation`, `SeancesFacturablesJdbc` (ACL sur les séances, contexte `facturation`).
- **RG-FAC-021** — **Forfait appliqué** à une séance : le forfait de surcharge posé sur la séance (RG-SEA-033) ; à
  défaut, le forfait de la prise en charge couvrant la date (effectif sinon demandé,
  de préférence la PEC dotée d'un forfait effectif, puis la plus récente) ; à défaut de PEC qui couvre la date, la PEC
  la
  plus récente même échue ; sans forfait, « Forfait non defini » à prix 0. *Source :* `PolitiqueDeForfaitApplique`.
- **RG-FAC-022** — La **simulation** (aperçu) construit les factures par groupe patient (ou patient + forfait selon le
  regroupement demandé), regroupe les séances par forfait en lignes (prix unitaire HT × nombre de séances),
  calcule **HT, TVA, TTC** (arrondis à deux décimales, TVA = HT × taux, **taux en vigueur à la fin de la période
  facturée**, 0 % sans règle) et le **numéro prévisionnel** : séquence courante du centre pour l'année du jour + 1, + 2…
  dans l'ordre des factures (patients triés par nom), selon le modèle du centre (RG-FAC-010), sans rien réserver. Elle
  n'enregistre rien. Accès : `ADMIN` (le profil `MEDECIN` est refusé par le filtre des profils, 403 `ROLE_SCOPE`) ; le
  centre demandé est confronté à celui de la session (403 sinon). *Source :* `Simulation`, `FactureSimulee`,
  `NumerotationPrevisionnelle`, `TauxDeTva`, `SimulerLaFacturation` → `SimulationControleur`
  (`POST /facturation/preview`).
- **RG-FAC-023** — Depuis la simulation, on peut **exclure une séance**, la **réinclure** ou **changer son forfait** ;
  la
  simulation est recalculée à chaque fois, dans la même transaction. **Exclure** (séance validée/signée non facturée
  uniquement) exige un **motif d'absence** (`HOSPITALISATION`, `MALADIE`, `VOYAGE`, `TRANSPORT`, `FAMILIAL`,
  `REFUS_PATIENT`, `DECES`, `AUTRE`, `NON_JUSTIFIEE` ; `AUTRE` exige un commentaire) : la séance passe au statut
  `ABSENT` et une **absence de patient** est déclarée avec ce motif, au nom de l'utilisateur de la session, selon les
  règles
  des absences (RG-ABS-001, 005, 011, 012 : déclaration au plus 60 jours après la séance, pas de date future, période du
  patient non encore facturée, une absence par patient et par jour) ; un refus annule l'exclusion (422). Le statut
  d'avant (validée ou signée)
  est mémorisé. **Réinclure** (`POST /facturation/preview/seances/{id}/include`) rend à la séance exclue non facturée
  son
  statut d'avant et retire l'absence déclarée par l'exclusion ; une séance `ABSENT` sans cette mémoire (absence réelle,
  exclusion antérieure) ne se réinclut pas (422). **Changer le forfait** impose à la séance validée/signée non facturée
  un
  forfait du centre (surcharge, RG-FAC-021). Accès et centre : comme RG-FAC-022. `ABSENT` est un statut à part entière
  des
  séances (RG-SEA-001) : la séance exclue reste consultable, n'est plus facturable, n'est plus « réalisée » (RG-ABS-012)
  et n'accepte pas de volet médical. *Source :* `SeanceDeFacturation`, `ExclureUneSeance`, `ReinclureUneSeance`,
  `ChangerLeForfaitDUneSeance` → `SimulationControleur` ; ACL `AbsencesDesPatientsLegacy` (ADR 0031).
- **RG-FAC-024** — Le tableau de bord de facturation d'un mois (`GET /api/v1/facturation/dashboard`) présente : chiffre
  d'affaires HT et TTC, séances facturées, patients facturés, factures créées, ventilation par caisse d'assurance et par
  état du patient. Les montants sont les **totaux enregistrés sur les factures** émises dans le mois (date de
  facturation, bornes incluses), jamais recalculés. Les « séances facturées » comptent les séances au statut `FACTUREE`
  dont la date tombe dans le mois, alors que les ventilations comptent les séances rattachées aux factures du mois : les
  deux nombres peuvent différer. Le mois est obligatoire. Accès : `ADMIN`, `MEDECIN`, `SECRETAIRE` (le médecin seul est
  refusé) ; le **centre demandé doit être celui de la session** (403 sinon). *Source :* `ConsulterLeTableauDeBord` →
  `TableauDeBordControleur` (ADR 0034).
- **RG-FAC-025** — La **synthèse mensuelle de facturation** s'imprime sur une période (début et fin incluses, aucun
  plafond de durée) en PDF, Excel ou HTML (PDF embarqué). Deux voies coexistent : `POST /api/v1/facturation/synthese/print`
  (tableau forfait x caisse, une colonne HT et TTC par caisse, indicateurs, barres de répartition par caisse et par
  situation du patient) et `POST /api/v1/documents/print` avec le type `SYNTHESE_FACTURATION_MENSUELLE` (gabarit à
  quatre colonnes CNAS, CASNOS, CAMSSP, Autres). Les **montants globaux** (HT, TVA, TTC) sont la somme des totaux
  enregistrés des factures de la période (RG-FAC-024), y compris les factures sans ligne ; dans le tableau, le TTC d'une
  ligne est sa part du TTC enregistré de la facture. Sans facture dans la période, le document est vide. Les deux voies exigent
  le même profil (`ADMIN` ou `SECRETAIRE` ; `MEDECIN` seul refusé) et le **centre de la session** (403 sinon). *Source :*
  `ImprimerLaSynthese`, `SyntheseControleur`, `FacturationSyntheseMensuelleReportService` (rendu, plateforme), `DocumentRestController`.
- **RG-FAC-026** — La **part financière du pilotage de la Direction** (vue d'ensemble, répartition par caisse, absences)
  lit la Facturation par son langage publié `SyntheseFinanciereDesCentres` : chiffre d'affaires HT et TTC (totaux enregistrés),
  nombre de factures, **encaissé** (somme signée des règlements de toutes dates des factures émises dans la période),
  chiffre d'affaires par mois et HT par caisse (agence figée sur la facture). *Source :* `DirectionDashboardQueryService`,
  `DirectionBreakdownQueryService`, `DirectionAbsencesQueryService` (plateforme).

## 12.4 Validation de la facturation

- **RG-FAC-030** — La **validation** (`POST /api/v1/facturation/validate`, `ADMIN` ; le centre du corps doit être celui
  de
  la session, sinon 403) émet de façon **atomique** les factures de la simulation de la période (RG-FAC-022) : une
  facture par groupe, **regroupement choisi par le corps de la requête** (jamais par les réglages du centre). Chaque
  facture reçoit le **numéro suivant** de la séquence du centre pour l' **année du jour** (RG-FAC-010) ; la réservation
  est **atomique** (deux validations simultanées d'un centre se mettent en file : jamais le même numéro). Elle **fige**
  le
  patient (code, nom, état, numéro d'immatriculation), le centre payeur et l'agence, la période, la date de facturation
  (jour de validation), le taux de TVA **en vigueur à la fin de la période facturée**, les forfaits, prix et quantités,
  et **l'utilisateur qui a validé** ; elle marque chaque séance `FACTUREE` rattachée à sa facture. Refus : **409** si le
  numéro réservé est déjà celui d'une facture du centre (séquence en retard sur les factures existantes ; rien n'est
  émis, la séquence est restituée) ; **422** si une séance éligible n'a **aucun forfait** (ni surcharge, ni prise en
  charge) : « N séances éligibles n'ont pas de forfait défini », aucune facture n'est créée ; **422** si une séance a
  changé entre-temps (« Une ou plusieurs seances ne sont plus eligibles »). Sans séance éligible : 0 facture, la
  séquence ne bouge pas. Une deuxième facture pour la même période et le même patient reste possible (séance validée
  après l'émission). *Source :* `Facture`, `LigneFacture`, `ValiderLaFacturation`, `SequenceDeFacturation`,
  `SeancesSansForfait`, `NumeroDeFactureDejaPris` (contexte `facturation`, ADR 0032) → `ValidationControleur` ;
  ACL `LiaisonDesSeancesJdbc`.
- **RG-FAC-031** — Une facture a au moins une ligne et couvre au moins une séance ; ligne : prix unitaire HT valide
  (jamais négatif) et nombre de séances > 0 ; totaux : HT = somme des lignes, **TVA arrondie (HALF_UP) sur le total HT
  de
  la facture** (pas ligne par ligne), TTC = HT + TVA, à deux décimales ; période valide. Une facture émise ne se modifie
  plus (le prix, le libellé du forfait, le patient et le taux restent ceux de l'émission). *Source :* `Facture`,
  `LigneFacture` (contexte `facturation`).
- **RG-FAC-032** — Après validation, un évènement « facturation validée » (`FacturationValideeEvent`, version 3, ADR 0036) annonce
  ce qui a été émis — centre, **période réellement facturée**, **nombre de factures émises**, nombre de séances
  facturées, **total TTC exact**, instant de la validation ; il ne liste plus les factures (taille bornée) — et
  déclenche, après le commit, la génération des écritures de ventes (RG-CPT-001), qui relit en base les factures de la
  période encore sans écriture (idempotent). **Aucun évènement** quand aucune facture n'est émise ; la reconstitution des ventes manquantes passe par
  `POST /api/v1/comptabilite/rebuild`. L'existence d'une facture couvrant une date **clôt les absences** du patient à
  cette date (RG-ABS-024) ; les séances facturées deviennent immuables (RG-SEA-002). *Source :*
  `FacturationValideeEvent` (langage publié du contexte `facturation`) → `FacturationValideeComptabiliteListener`.

## 12.4 bis Clôture mensuelle de facturation

- **RG-FAC-040** — **Clôture d'un mois** (`POST /api/v1/facturation/periodes/{mois}/cloturer`, mois `AAAA-MM`, `ADMIN`) : un
  mois par centre, **sans aucune précondition** (l'administrateur décide, y compris un mois futur ou sans séance). La réponse
  porte l'**avertissement chiffré** `seancesEligiblesRestantes` : nombre de séances du centre dans ce mois encore éligibles à la
  facturation (statut `VALIDEE` ou `SIGNEE`, non facturées, non exclues). Le centre du corps (facultatif) ou de la requête doit
  être celui de la session (403 sinon) ; l'auteur est l'utilisateur de la session. Clôturer deux fois le même mois est refusé
  (409 `PERIODE_DEJA_CLOTUREE`, la première clôture reste). Mois illisible : 400 `MOIS_INVALIDE`. *Source :* `MoisDeFacturation`,
  `CloturerLeMois` (contexte `facturation`, ADR 0037).
- **RG-FAC-041** — **Effets d'un mois clos** : sont refusés (422 `PERIODE_DE_FACTURATION_CLOTUREE`, message avec le mois) la
  **validation** de la facturation dont la période touche au moins un mois clos du centre (tout mois couvert par l'intervalle,
  même d'un seul jour), et l'**exclusion**, la **réinclusion** et le **changement de forfait** d'une séance dont la date est
  dans un mois clos (la période demandée n'y change rien). Restent permis : la simulation (`POST /preview`), toutes les
  lectures, les règlements (RG-REG-001) et la comptabilisation des factures déjà émises. Les autres mois et les autres centres ne
  sont pas touchés. Aucun mois n'est clos par défaut. *Source :* `GardeDesMoisClos` (appelée par `ValiderLaFacturation`,
  `ExclureUneSeance`, `ReinclureUneSeance`, `ChangerLeForfaitDUneSeance`).
- **RG-FAC-042** — **Clôture et émission se sérialisent** pour un même (centre, mois) : la ligne du mois est verrouillée
  (`SELECT ... FOR UPDATE`) jusqu'à la fin de la transaction de la clôture, de la réouverture, de la validation et de chaque
  modification de la simulation. Une validation en cours fait attendre la clôture (qui compte alors des séances restantes
  exactes) ; une clôture en cours fait attendre la validation, qui est ensuite refusée (RG-FAC-041). Les mois d'un intervalle
  se verrouillent du plus ancien au plus récent. *Source :* `MoisDeFacturationJdbc.verrouiller`.
- **RG-FAC-043** — **Réouverture** (`POST /api/v1/facturation/periodes/{mois}/rouvrir`, `ADMIN`) : le **motif est
  obligatoire** (400 `MOTIF_DE_REOUVERTURE_OBLIGATOIRE` ; 500 caractères au plus), l'auteur est l'utilisateur de la session ;
  rouvrir un mois qui n'est pas clos est refusé (409 `PERIODE_NON_CLOTUREE`). Un mois rouvert peut être clos à nouveau.
  *Source :* `RouvrirLeMois`.
- **RG-FAC-044** — La réouverture est refusée (422 `PERIODE_COMPTABLE_CLOTUREE`) si la **période comptable du même mois et du
  même centre est clôturée** (RG-CPT-005) : la Facturation lit cet état par le langage publié `PeriodesComptables` de la
  Comptabilité ; on ne rouvre pas la facturation d'un mois dont la comptabilité est close. *Source :* `ClotureComptable`
  (ACL `ClotureComptableComptabilite`).
- **RG-FAC-045** — **Traçabilité et lecture** : chaque clôture (auteur, instant, séances éligibles restantes) et chaque
  réouverture (auteur, instant, motif) est ajoutée à un **journal append-only** (`periodes_facturation_journal`) et publiée
  par un évènement d'intégration (`PeriodeDeFacturationCloturee`, `PeriodeDeFacturationRouverte`, jamais publié si l'acte est
  refusé ; aucun consommateur pour l'instant). `GET /api/v1/facturation/periodes?annee=AAAA` (`ADMIN`, 2000 à 2100, 400
  `ANNEE_INVALIDE` sinon) donne l'état des 12 mois du centre : ouvert ou clôturé, dernière clôture (par, le), dernière
  réouverture (par, le, motif). *Source :* `PeriodesDeFacturationControleur`, `ConsulterLesPeriodes`.

## 12.5 Règlements des factures

- **RG-REG-001** — Un règlement est rattaché à une facture et à un centre : montant **non nul** (un montant négatif est
  un **remboursement**, voir RG-REG-005), date (le jour d'exécution si elle est absente, jamais contrôlée), **auteur =
  l'utilisateur de la session** (« system » s'il est inconnu ; le `userId` du corps est ignoré), code `REG-` + 8
  caractères généré, montant arrondi au centime. Le centre du corps ou de la requête doit être celui de la session (403
  sinon, en écriture comme en lecture). Accès : `ADMIN`, `SECRETAIRE`. Un règlement enregistré ne se modifie ni ne
  s'annule : le seul moyen de corriger est un remboursement.
- **RG-REG-002** — **Situation d'une facture** = lecture calculée de la **somme signée** des règlements contre le total
  TTC figé : `NON_REGLEE` (somme nulle, reste = total), `PARTIELLEMENT_REGLEE` (reste > 0), `REGLEE` (soldée ;
  si le réglé dépasse le facturé, la situation est « trop-perçu » et le montant excédentaire est affiché : le
  **trop-perçu est accepté**). Les montants sont arrondis au centime.
- **RG-REG-005** — **Remboursement** (à valider par le métier) : un règlement négatif ne peut pas dépasser le **total déjà
  réglé** de la facture (somme signée, trop-perçu compris) : refus **422** `REMBOURSEMENT_SUPERIEUR_AU_TOTAL_REGLE`. Il est
  comptabilisé par l'écriture de **sens inverse** de l'encaissement (débit du compte client, crédit de la banque ou de la
  caisse), libellée « Remboursement … » (décision du 2026-10-21).
- **RG-REG-008** — **Export CSV des règlements** : une cellule de texte (numéro de facture, assurance, nom, prénom, caisse,
  agence, centre payeur) qui commence par `=`, `+`, `-`, `@`, une tabulation ou un retour chariot est précédée d'une
  apostrophe `'`, pour qu'un tableur ne l'exécute pas comme une formule ; les montants ne sont pas modifiés.
- **Auteur** (réglages de facturation, types de TVA, règlements) : l'utilisateur connecté ; l'identité envoyée dans la requête
  est ignorée.
- **RG-REG-006** — **Mode de règlement et référence de paiement** (à valider par le métier) : un règlement peut porter un
  mode (`BANQUE`, `CHEQUE`, `CAISSE`, `ESPECES`) et une référence libre (≤ 120 caractères). `CAISSE` et `ESPECES` sont
  comptabilisés au journal de caisse ; `BANQUE`, `CHEQUE` et l'absence de mode (anciens règlements, reprise de données) au
  journal de banque `BQ`. Un mode inconnu est refusé (400).
- **RG-REG-007** — **Clé d'idempotence** (à valider par le métier) : le corps peut porter une clé (`cleIdempotence`,
  ≤ 120 caractères) ; un renvoi pour la même facture et le même centre avec la même clé ne crée ni règlement, ni
  événement, ni écriture et répond la situation courante de la facture. Sans clé, un renvoi crée un second règlement.
- **RG-REG-003** — La recherche et le tableau de bord des règlements portent sur une **année** (2000 à 3000) et
  facultativement un **mois** (1 à 12), une caisse, une agence ou un centre payeur ; les listes sont
  paginées (page ≥ 0, taille > 0) et exportables en Excel ou PDF.
- **RG-REG-004** — Chaque règlement enregistré déclenche, après son enregistrement, la génération d'une écriture de
  trésorerie (RG-CPT-002) par l'événement `FactureReglementRecordedEvent` (langage publié du contexte `facturation`,
  porteur de l'identifiant du règlement, du montant signé et du mode) → `ReglementEncaisseComptabiliteListener`. Un mois
  comptable clos n'empêche pas le règlement : l'écriture est alors à régénérer (`POST /comptabilite/rebuild`).

## 12.6 Comptabilité (plan SCF)

- **RG-CPT-001** — **Écriture de ventes** par facture, dans le journal que le centre a choisi pour les ventes (`VE` par
  défaut, RG-CPT-010) : débit du compte client (411xxx), crédit du compte
  de ventes (706) pour le HT, crédit de la TVA collectée (44571) si la TVA est > 0 (dérivée de TTC − HT si le total de
  TVA manque) ; date de l'écriture = **fin de la période facturée** (jamais la date d'émission) ; axes analytiques
  centre et type de tiers ; statut `VALIDEE`. **Idempotence** : une facture n'a qu'une écriture (recherche par pièce
  source, **quel que soit le journal** : changer le journal des ventes ne recrée jamais une écriture déjà passée) ; la
  base le garantit aussi : **une seule écriture par source et par centre** (index unique, migration V37 ; le numéro de
  pièce, lui, n'est pas contraint). L'écriture est générée **après l'enregistrement de la validation de facturation**,
  facture par facture, chacune indépendamment : un refus (mois clôturé, facture déséquilibrée…) n'annule jamais la
  facturation, n'empêche pas les factures suivantes, et laisse la facture **à régénérer** (RG-CPT-024). *Source :*
  `ComptabiliserLesVentesEtReglements`, `EcritureComptable.generer`, `RedactionDesEcritures.ventes` (contexte
  `comptabilite`), écouteur `FacturationValideeComptabiliteListener` → langage publié
  `ComptabilisationDesVentesEtReglements`.
- **RG-CPT-002** — **Écriture de règlement** : journal choisi pour les règlements en banque (`BQ` par défaut, débit
    512) ou en caisse (`CA` par défaut, débit 530, si le mode
  est « caisse » ou « espèces ») ; crédit du compte client ; **un remboursement** (règlement négatif) est l'écriture de
  sens inverse (débit du compte client, crédit de la trésorerie) pour la valeur absolue du montant ; **une écriture par
         règlement** (idempotence par identifiant de règlement, garantie aussi en base comme RG-CPT-001). Sans mode
         (anciens règlements), le règlement est comptabilisé en « banque » (RG-REG-006). Comme la vente, l'écriture est générée **après l'enregistrement du
         règlement**, dans sa propre transaction : un refus n'annule jamais le règlement, qui reste à régénérer
         (RG-CPT-024).
- **RG-CPT-003** — Toute écriture comporte **au moins deux lignes** et doit être **équilibrée** (total débit = total
  crédit à deux décimales : `ECRITURE_DESEQUILIBREE`) ; compte SCF obligatoire, débit et
  crédit non négatifs ; numéro de pièce par journal et par année, qui **suit le plus grand numéro existant comparé
  numériquement** (après `VE-2026-999999` vient `VE-2026-1000000`, un suffixe non numérique est ignoré), attribué sous
  verrou. Toute écriture naît dans un **mois ouvert** (RG-CPT-005) : la règle est portée par l'écriture elle-même, quel
  que soit le chemin (vente, règlement, stock, pièce saisie, extourne).
- **RG-CPT-004** — **Statuts** : `BROUILLON` → `VALIDEE` → `EXPORTEE`. Une écriture exportée est **immuable**
  (`ECRITURE_EXPORTEE_IMMUABLE`) ; seules les écritures validées sont exportables (`EXPORT_ECRITURE_NON_VALIDEE`) ;
  l'export marque les écritures validées comme exportées. Écriture inconnue : `ECRITURE_INTROUVABLE`.
- **RG-CPT-005** — **Clôture de période** (mois) : réservée à `ADMIN` ; une période déjà clôturée ne se reclôt pas
  (`PERIODE_DEJA_CLOTUREE`) ; aucune écriture ne peut être générée dans une période clôturée (`PERIODE_CLOTUREE`).
  Lorsque la période d'une facture ou d'un règlement est clôturée, la facture ou le règlement reste enregistré mais
  **aucune écriture n'est générée** : le refus est journalisé et la source figure parmi les factures à régénérer
  (RG-CPT-024).
- **RG-CPT-006** — **Plan de comptes par centre** (`MappingComptable`), **entièrement paramétrable** depuis l'écran
  « Paramétrage comptable » (`ADMIN`) : comptes ventes, client « patient qui paie lui-même », client « payeur sans
  compte propre », banque, caisse — tous obligatoires ; valeurs par défaut :
  706, 411100, 411500, 512, 530, 44571 (TVA collectée) ; comptes du stock (RG-CPT-011). Le compte client d'un payeur se
  règle payeur par payeur (RG-CPT-019) : il n'existe plus de compte
  figé par type de payeur (CNAS, CASNOS, mutuelle). Chaque compte se **choisit dans le plan comptable du centre**
  (RG-CPT-018) ; un compte qui n'y figure pas, ou désactivé, est refusé (`COMPTE_INCONNU`). Le paramétrage ne vaut que
  pour les écritures générées
  ensuite : les écritures déjà passées ne sont pas réimputées. La **TVA collectée** est facultative à l'enregistrement
  (vide : la réponse la montre vide, mais toute relecture et toute écriture de ventes lisent `44571`). Les comptes du
  stock et
  les journaux non précisés gardent leur valeur actuelle (aucun retour aux valeurs par défaut hors renvoi explicite) ;
  **tout
  le mapping est revalidé à chaque enregistrement**, journaux d'abord (`JOURNAL_INCONNU`) puis comptes
  (`COMPTE_INCONNU`).
  Un numéro de compte du mapping s'écrit en **majuscules** (un numéro en minuscules est refusé). *Source :*
  `MappingComptable`,
  `ComptesDuStock`, `ModifierLeMapping` (contexte `comptabilite`), `ComptabiliteParametrageComponent` →
  `ComptabiliteParametrageStore` → `ComptabiliteApiService` → `MappingControleur` (`/mapping`) →
  `MappingComptableRepository`. **Règles de TVA comptables** (`/regles-tva`, `ADMIN`) versionnées dans le temps : type
  de prestation (texte brut, 120 caractères), **taux de 0 à 100 %** (arrondi à deux décimales par la base), exonération
  (obligatoire), début de validité obligatoire, fin facultative, texte de référence. L'écran comptable les **ajoute** ;
  la modification et la désactivation passent par l'écran « Types de TVA » (RG-FAC-003). La liste montre toutes les
  règles du centre, de la plus récente à la plus ancienne. Un ajout est **refusé (400)** si le taux dépasse 100 %, si la
  date de fin précède la date de début, ou si la règle **chevauche une règle active du même centre et du même type**
  (périodes [début, fin] bornes incluses, fin absente = période ouverte ; les règles désactivées sont ignorées) : c'est
  la règle unique de RG-FAC-001 (ADR 0030) ; les règles déjà enregistrées ne sont pas revalidées. Un ajout est visible
  aussitôt dans les deux écrans (la liste n'est plus mise en cache). La règle applicable à une date est la dernière
  commencée parmi les règles actives du type. *Source :* `RegleTva`, `EnregistrerUneRegleDeTva` →
  `ReglesTvaControleur` (`/regles-tva`) → `RegleTvaRepository`.
- **RG-CPT-007** — **Reconstruction** (rejeu) des écritures de factures et règlements existants sur une période,
  réservée à `ADMIN` ; l'idempotence évite tout doublon. Une vente régénérée est datée de la **fin de sa période
  facturée** (comme RG-CPT-001). Chaque source est traitée indépendamment : un refus (mois clôturé…) **n'arrête pas les
  suivantes** ; il est rapporté avec sa raison (RG-CPT-024). Consultation des écritures et des journaux :
  `ADMIN`, `SECRETAIRE` (paginée) ; la liste des écritures est **relue à chaque ouverture de l'écran Comptabilité**
  (les écritures générées entre-temps — stock, ventes, règlements — apparaissent sans recharger la page) ; validation,
  export, paramétrage (comptes, journaux, mapping), règles TVA, clôture, comptabilisation du stock : `ADMIN`. *Source :*
  `ComptabiliserLesVentesEtReglements.reconstruire` → `VentesEtReglementsControleur` (`/rebuild`) ;
  `ComptabiliteDashboardComponent` → `ComptabiliteStore.refresh`.
- **RG-CPT-008** — *(point d'attention levé)* Les créances ne sont plus toutes imputées au compte client « autre » :
  chaque facture débite le compte de **son payeur** (RG-CPT-019), le compte par défaut si le payeur n'en a pas, et le
  compte « patients » si la facture n'a pas de payeur. Le **règlement** crédite le compte que l'écriture de la facture
  a débité, même si le compte du payeur a changé entre-temps (à défaut d'écriture de facture, le compte actuel du
  payeur).
- **RG-CPT-009** — **Journaux paramétrables par centre** : chaque centre définit librement ses journaux (code de 1 à 10
  lettres majuscules ou chiffres, libellé de 100 caractères au plus, actif ou non). Tant qu'il n'a rien paramétré, il
  dispose des journaux par défaut `VE` Ventes, `BQ` Banque, `CA` Caisse, `AC` Achats, `ST` Stocks ; ils sont
  enregistrés comme les siens à sa première modification. Un journal **choisi pour une opération** ne peut être ni
  désactivé ni supprimé (`JOURNAL_UTILISE`) ; un journal **qui porte des écritures** se désactive mais ne se supprime
  pas (`JOURNAL_AVEC_ECRITURES`) ; supprimer un journal inconnu est refusé (`JOURNAL_INTROUVABLE`). La liste est
  paginée ; l'indicateur « actif » est obligatoire (400 sinon). *Source :* `Journal`, `CodeJournal`,
  `EnregistrerUnJournal`, `SupprimerUnJournal` (contexte `comptabilite`), `ComptabiliteParametrageComponent` →
  `ComptabiliteParametrageStore` →
  `ComptabiliteApiService` → `JournauxControleur` (`/journaux`) → `JournalRepository`.
- **RG-CPT-010** — **Journal de chaque opération** : le centre choisit le journal des ventes, des règlements en banque,
  des règlements en caisse, des réceptions de stock, des sorties de stock et des écarts d'inventaire (par défaut `VE`,
  `BQ`, `CA`, `AC`, `ST`, `ST`). Seul un journal **existant et actif** peut être choisi (`JOURNAL_INCONNU`). Le numéro
  de pièce reprend le code du journal : `<JOURNAL>-<année>-<n° sur 6 chiffres>`, numéroté par journal et par année.
- **RG-CPT-011** — **Comptes du stock** (inventaire permanent), paramétrables par centre : stock (322 par défaut),
  consommation (602), factures non parvenues (408), boni d'inventaire (757), mali d'inventaire (657). La **fiche d'un
  article** peut préciser son propre compte de stock et son propre compte de consommation (RG-STK-008) ; à défaut, ceux
  du centre s'appliquent.
- **RG-CPT-012** — **Écriture de réception** : un bon de réception **validé** donne une écriture, dans le journal des
  réceptions, à la date de réception : débit du compte de stock (de l'article ou du centre), crédit des **factures non
  parvenues**, pour la valeur **hors taxe** quantité × prix unitaire. La TVA d'achat et le compte fournisseur se
  traitent à la facture, **hors application**. Une écriture par bon, jamais doublée.
- **RG-CPT-013** — **Écriture des sorties** : les sorties de stock d'une journée (consommations de séance, sorties
  manuelles) sont **centralisées en une écriture par centre et par jour**, dans le journal des sorties : débit du
  compte de consommation, crédit du compte de stock, valorisés au **prix moyen pondéré appliqué à la sortie** (à
  défaut le PMP après mouvement, puis le PMP courant de l'article ; ordre de valorisation : RG-STK-041). Une ligne par
  compte. Un article sorti **sans prix ni PMP** (valeur nulle) ou reçu sans prix n'est pas valorisé : la journée ou le
  bon est **signalé** avec les articles concernés (`SANS_VALEUR`, RG-CPT-015).
- **RG-CPT-014** — **Écriture des écarts d'inventaire** : un inventaire **clôturé** donne une écriture, dans le journal
  des inventaires, à la date de l'inventaire : excédent (compté > théorique) = débit stock, crédit boni ; manquant =
  débit mali, crédit stock ; écarts valorisés au PMP de la ligne d'inventaire. Une ligne non comptée ne produit rien.
- **RG-CPT-015** — **Comptabilisation du stock rejouable** : elle a lieu **chaque nuit** sur les 35 derniers jours
  (`ComptabiliteStockScheduler`) et **à la demande** de l'administrateur sur une période d'un an au plus. Seule la
  **différence** entre ce qui devrait être comptabilisé et ce qui l'est déjà est écrite : la rejouer ne crée aucun
  doublon. Quand les sorties d'un jour changent après coup (mouvement saisi en retard, PMP recalculé), l'écriture du
  jour est **mise à jour** tant qu'elle n'est ni exportée ni dans une période clôturée (son numéro de pièce est
  conservé) ; sinon la différence fait l'objet d'une **écriture de complément** — datée du jour même, ou du jour du
  traitement si la période d'origine est clôturée (au plus 50 compléments par journée). Une réception ou un inventaire
  dont la période est **clôturée** n'est pas comptabilisé : il est compté comme « ignoré ». Le résultat affiché à
  l'écran détaille **chaque pièce non comptabilisée** — nature (réception, inventaire, journée de sorties), référence
  (numéro du bon ou de l'inventaire, date de la journée) et raison : mois clôturé (`PERIODE_CLOTUREE`), articles sans
  valeur cités par leur code (`SANS_VALEUR`), plafond de compléments atteint (`COMPLEMENTS_MAX`) ; la tâche de nuit les
  journalise. Le stock d'un centre n'est jamais comptabilisé dans un autre. *Source :* `ComptabiliserLeStock`,
  `SoldesDuStock`, `EcritureComptable.generer/reviserLesSorties` (contexte `comptabilite`),
  `ComptabiliteParametrageComponent` → `ComptabiliteParametrageStore` → `ComptabiliteApiService` →
  `ComptabilisationDuStockControleur` (`/stock/synchroniser`), `ComptabilisationDuStockPlanifiee` (nuit) →
  `OperationsDeStock` (`OperationsDeStockJdbc`).
- **RG-CPT-016** — **Isolement par centre de la comptabilité** : toute requête comptable (écritures, export, clôture,
  paramétrage, journaux, comptabilisation du stock, reconstruction) est confrontée au centre de la session ; demander
  un autre centre est refusé (403). *Source :* contrôleurs du contexte `comptabilite` (annexe 16), `SessionCourante`.
- **Point d'attention (RG-CPT-017)** — Le stock n'est pas comptabilisé à chaque mouvement mais par traitement (nuit ou
  demande) : une sortie du jour n'apparaît en comptabilité qu'après le traitement suivant. La TVA déductible sur achats
  et la dette fournisseur (401) ne sont pas gérées automatiquement : le compte des factures non parvenues se solde par
  une pièce saisie (RG-CPT-021), à la comptabilisation de la facture du fournisseur.
- **RG-CPT-018** — **Plan comptable par centre** : chaque centre tient la liste de ses comptes (numéro de 1 à 20
  lettres ou chiffres, libellé de 150 caractères au plus, actif ou non), paginée et recherchable par numéro ou
  libellé. Tant qu'il n'a rien paramétré, il dispose d'un **plan SCF de départ** (stock, fournisseurs, clients,
  personnel, TVA, trésorerie, charges, produits), complété des comptes que son paramétrage utilise déjà ; ce plan est
  enregistré comme le sien à sa première modification. Partout où un compte est demandé (paramétrage, payeur, modèle
  de pièce), il **se choisit dans ce plan**. Un compte **utilisé** par le paramétrage, un payeur ou un modèle de pièce
  ne peut être ni désactivé ni supprimé (`COMPTE_UTILISE`) ; un compte **qui porte des écritures** se désactive mais ne
  se supprime pas (`COMPTE_AVEC_ECRITURES`) ; supprimer un compte inconnu est refusé (`COMPTE_INTROUVABLE`). Ajouter
  un compte ne demande aucun développement. Le numéro est enregistré **en majuscules** (comme le code d'un journal) ; un
  compte
  rattaché à un **article** compte comme utilisé. *Source :* `PlanComptable`, `CompteComptable`, `EnregistrerUnCompte`,
  `SupprimerUnCompte`, `PlanComptableComponent` →
  `PlanComptableStore` → `ComptabiliteApiService` → `PlanComptableControleur` (`/comptes`) →
  `CompteRepository`.
- **RG-CPT-019** — **Compte client de chaque payeur** : chaque payeur du référentiel (caisse, mutuelle, entreprise…)
  peut porter son propre compte client, choisi dans le plan comptable (`COMPTE_INCONNU` sinon) ; ses factures y sont
  débitées. Un payeur sans compte propre utilise le compte client par défaut du centre ; vider le compte d'un payeur
  l'y ramène. Un payeur d'un autre centre est introuvable (`PAYEUR_INTROUVABLE`). **Un nouveau client ne demande aucun
  développement** : on crée le payeur dans les référentiels, on ajoute son compte au plan, on le lui affecte. La liste
  des payeurs est paginée et recherchable. *Source :* `CompteClientDuPayeur`, `AffecterUnCompteAuPayeur`,
  `PlanComptableComponent` →
  `PlanComptableStore` → `ComptabiliteApiService` → `ComptesPayeursControleur` (`/payeurs`) →
  `CompteClientDuPayeurRepository`.
- **RG-CPT-020** — **Modèles de pièces** : un centre définit ses propres types de pièces (loyer, salaires, facture
  fournisseur, opération diverse…). Un modèle porte un code unique dans le centre (1 à 20 lettres majuscules,
  chiffres, tirets ; `MODELE_CODE_EXISTANT`), un libellé, un **journal actif** (`JOURNAL_INCONNU` sinon) et de 2 à 20
  **lignes** — un sens (débit ou crédit), un compte du plan et un libellé facultatif chacune — dont au moins une au
  débit et une au crédit (`MODELE_LIGNES_INVALIDES`). Un modèle désactivé ne sert plus à saisir (`MODELE_INACTIF`) ;
  le supprimer conserve les pièces déjà saisies ; un modèle inconnu est refusé (`MODELE_INTROUVABLE`). Le journal d'un
  modèle ne peut être ni désactivé ni supprimé (`JOURNAL_UTILISE`). **Un nouveau type de pièce ne demande aucun
  développement.** Liste paginée ; gestion réservée à `ADMIN`. *Source :* `ModeleDePiece`, `GererLesModelesEtPieces`,
  `ModelesPieceComponent` → `PiecesComptablesStore` → `ComptabiliteApiService` →
  `ModelesEtPiecesControleur` (`/modeles`) → `ModeleDePieceRepository`.
- **RG-CPT-021** — **Saisie d'une pièce** (`ADMIN`, `SECRETAIRE`) : on choisit un modèle actif, une date, un libellé (à
  défaut celui du modèle) et **un montant par ligne** ; une ligne à zéro n'est pas écrite. La pièce devient une
  écriture **validée** dans le journal du modèle, numérotée comme les autres (RG-CPT-010). Elle est refusée si un
  montant est négatif, s'il n'y a pas un montant par ligne ou s'il reste moins de deux lignes
  (`PIECE_MONTANTS_INVALIDES`), si elle n'est pas équilibrée (`ECRITURE_DESEQUILIBREE`), si sa période est clôturée
  (`PERIODE_CLOTUREE`) ou si un de ses comptes a quitté le plan (`COMPTE_INCONNU`). *Source :*
  `ModeleDePiece.composerLaPiece`, `EcritureComptable.saisir`, `GererLesModelesEtPieces.saisir`,
  `SaisiePieceComponent` → `PiecesComptablesStore` → `ComptabiliteApiService` → `ModelesEtPiecesControleur`
  (`/pieces`) → `EcritureComptableRepository`.
- **RG-CPT-022** — **Extourne d'une pièce saisie** (`ADMIN`) : une écriture ne se supprime jamais ; une pièce saisie
  par erreur s'annule par une **écriture inverse** (débits et crédits échangés), datée du jour, dans le même journal,
  libellée « Extourne <n° de pièce> ». Une pièce ne s'extourne **qu'une fois** et une extourne ne s'extourne pas
  (`PIECE_DEJA_EXTOURNEE`) ; une écriture générée par le système (vente, règlement, stock) ne s'extourne pas à la
  main (`PIECE_NON_SAISIE`) ; la période du jour doit être ouverte (`PERIODE_CLOTUREE`). *Source :*
  `EcritureComptable.exigerExtournable/extourner`, `GererLesModelesEtPieces.extourner`,
  `ComptabiliteDashboardComponent` → `PiecesComptablesStore` → `ComptabiliteApiService` → `ModelesEtPiecesControleur`
  (`/pieces/{id}/extourne`).
- **Point d'attention (RG-CPT-023)** — Les comptes propres à un article (RG-STK-008) se saisissent encore librement
  sur la fiche article : ils ne sont pas contrôlés contre le plan comptable du centre.
- **RG-CPT-024** — **Factures sans écriture et régénération** (`ADMIN`, écran « Paramétrage comptable », carte
  « Comptabiliser les ventes et les règlements ») : pour une période choisie, l'écran liste les **factures émises qui
  n'ont aucune écriture de ventes** (numéro, date d'émission, fin de période, total TTC) ; « Régénérer les écritures »
  demande une confirmation puis crée les écritures manquantes des factures émises et des règlements encaissés de la
  période (RG-CPT-007), sans jamais modifier une écriture existante ; l'écran affiche alors le nombre de factures et de
  règlements comptabilisés, **chaque source refusée avec sa raison** (mois clôturé `PERIODE_CLOTUREE`, facture
  déséquilibrée `ECRITURE_DESEQUILIBREE`, `ERREUR_TECHNIQUE`), puis relit la liste des factures encore sans écriture.
  Les refus de la comptabilisation du stock (RG-CPT-015) s'affichent de la même façon. *Source :*
  `ComptabiliserLesVentesEtReglements.facturesSansEcriture/reconstruire` → `VentesEtReglementsControleur`
  (`/ventes/non-comptabilisees`, `/rebuild`) ; `ComptabiliteParametrageComponent` → `ComptabiliteParametrageStore`
  (`chercherVentesNonComptabilisees`, `reconstruireVentes`).
- **RG-CPT-025** — **Les soldes d'ouverture ne sont pas comptabilisés par la plateforme.** Les factures de reprise
  (numéro commençant par `REPRISE-`, créées par l'import « soldes d'ouverture ») et leurs règlements de reprise (auteur
  `REPRISE`) sont exclus de la liste des factures sans écriture, de la régénération des écritures (`/rebuild`) et de la
  comptabilisation automatique d'une période : ces soldes figurent déjà dans la comptabilité de l'ancien logiciel. Un
  encaissement saisi ensuite dans la plateforme sur une facture de reprise reste un règlement ordinaire. *Source :*
  `FacturesEmisesJdbc`, `ReglementsEncaissesJdbc` (contexte `facturation`, ports publiés par la Comptabilité) ; décision
  du 2026-10-21 (KB-FAC-67).
