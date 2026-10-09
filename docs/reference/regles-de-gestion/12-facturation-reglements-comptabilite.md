# 12 — Facturation, TVA, règlements et comptabilité

> Préfixes `RG-FAC` (facturation et TVA), `RG-REG` (règlements), `RG-CPT` (comptabilité). Le système **n'accepte à la
facturation que des séances validées et couvertes**, **fige** prix, TVA et
> payeur sur chaque facture, **calcule le solde** de chaque facture et **génère les écritures comptables équilibrées**.
> Sources : `FacturationDomainService`,
> `SeanceEligibleForFacturationSpecification`, `FactureAggregate`, `FactureNumberTemplate`, `TypeTVA`,
> `TypeTvaDomainService`, `SeanceFacturationJdbcAdapter`, `FactureReglementAggregate`,
> `FactureReglementStatusSpecification`, `ReglementService`, `ComptabiliteService`, `GenerateurEcritureComptable`,
> `EcritureComptable`, `MappingComptable`, `RegleTVA`, listeners comptables,
> contrôleurs `Facturation*`, `TvaTypes*`, `Reglement*`, `Comptabilite*`.

## 12.1 Types de TVA

- **RG-FAC-001** — Un type de TVA est propre à un centre : libellé obligatoire, **taux de 0 à 100 %**, type de
  prestation obligatoire, indicateur d'exonération, **date de début de validité obligatoire**,
  date de fin facultative **strictement postérieure** au début (« La date de fin doit être postérieure à la date de
  début »), texte de référence légale facultatif.
- **RG-FAC-002** — La TVA applicable à une date est le type **actif** couvrant cette date pour la prestation
  `HEMODIALYSE` ; **sans type actif, le taux est 0 %**. Le taux est figé sur chaque facture au
  moment de la validation.
- **RG-FAC-003** — Les types de TVA se désactivent (jamais supprimés physiquement) ; création, modification,
  désactivation : `ADMIN` ; consultation : `ADMIN`, `MEDECIN`, `SECRETAIRE` (liste paginée,
  mise en cache par centre, vidée à chaque écriture).

## 12.2 Paramètres de facturation du centre

