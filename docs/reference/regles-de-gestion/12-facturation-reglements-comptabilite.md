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

- **RG-CPT-001** — **Écriture de ventes (journal VE)** par facture : débit du compte client (411xxx), crédit du compte
  de ventes (706) pour le HT, crédit de la TVA collectée (44571) si la TVA est > 0 (dérivée de TTC − HT si le total de
  TVA manque) ; date de l'écriture = **fin de la période facturée** (jamais la date d'émission) ; axes analytiques
  centre et type de tiers ; statut `VALIDEE`. **Idempotence** : une facture n'a qu'une écriture (recherche par pièce
  source).
- **RG-CPT-002** — **Écriture de règlement** : journal banque `BQ` (débit 512) ou caisse `CA` (débit 530, si le mode
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
- **RG-CPT-006** — **Plan de comptes par centre** (`MappingComptable`) : comptes ventes, clients (patient, CNAS, CASNOS,
  mutuelle, autre), banque, caisse — tous obligatoires ; valeurs par défaut :
  706, 411100, 411200, 411300, 411400, 411500, 512, 530, 44571. **Règles de TVA comptables** versionnées dans le temps
  (type de prestation, taux ≥ 0, exonération, début de validité obligatoire).
- **RG-CPT-007** — **Reconstruction** (rejeu) des écritures de factures et règlements existants sur une période,
  réservée à `ADMIN` ; l'idempotence évite tout doublon. Consultation des écritures :
  `ADMIN`, `SECRETAIRE` (paginée) ; validation, export, mapping, règles TVA, clôture : `ADMIN`.
- **Point d'attention (RG-CPT-008)** — Les écritures sont aujourd'hui générées avec le type de tiers `AUTRE` : toutes
  les créances sont imputées au compte client « autre » (411500) quel que soit le payeur ;
  les comptes CNAS, CASNOS, mutuelle et patient du plan de comptes ne sont pas encore alimentés automatiquement.
