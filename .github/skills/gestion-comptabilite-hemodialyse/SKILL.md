---
name: gestion-comptabilite-hemodialyse
description: Règles métier et architecture pour le module comptabilité (génération d'écritures comptables SCF à destination de systèmes tiers, à partir de la facturation et des règlements). À utiliser dès qu'une tâche touche à l'entité EcritureComptable, au mapping plan comptable, à la génération automatique d'écritures depuis un événement de facturation/règlement, au lettrage tiers, à la clôture de période, ou à l'export vers un logiciel comptable externe.
---

# Gestion de la comptabilité (génération d'écritures SCF)

## Objectif métier

Ce module ne fait pas de la comptabilité — il **traduit** les événements métier déjà produits par les modules
facturation et règlements en écritures comptables conformes au Système Comptable Financier (SCF) algérien, puis les rend
disponibles à l'export vers un système comptable tiers (logiciel de l'expert-comptable, Sage, etc.). Il ne remplace
jamais un logiciel de comptabilité générale — il en est un fournisseur de données fiables.

Respecte les contraintes archi déjà définies dans `AGENTS.md` (hexagonal + DDD, isolation multi-centre par `centerId`,
tests unitaires + intégration obligatoires des deux côtés, pas d'API dépréciées). Ce skill ne répète pas ces règles, il
documente le domaine métier propre à la comptabilité.

## Principe non négociable : traçabilité SCF

Le SCF impose la comptabilité d'engagement et l'indépendance des exercices : une écriture rattachée à une période
clôturée ne peut plus être modifiée, et une correction se fait toujours par extourne/contre-passation, jamais par
modification ou suppression. Ce principe doit être appliqué dès la conception de l'agrégat, pas ajouté après coup en
validation applicative.

## Concepts du domaine

### 1. Agrégat `EcritureComptable`

Champs : `id`, `journalCode` (ex. `VE` ventes, `BQ` banque, `CA` caisse), `dateEcriture`, `datePiece`, `numeroPiece`
(séquentiel par journal, sans trou, jamais réutilisé), `libelle`, `centerId`, `statut` (`BROUILLON` / `VALIDEE` /
`EXPORTEE`), collection de `LigneEcriture`.

`LigneEcriture` : `compteSCF`, `libelleLigne`, `montantDebit`, `montantCredit`, `tiersId` (optionnel, alimente le
lettrage).

Invariants à porter dans le constructeur/factory de l'agrégat, pas en validation applicative :

- Σdébit = Σcrédit sur l'ensemble des lignes, sinon l'écriture ne peut pas exister.
- Une écriture au statut `EXPORTEE` est immuable — toute correction passe par une nouvelle écriture d'extourne.
- Une écriture ne peut être créée sur une période déjà clôturée.

### 2. Mapping comptable configurable

Value object `MappingComptable` : table de correspondance entre concepts métier et comptes SCF, **configurable par
`centerId`** (pas codée en dur dans le domaine), pour absorber d'éventuelles variations de plan comptable entre centres.
Comptes de référence à mapper au minimum :

- Créance tiers payeur : **décision actée — un compte collectif 411 distinct par type de tiers payeur** (ex. 411100
  patient direct, 411200 CNAS, 411300 CASNOS, 411400 mutuelle — numérotation indicative, à caler sur votre plan
  comptable réel), **avec lettrage systématique par `tiersId` à l'intérieur de chaque compte** pour le suivi individuel.
  Le mapping doit donc résoudre le compte 411 applicable à partir du type de tiers payeur de la ligne (donnée déjà
  disponible sur la facture), pas d'un seul compte générique.
- Produit de prestation (70x, éventuellement subdivisé par type de forfait pour du suivi analytique)
- Trésorerie (512 banque, 53 caisse)
- Charges de consommables (60x) en contrepartie de sortie de stock (3x), si le module pousse jusqu'à la valorisation des
  charges

Toute modification du mapping doit être tracée (qui, quand, ancien/nouveau) — un mapping erroné fausse silencieusement
toute la comptabilité générée en aval.

### 3. Génération pilotée par événements de domaine

Le générateur d'écritures est un **listener**, jamais un service appelé manuellement depuis la facturation ou les
règlements — ça élimine le risque d'oubli et garde une traçabilité claire "quel événement métier a produit quelle
écriture".

Événements déclencheurs identifiés à ce stade (liste à valider/compléter avec toi) :

- `FactureEmise` → écriture journal ventes : une ligne de crédit produit (70x), une ou plusieurs lignes de débit tiers
  (411) — **une facture peut porter plusieurs tiers payeurs sur une même séance** (part CNAS + part patient selon le
  forfait), donc ne pas supposer 1 facture = 1 tiers dans le modèle.
- `FactureAnnulee` / avoir émis → écriture d'extourne symétrique, jamais suppression de l'écriture d'origine.
- `ReglementEncaisse` → écriture journal banque ou caisse selon le mode de règlement, avec `tiersId` porté sur la ligne
  pour permettre le lettrage ultérieur avec la facture correspondante.
- **Hors périmètre v1** : la sortie de stock liée à une séance (écritures de charge 60x/3x au coût PMP) n'est pas
  traitée dans cette version. Le module se limite à `FactureEmise`/`FactureAnnulee` et `ReglementEncaisse`. Prévoir cet
  événement dans le modèle (event listener additionnel) sans l'implémenter, pour ne pas fermer la porte à une v2 — mais
  ne pas le développer maintenant.

Règle d'idempotence à implémenter : un événement rejoué (bug, retry, double publication) ne doit jamais générer une
seconde écriture pour la même opération source — même logique que l'idempotence déjà posée sur le scan de séance.

### 4. Lettrage

Rapprochement entre une ligne de crédit "facture" et une ou plusieurs lignes de débit "règlement" portant le même
`tiersId`. Particulièrement utile pour le suivi des créances CNAS/CASNOS, qui peuvent avoir des délais de paiement longs
et donner lieu à des règlements partiels ou groupés (un seul virement CNAS peut solder plusieurs factures).

### 5. Clôture de période

Opération explicite (`ClOturerPeriodeComptable`), typiquement mensuelle, par `centerId`. Après clôture :

- Plus aucune écriture ne peut être créée ou modifiée sur la période.
- Toute opération métier ultérieure référençant rétroactivement une période clôturée doit remonter une erreur explicite
  au module appelant (facturation/règlements), pas un échec silencieux.

### 6. Paramétrage fiscal (TVA)

Le traitement TVA des prestations de dialyse (exonération, taux, seuils) est une règle fiscale externe qui peut évoluer
indépendamment du code — le domaine ne doit jamais coder en dur un taux ou une exonération. Traiter ça comme une
**donnée de configuration versionnée dans le temps**, sur le même principe que le snapshot de forfait sur une séance :
ce qui a été appliqué à une facture passée ne doit jamais bouger rétroactivement si la règle change plus tard.

Value object `RegleTVA` (ou `ParametreFiscal`) :

- `typePrestation` (ou `codeTVA`) — la règle peut varier selon le type de prestation facturée, pas seulement une règle
  unique pour toute l'activité.
- `tauxApplique` (0 si exonéré, sinon le taux en vigueur).
- `exonere` (booléen explicite, plus lisible qu'un taux à 0 qu'on doit interpréter).
- `dateDebutValidite` / `dateFinValidite` (nullable si toujours en vigueur) — c'est cette période de validité qui permet
  l'historisation.
- `texteReference` (référence au texte réglementaire ou à la confirmation de l'expert-comptable qui justifie la règle) —
  utile le jour d'un contrôle fiscal, pour justifier pourquoi telle règle était appliquée à telle date.

Règles d'application :

- Au moment de la génération de l'écriture de facturation (événement `FactureEmise`), on résout la `RegleTVA` active **à
  la date de la facture**, jamais la règle courante au moment où le code tourne — sinon un recalcul ou un rejeu
  d'événement changerait le passé.
- La règle résolue est **figée sur l'écriture générée** (comme le forfait figé sur la séance), pas recalculée
  dynamiquement à chaque lecture.
- Modifier ou ajouter une `RegleTVA` (nouveau taux, nouvelle exonération, nouvelle date d'entrée en vigueur) doit être
  une opération d'administration simple, accessible sans déploiement de code — écran de configuration ou table pilotée,
  pas une constante dans le code. C'est le point qui vous permet de vous adapter rapidement si les règles changent, sans
  toucher au domaine ni redéployer.
- Toute création/modification de `RegleTVA` doit être tracée (qui, quand, ancienne/nouvelle valeur) — c'est une donnée
  sensible pour la conformité fiscale, au même titre que le changement de forfait sur une séance.
- Le mapping comptable (section 2) doit prévoir, en plus des comptes déjà listés, les comptes de TVA collectée le jour
  où une prestation sort du régime d'exonération — ne pas fermer cette porte dans le modèle même si elle n'est pas
  utilisée aujourd'hui.

Placement architectural : `RegleTVA` est une donnée gérée par un port `ParametrageFiscalPort` (lecture par date + type
de prestation), avec un adaptateur de persistance simple. Le générateur d'écritures (`GenerateurEcritureComptable`)
consomme ce port au moment de la génération, il ne connaît pas la logique métier de résolution de la règle applicable —
ça reste une responsabilité du port/du repository.

### 7. Export vers systèmes tiers

Le domaine ne connaît **aucun format d'export**. Le port sortant `ExportComptablePort` expose une opération type
"exporter un journal sur une période", implémenté par des adaptateurs interchangeables. **Cible v1 : format d'import
Sage 100** (adaptateur `Sage100ExportAdapter`).

Points à vérifier avant d'écrire l'adaptateur (spécifique à Sage 100, à confirmer sur la version exacte utilisée par
votre expert-comptable — le format d'import peut varier légèrement selon la version/l'édition) :

- Format de fichier attendu (CSV structuré à colonnes fixes le plus souvent, parfois avec un en-tête de type journal).
- Codification exacte attendue pour le journal, le n° de pièce, la date (format de date Sage a ses propres conventions),
  le compte, le libellé, débit/crédit (souvent deux colonnes séparées plutôt qu'un signe).
- Comment Sage 100 représente le lettrage à l'import (code lettrage par ligne) — important vu la décision de lettrer par
  `tiersId` plutôt que d'avoir un sous-compte par patient.
- Si Sage 100 attend une correspondance analytique en colonne dédiée (cf. section classe 9 ci-dessous) plutôt qu'un
  fichier séparé.

Comme pour tout adaptateur, cette logique de formatage reste entièrement dans l'infrastructure — le domaine produit un
`EcritureComptable` neutre, l'adaptateur `Sage100ExportAdapter` sait seul comment le sérialiser pour Sage 100. Ajouter
un second format d'export plus tard (autre outil, autre expert-comptable) ne doit jamais toucher au domaine ni aux use
cases de génération.

### 8. Comptabilité analytique (classe 9)

Décision actée : la classe 9 est gérée dès la v1, en complément de la classe 7 (produits) — chaque écriture de
facturation porte, en plus du compte SCF, un ou plusieurs **axes analytiques**, sans dupliquer le classement en comptes
distincts.

Axes analytiques pertinents pour votre activité (à valider/prioriser avec toi, liste indicative) :

- `centerId` — rentabilité par centre, l'axe le plus évident vu votre isolation multi-centre déjà posée partout
  ailleurs.
- Type de forfait — rentabilité par catégorie de forfait CNAS.
- Type de tiers payeur (CNAS / CASNOS / patient direct / mutuelle) — utile pour analyser le poids relatif de chaque
  financeur, indépendamment du suivi de créance déjà assuré par le lettrage 411.

Placement dans le modèle : `LigneEcriture` porte un ou plusieurs `AxeAnalytique` (value object `code` + `libelle`),
résolus au moment de la génération à partir du contexte de la facture (centre, forfait, tiers) — pas saisis
manuellement. Comme pour le mapping comptable (section 2), la définition des axes et leur code doit être configurable,
pas codée en dur, pour absorber une évolution future (ajout d'un axe, renommage) sans redéploiement.

Si le format Sage 100 attend l'analytique dans une colonne dédiée du même fichier plutôt que dans un flux séparé (point
à vérifier, cf. section export), l'adaptateur `Sage100ExportAdapter` doit projeter les `AxeAnalytique` de chaque ligne
dans le format attendu — encore une fois, ça reste un détail d'infrastructure, le domaine ne connaît que la notion
générique d'axe analytique.

## Placement architectural (hexagonal / DDD)

- **Domaine** : agrégat `EcritureComptable`, value object `MappingComptable`, service de génération pur
  `GenerateurEcritureComptable` (sans I/O).
- **Application** : use cases `GenererEcritureFacturation`, `GenererEcritureReglement`, `LettrerEcritures`,
  `ClOturerPeriodeComptable`, `ExporterJournal` — pas un `ComptabiliteService` fourre-tout.
- **Ports sortants** : `EcritureComptableRepositoryPort`, `MappingComptablePort` (lecture config par `centerId`),
  `ExportComptablePort`.
- **Adaptateurs** : listeners d'événements `FactureEmise`/`ReglementEncaisse` (internes, appel direct ou via la queue
  déjà utilisée ailleurs dans le projet), adaptateur de persistance, adaptateur (s) d'export.
- **Isolation multi-centre** : `centerId` porté par `EcritureComptable` dès la génération, mapping comptable résolu par
  centre, clôture de période effectuée centre par centre (un centre peut clôturer sans bloquer les autres).

## Décisions actées (v1)

1. **Périmètre** : facturation + règlements uniquement. Pas de valorisation stock (60x/3x) en v1 — prévu dans le modèle
   événementiel mais non implémenté.
2. **Export** : format d'import Sage 100, via `Sage100ExportAdapter`.
3. **Créances tiers (411)** : un compte collectif distinct par type de tiers payeur (patient direct / CNAS / CASNOS /
   mutuelle), pas de sous-compte par patient — suivi individuel assuré par le lettrage sur `tiersId` à l'intérieur de
   chaque compte.
4. **Analytique (classe 9)** : gérée dès la v1, via des `AxeAnalytique` portés par chaque `LigneEcriture` (centre,
   forfait, type de tiers), configurables et non codés en dur.
5. **TVA** : paramétrable dans l'application via `RegleTVA` (cf. section 6) — taux, exonération et date d'effet sont des
   données de configuration modifiables sans redéploiement, jamais des constantes codées en dur. Vous pourrez ajuster la
   règle le jour où les textes changent ; la valeur exacte à appliquer aujourd'hui (exonéré ou taux) reste une décision
   fiscale à confirmer avec votre expert-comptable, mais elle n'a aucun impact sur la conception — c'est une simple
   donnée à saisir dans l'écran de configuration.

## Points ouverts à valider avec toi

1. Spécification exacte du format d'import Sage 100 (structure de fichier, codification date/journal/pièce,
   représentation du lettrage et de l'analytique à l'import) — dépend de la version/édition utilisée par votre
   expert-comptable, à obtenir avant d'écrire `Sage100ExportAdapter`.
2. Liste et priorisation définitive des axes analytiques (centre / forfait / type de tiers, ou d'autres) — cf. section
   8.