- **RG-FAC-010** — Chaque centre définit un **modèle de numérotation** des factures (défaut `FAC-{YEAR}-{SEQ}`) : jetons
  `{YYYY}`/`{YEAR}`, `{YY}`, `{CENTER}` (8 premiers caractères de l'identifiant du centre),
  `{SEQ}` ou `{SEQn}` (séquence sur n chiffres, 4 par défaut). Le modèle est obligatoire ; la séquence est strictement
  positive et propre au centre (et à l'année).
- **RG-FAC-011** — Option « **regroupement multi-forfait** » (défaut oui) : une facture par patient et par période
  (plusieurs lignes de forfaits) ; sinon une facture par patient **et par forfait**.
  Modification des paramètres : `ADMIN` ; lecture : `ADMIN`, `MEDECIN`, `SECRETAIRE`.

## 12.3 Séances facturables et simulation

- **RG-FAC-020** — Est **éligible** une séance du centre, dans la période, au statut `VALIDEE` ou `SIGNEE` (la signature
  médecin n'est pas exigée) et **pas déjà facturée**. La période est un mois entier ou
  deux dates (fin non antérieure au début).
- **RG-FAC-021** — **Forfait appliqué** à une séance : le forfait de surcharge posé sur la séance (RG-SEA-033) ; à
  défaut, le forfait de la prise en charge couvrant la date (effectif sinon demandé,
  de préférence la PEC dotée d'un forfait effectif, puis la plus récente) ; sans forfait, prix 0.
- **RG-FAC-022** — La **simulation** (aperçu) construit les factures par groupe patient (ou patient + forfait), regroupe
  les séances par forfait en lignes (prix unitaire HT × nombre de séances),
  calcule **HT, TVA, TTC** (arrondis à deux décimales, TVA = HT × taux) et le numéro prévisionnel. Elle n'enregistre
  rien. Accès : `ADMIN`, `MEDECIN`.
- **RG-FAC-023** — Depuis la simulation, on peut **exclure une séance** (statut `ABSENT`, détachée de toute facture ;
  uniquement une séance validée/signée non facturée) ou **changer son forfait** ;
  la simulation est recalculée. `ABSENT` est un statut à part entière des séances (RG-SEA-001) : la séance exclue reste
  consultable, n'est plus facturable, n'est plus « réalisée » (RG-ABS-012) et n'accepte pas de volet médical.
- **RG-FAC-024** — Le tableau de bord de facturation d'un mois présente : chiffre d'affaires HT et TTC, séances
  facturées, patients facturés, factures créées, ventilation par caisse d'assurance et par état
  du patient. Accès : `ADMIN`, `MEDECIN`, `SECRETAIRE`. Une synthèse imprimable est disponible (modèle de document,
  chapitre documents).

## 12.4 Validation de la facturation

- **RG-FAC-030** — La **validation** recalcule la simulation, puis, de façon atomique : crée **une facture par groupe**
  avec un numéro réel de séquence, **fige** le patient (code, nom, état, numéro
  d'immatriculation), le centre payeur et l'agence, la période, la date de facturation (jour de validation) et le taux
  de TVA ; marque chaque séance `FACTUREE` rattachée à sa facture. Si une
  séance a changé entre-temps, la validation entière échoue (« Une ou plusieurs seances ne sont plus eligibles »). Sans
  séance éligible : 0 facture créée. Accès : `ADMIN`, `MEDECIN`.
- **RG-FAC-031** — Une facture a au moins une ligne ; ligne : prix unitaire HT valide et nombre de séances > 0 ; totaux
  HT/TVA/TTC arrondis à deux décimales ; période valide.
- **RG-FAC-032** — Après validation, un évènement « facturation validée » déclenche la génération des écritures de
  ventes (RG-CPT-001) ; l'existence d'une facture couvrant une date **clôt les absences** du patient à cette date
  (RG-ABS-024) ; les séances facturées deviennent immuables (RG-SEA-002).

## 12.5 Règlements des factures

- **RG-REG-001** — Un règlement est rattaché à une facture et à un centre : montant **non nul** (un montant négatif est
  accepté et correspond à un remboursement ou à une correction), date obligatoire, auteur (« system » par défaut), code
  `REG-` + 8 caractères généré. Accès : `ADMIN`, `SECRETAIRE`.
- **RG-REG-002** — **Situation d'une facture** = comparaison du montant facturé et du total réglé : `NON_REGLEE` (rien
  de réglé, reste = total), `PARTIELLEMENT_REGLEE` (reste > 0), `REGLEE` (soldée ;
  si le réglé dépasse le facturé, la situation est « trop-perçu » et le montant excédentaire est affiché). Les montants
  sont arrondis au centime.
- **RG-REG-003** — La recherche et le tableau de bord des règlements portent sur une **année** (2000 à 3000) et
  facultativement un **mois** (1 à 12), une caisse, une agence ou un centre payeur ; les listes sont
  paginées (page ≥ 0, taille > 0) et exportables en Excel ou PDF.
- **RG-REG-004** — Chaque règlement enregistré déclenche la génération d'une écriture de trésorerie (RG-CPT-002).

## 12.6 Comptabilité (plan SCF)

- **RG-CPT-001** — **Écriture de ventes** par facture, dans le journal que le centre a choisi pour les ventes (`VE` par
  défaut, RG-CPT-010) : débit du compte client (411xxx), crédit du compte
  de ventes (706) pour le HT, crédit de la TVA collectée (44571) si la TVA est > 0 (dérivée de TTC − HT si le total de
  TVA manque) ; date de l'écriture = **fin de la période facturée** (jamais la date d'émission) ; axes analytiques
  centre et type de tiers ; statut `VALIDEE`. **Idempotence** : une facture n'a qu'une écriture (recherche par pièce
  source, **quel que soit le journal** : changer le journal des ventes ne recrée jamais une écriture déjà passée).
- **RG-CPT-002** — **Écriture de règlement** : journal choisi pour les règlements en banque (`BQ` par défaut, débit
    512) ou en caisse (`CA` par défaut, débit 530, si le mode
  est « caisse ») ; crédit du compte client ; montant en valeur absolue ; **une écriture par
  règlement** (idempotence par identifiant de règlement). Le mode par défaut du règlement enregistré est « banque ».
- **RG-CPT-003** — Toute écriture comporte **au moins deux lignes** et doit être **équilibrée** (total débit = total
  crédit à deux décimales : `ECRITURE_DESEQUILIBREE`) ; compte SCF obligatoire, débit et
  crédit non négatifs ; numéro de pièce par journal et par année.
- **RG-CPT-004** — **Statuts** : `BROUILLON` → `VALIDEE` → `EXPORTEE`. Une écriture exportée est **immuable**
  (`ECRITURE_EXPORTEE_IMMUABLE`) ; seules les écritures validées sont exportables (`EXPORT_ECRITURE_NON_VALIDEE`) ;
  l'export marque les écritures validées comme exportées. Écriture inconnue : `ECRITURE_INTROUVABLE`.
- **RG-CPT-005** — **Clôture de période** (mois) : réservée à `ADMIN` ; une période déjà clôturée ne se reclôt pas
  (`PERIODE_DEJA_CLOTUREE`) ; aucune écriture ne peut être générée dans une période clôturée (`PERIODE_CLOTUREE`).
  Lorsque la période d'un règlement est clôturée, le règlement reste enregistré mais **aucune écriture n'est générée**
  (avertissement journalisé).
- **RG-CPT-006** — **Plan de comptes par centre** (`MappingComptable`), **entièrement paramétrable** depuis l'écran
  « Paramétrage comptable » (`ADMIN`) : comptes ventes, clients (patient, CNAS, CASNOS,
  mutuelle, autre), banque, caisse — tous obligatoires ; valeurs par défaut :
  706, 411100, 411200, 411300, 411400, 411500, 512, 530, 44571 (TVA collectée, facultative) ; comptes du stock
  (RG-CPT-011). Un compte comporte 1 à 20 lettres ou chiffres. Le paramétrage ne vaut que pour les écritures générées
  ensuite : les écritures déjà passées ne sont pas réimputées. **Règles de TVA comptables** versionnées dans le temps
  (type de prestation, taux ≥ 0, exonération, début de validité obligatoire). *Source :* `MappingComptable`,
  `ComptabiliteParametrageComponent` → `ComptabiliteParametrageStore` → `ComptabiliteApiService` →
  `ComptabiliteRestController` (`/mapping`) → `ComptabiliteUseCase` → `MappingComptablePort`.
- **RG-CPT-007** — **Reconstruction** (rejeu) des écritures de factures et règlements existants sur une période,
  réservée à `ADMIN` ; l'idempotence évite tout doublon. Consultation des écritures et des journaux :
  `ADMIN`, `SECRETAIRE` (paginée) ; validation, export, paramétrage (comptes, journaux), règles TVA, clôture,
  comptabilisation du stock : `ADMIN`.
- **Point d'attention (RG-CPT-008)** — Les écritures sont aujourd'hui générées avec le type de tiers `AUTRE` : toutes
  les créances sont imputées au compte client « autre » (411500) quel que soit le payeur ;
  les comptes CNAS, CASNOS, mutuelle et patient du plan de comptes ne sont pas encore alimentés automatiquement.
- **RG-CPT-009** — **Journaux paramétrables par centre** : chaque centre définit librement ses journaux (code de 1 à 10
  lettres majuscules ou chiffres, libellé de 100 caractères au plus, actif ou non). Tant qu'il n'a rien paramétré, il
  dispose des journaux par défaut `VE` Ventes, `BQ` Banque, `CA` Caisse, `AC` Achats, `ST` Stocks ; ils sont
  enregistrés comme les siens à sa première modification. Un journal **choisi pour une opération** ne peut être ni
  désactivé ni supprimé (`JOURNAL_UTILISE`) ; un journal **qui porte des écritures** se désactive mais ne se supprime
  pas (`JOURNAL_AVEC_ECRITURES`) ; supprimer un journal inconnu est refusé (`JOURNAL_INTROUVABLE`). La liste est
  paginée. *Source :* `JournauxService`, `ComptabiliteParametrageComponent` → `ComptabiliteParametrageStore` →
  `ComptabiliteApiService` → `ComptabiliteRestController` (`/journaux`) → `JournauxUseCase` → `JournalRepositoryPort`.
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
  défaut le PMP après mouvement, puis le PMP courant de l'article). Une ligne par compte.
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
  dont la période est **clôturée** n'est pas comptabilisé : il est compté comme « ignoré » et signalé à l'écran. Le
  stock d'un centre n'est jamais comptabilisé dans un autre. *Source :* `ComptabiliteStockService`,
  `GenerateurEcritureStock`, `ComptabiliteParametrageComponent` → `ComptabiliteParametrageStore` →
  `ComptabiliteApiService` → `ComptabiliteRestController` (`/stock/synchroniser`) → `ComptabiliteStockUseCase` →
  `OperationsStockPort` (`OperationsStockJdbcAdapter`).
- **RG-CPT-016** — **Isolement par centre de la comptabilité** : toute requête comptable (écritures, export, clôture,
  paramétrage, journaux, comptabilisation du stock, reconstruction) est confrontée au centre de la session ; demander
  un autre centre est refusé (403). *Source :* `ComptabiliteRestController`, `CenterAccessGuard`.
- **Point d'attention (RG-CPT-017)** — Le stock n'est pas comptabilisé à chaque mouvement mais par traitement (nuit ou
  demande) : une sortie du jour n'apparaît en comptabilité qu'après le traitement suivant. La TVA déductible sur achats
  et la dette fournisseur (401) ne sont pas gérées : le compte des factures non parvenues se solde hors application, à
  la comptabilisation de la facture du fournisseur.
