# 12 — Facturation, TVA, règlements et comptabilité

> Préfixes `RG-FAC` (facturation et TVA), `RG-REG` (règlements), `RG-CPT` (comptabilité). Le système **n'accepte à la
facturation que des séances validées et couvertes**, **fige** prix, TVA et
> payeur sur chaque facture, **calcule le solde** de chaque facture et **génère les écritures comptables équilibrées**.
> Sources : `FacturationDomainService`,
> `SeanceEligibleForFacturationSpecification`, `FactureAggregate`, `FactureNumberTemplate`, `TypeTVA`,
> `RegleTva`, `GererLesTypesDeTva`, `ParametresDeFacturation`, `ModeleDeNumerotation`, `SeanceFacturationJdbcAdapter`,
> `FactureReglementAggregate`,
> `FactureReglementStatusSpecification`, `ReglementService`, `ComptabiliteService`, `GenerateurEcritureComptable`,
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
  moment de la validation. *Source :* langage publié `ParametrageFiscal` (contexte `comptabilite`) ; la simulation et
  la validation de facturation lisent encore la table par `TypeTvaJdbcAdapter` jusqu'aux slices F2 et F3.
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
  (`/facturation/settings`) ; numéro calculé par `FactureNumberTemplate`.
- **RG-FAC-011** — Option « **regroupement multi-forfait** » (défaut oui) : une facture par patient et par période
  (plusieurs lignes de forfaits) ; sinon une facture par patient **et par forfait**. Un centre qui n'a rien enregistré
  lit les valeurs par défaut, sans date de mise à jour : **la lecture n'écrit rien**. Modification des paramètres :
  `ADMIN` ; lecture : `ADMIN`, `SECRETAIRE` (le profil `MEDECIN`, admis par la route, est refusé par le filtre des
  profils : 403 `ROLE_SCOPE`). Le centre demandé est confronté à celui de la session (403 sinon). *Source :*
  `ConfigurerLaFacturation` → `ParametresFacturationControleur`.

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
  est « caisse ») ; crédit du compte client ; montant en valeur absolue ; **une écriture par
         règlement** (idempotence par identifiant de règlement, garantie aussi en base comme RG-CPT-001). Le mode par
         défaut
         du règlement enregistré est « banque ». Comme la vente, l'écriture est générée **après l'enregistrement du
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
