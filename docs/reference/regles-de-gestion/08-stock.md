# 08 — Stock : articles, fournisseurs, bons, lots, PMP, inventaire, alertes, groupes d'articles

> Préfixe `RG-STK`. Le système **valorise** le stock (PMP recalculé en cascade), **choisit les lots à sortir** (FEFO),
> **gèle** les mouvements pendant un
> inventaire, **surveille** péremptions, seuils et ruptures, et **trace** chaque lot jusqu'au patient. Sources :
> `Article`, `Lot`, `BonCommande`,
> `BonReception`, `BonSortie`, `BonReceptionService`, `BonSortieService`, `BonCommandeService`, `PmpCalculator`,
> `PmpEngine`,
> `PmpRecalculationCoordinator`, `ValorisationStockCalculator`, `Inventaire*`, `InventoryGuardedMovementRepository`,
> `StockDashboardService`,
> `StockDashboardAdapter`, `GroupeArticle*`, `ExpirationAlertScheduler`, contrôleurs `Stock*`, `Bon*`, `Inventaire*`,
> `GroupeArticle*`.

## 8.1 Référentiel (articles, fournisseurs, emplacements)

- **RG-STK-001** — Un article porte : code, libellé, unité (tous trois obligatoires), seuil d'alerte, stock courant, PMP
  courant, indicateur « géré par lot »
  et « actif », et un éventuel **type de traitement de l'anémie** (`EPO` ou `FER_INJECTABLE`, vide = article ordinaire)
  qui alimente les listes de
  prescription (voir RG-MED).
- **RG-STK-002** — Un article inactif ne peut plus être sorti du stock (« Article inactif »). Une consommation doit être
  strictement positive et ne peut dépasser le stock (« Stock insuffisant pour l'article »).
- **RG-STK-003** — Un fournisseur exige une raison sociale ; un emplacement exige un libellé. Les fournisseurs et
  emplacements sont propres au centre.
- **RG-STK-004** — Création d'articles, de fournisseurs et d'emplacements : `ADMIN`, `PHARMACIEN`. Lecture des
  articles : `ADMIN`, `PHARMACIEN`, `INFIRMIER`, `MEDECIN` ;
  des fournisseurs et emplacements : `ADMIN`, `PHARMACIEN`, `INFIRMIER`.
- **RG-STK-005** — Les références de pièces sont générées par séquence de centre : `BC` (bon de commande), `BR`
  (réception), `BS` (sortie), `INV` (inventaire).

## 8.2 Bons de commande

- **RG-STK-010** — Un bon de commande naît en `BROUILLON` avec un fournisseur et des lignes (article, quantité, prix
  unitaire) ; ses lignes se remplacent tant qu'il est
  brouillon. Le total est la somme des sous-totaux de lignes (montants non négatifs, RG-TRV-030).
- **RG-STK-011** — Validation : uniquement depuis `BROUILLON` (« Seul un bon BROUILLON peut etre valide ») et avec au
  moins une ligne (« doit contenir au moins une ligne ») ; statut
  `VALIDE`. Il passe à `RECU` quand un bon de réception issu de lui est validé.
- **RG-STK-012** — Accès : écriture `ADMIN`, `PHARMACIEN` ; lecture + `INFIRMIER`. Un bon inconnu du centre est
  introuvable.

## 8.3 Bons de réception, lots, entrées

- **RG-STK-020** — Un bon de réception (`BROUILLON` → `VALIDE`) peut être créé librement ou **depuis un bon de
  commande** (lignes reprises, date du jour, fournisseur du bon).
  Il exige au moins une ligne à la validation ; seul un bon brouillon se valide.
- **RG-STK-021** — À la validation, pour chaque ligne : article obligatoire et existant du centre, quantité > 0, un
  **lot** est créé (quantité initiale = restante, PMP = prix
  d'entrée) et un mouvement `ENTREE` daté de la **date de réception** est enregistré ; le PMP de chaque article touché
  est recalculé en cascade ; le bon de commande lié passe
  `RECU` ; le centre est notifié (mouvement de stock).
- **RG-STK-022** — Article **géré par lot** : numéro de lot **et** date de péremption obligatoires. Article non géré par
  lot : numéro de lot facultatif, sinon généré (`AUTO-<référence du bon>-<rang>`).
- **RG-STK-023** — **Correction d'un bon déjà validé** : la date des mouvements d'entrée suit toujours la date du bon ;
  le nombre de lignes ne peut pas changer, l'article
  d'une ligne ne peut pas être modifié, la nouvelle quantité ne peut être inférieure à ce qui est déjà consommé sur le
  lot ; le lot et le mouvement sont mis à jour puis le PMP
  est recalculé sur tout l'historique.
- **RG-STK-024** — Un article dont le PMP est en cours de recalcul est **verrouillé** : toute saisie le concernant est
  refusée (« Recalcul en cours … Saisie temporairement bloquee »).
- **RG-STK-025** — Accès : écriture `ADMIN`, `PHARMACIEN` ; lecture + `INFIRMIER`.

## 8.4 Bons de sortie, FEFO, consommation en séance

- **RG-STK-030** — Un bon de sortie (BS) peut être rattaché à une séance et un patient (poste, date) ; il exige au moins
  une ligne ; le lot choisi doit appartenir à l'article ; la
  quantité demandée doit être disponible sur le lot (« Stock insuffisant sur le lot »).
- **RG-STK-031** — **FEFO** : pour une sortie automatique (consommation de séance), les lots disponibles sont consommés
  par date de péremption croissante jusqu'à satisfaire la
  quantité ; le manque éventuel est signalé (« Stock insuffisant … manque »). Chaque sortie est valorisée au **PMP
  courant** de l'article.
- **RG-STK-032** — Les sorties liées à une séance sont immuables si la séance est **facturée**
  (`SEANCE_BILLED_STOCK_EXIT_IMMUTABLE`) et leur date ne peut être changée (`SEANCE_STOCK_EXIT_DATE_IMMUTABLE`).
  Corriger un bon de sortie restitue d'abord les quantités aux lots d'origine puis recrée les sorties.
- **RG-STK-033** — Accès : `ADMIN`, `PHARMACIEN`, `INFIRMIER`. La liste des lots disponibles d'un article est triée
  FEFO.

## 8.5 PMP (prix moyen pondéré) et valorisation

- **RG-STK-040** — Entrée : nouvelle quantité = quantité + q ; nouvelle valeur = valeur + q × prix ; **PMP = valeur /
  quantité** (4 décimales, arrondi demi-supérieur). Sortie :
  valeur diminuée au PMP courant, PMP inchangé ; si le stock tombe à 0 ou moins, valeur remise à 0 et dernier PMP
  conservé. Ajustement avec prix : traité comme une entrée ; sans prix :
  comme une sortie. Mouvement d'inventaire : traité comme une entrée.
- **RG-STK-041** — Toute réception ou correction d'une pièce passée déclenche un **recalcul en cascade** de l'historique
  de l'article (ordre chronologique) : le PMP après chaque
  mouvement et la valorisation de chaque sortie sont réécrits, ainsi que le PMP et la quantité courants de l'article. Le
  recalcul massif est un traitement asynchrone suivi par
  un identifiant de tâche et un verrou par article (réservé `ADMIN`, `PHARMACIEN` pour le lancer ; consultation +
  `INFIRMIER`).
- **RG-STK-042** — L'écran « explication du PMP » détaille, mouvement par mouvement, l'état avant/après et la formule
  appliquée.
- **RG-STK-043** — **Valorisation historique** d'une période : valeur de début, entrées (valorisées au prix), sorties
  (valorisées au PMP qui précède), « autres variations » (inventaires, ajustements sans prix, arrondis = fin − début −
  entrées + sorties) et valeur de fin ; elle est mise en cache par centre, période et ensemble d'articles (TTL
  `CACHE_TTL_STOCK_VALORISATION`, vidée à chaque écriture de mouvement).
- **RG-STK-044** — Les statistiques du tableau de bord stock portent sur 7, 30 ou 90 jours (30 par défaut) : quantité et
  valeur totales, tendance journalière des mouvements (jours sans mouvement comblés) et « top articles » (10 par défaut,
  50 maximum) triés par valeur ou volume.

## 8.6 Inventaire

- **RG-STK-050** — Un seul inventaire peut être **en cours** par centre (`INVENTORY_ALREADY_IN_PROGRESS`). La date est
  obligatoire (`INVENTORY_DATE_REQUIRED`), non future (`INVENTORY_DATE_IN_FUTURE`), **postérieure au dernier inventaire
  clôturé** (`INVENTORY_DATE_BEFORE_LAST`) et au moins égale à la date du dernier mouvement
  (`INVENTORY_MOVEMENTS_AFTER_DATE`). Ouverture impossible pendant un recalcul de PMP (`INVENTORY_RECALC_RUNNING`).
- **RG-STK-051** — À l'ouverture, le **stock théorique est figé** : une ligne par lot non épuisé de chaque article actif
  (triée par article puis FEFO) ; un article en stock sans
  lot (données anciennes) obtient une ligne sans lot.
- **RG-STK-052** — **Tant qu'un inventaire est en cours, aucun mouvement de stock n'est permis** dans le centre
  (`STOCK_INVENTORY_IN_PROGRESS`), ni création, modification ou suppression.
- **RG-STK-053** — Comptage : quantité ≥ 0 avec **3 décimales au plus** (`INVENTORY_QUANTITY_SCALE`) ; ligne
  introuvable : `INVENTORY_LINE_NOT_FOUND` ; inventaire introuvable :
  `INVENTORY_NOT_FOUND` ; inventaire clôturé/annulé : `INVENTORY_NOT_IN_PROGRESS`. Un **lot trouvé en rayon** mais
  inconnu s'ajoute (théorique 0, numéro de lot obligatoire pour un
  article géré par lot : `INVENTORY_LOT_REQUIRED`, article inconnu : `INVENTORY_ARTICLE_NOT_FOUND`, doublon :
  `INVENTORY_LINE_DUPLICATE`) ; seule une ligne ajoutée se retire (`INVENTORY_LINE_NOT_REMOVABLE` — pour un lot absent,
  on compte 0).
- **RG-STK-054** — **Import d'une feuille de comptage** (Excel) : lignes rapprochées par identifiant caché, sinon par
  code article + n° de lot ; anomalies listées sans bloquer le
  reste (article absent, plusieurs lots possibles, ligne déjà renseignée, quantité invalide) ; une cellule vide laisse
  la ligne inchangée ; le motif de l'écran est conservé si le
  fichier n'en porte pas. La **feuille de comptage** Excel est celle téléchargée pour l'inventaire : une feuille d'un
  autre inventaire est refusée (`INVENTORY_SHEET_OTHER_INVENTORY`), une feuille sans les
  colonnes attendues aussi (`INVENTORY_SHEET_INVALID`) ; mêmes contrôles de fichier que RG-REF-010. « Reporter le
  théorique » reprend la quantité théorique des lignes non comptées.
- **RG-STK-055** — **Clôture** : toutes les lignes doivent être comptées (`INVENTORY_NOT_COMPLETE`, compter 0 pour un
  lot absent) et **chaque écart justifié par un motif**
  (`INVENTORY_GAP_WITHOUT_REASON`). Alors, de façon atomique : les quantités comptées deviennent le stock des lots (lots
  trouvés créés ; `INVENTORY_LOT_NOT_FOUND` si un lot a disparu) ;
  tous les mouvements jusqu'à la date d'inventaire (fin de journée UTC) sont **clôturés** ; un mouvement `INVENTAIRE`
  par lot compté pose le stock de départ au PMP de l'inventaire ;
  quantités et PMP de tous les articles sont recalculés à partir de ce seul stock de départ.
- **RG-STK-056** — **Période clôturée** : aucun mouvement ne peut être daté au plus tard le jour du dernier inventaire
  (`STOCK_PERIOD_CLOSED`) ; un mouvement clôturé ne peut plus être
  modifié ni supprimé (`STOCK_MOVEMENT_CLOSED`).
- **RG-STK-057** — Annulation : possible tant que l'inventaire est en cours ; il n'a alors aucun effet sur le stock.
  Procès-verbal imprimé via le modèle de document `INVENTAIRE_STOCK`
  (filigrane « PROVISOIRE » tant que l'inventaire est en cours). Lecture/comptage : `ADMIN`, `PHARMACIEN`, `INFIRMIER` ;
  ouverture, report, clôture, annulation : `ADMIN`, `PHARMACIEN`.
  Chaque changement d'état notifie le centre.

## 8.7 Alertes et traçabilité

- **RG-STK-060** — Alertes de stock du centre : **péremption** (lot encore en stock dont la date de péremption est dans
  les 30 jours ou déjà dépassée), **rupture** (article actif dont le
  stock est ≤ 0) et **seuil** (stock > 0 mais ≤ seuil d'alerte). Consultation : `ADMIN`, `PHARMACIEN`, `INFIRMIER`.
- **RG-STK-061** — Contrôle quotidien à 07:00 (`ExpirationAlertScheduler.scanExpirations`) : pour chaque centre, les
  lots contenant du stock et expirant dans 30 jours sont **consignés au
  journal technique**. **Point d'attention :** ce contrôle ne produit aujourd'hui ni notification temps réel ni
  message ; les alertes visibles sont celles du tableau de bord (RG-STK-060). Il
  parcourt aussi les centres inactifs.
- **RG-STK-062** — **Traçabilité par lot** : de l'entrée (bon de réception, fournisseur) aux sorties (séances, patients)
  pour un numéro de lot donné (`ADMIN`, `PHARMACIEN`).

## 8.8 Groupes d'articles (kits de consommables)

- **RG-STK-070** — Un groupe regroupe de 1 à **500** articles du centre ; nom obligatoire (100 caractères maximum,
  espaces superflus réduits), description facultative (500 caractères maximum).
- **RG-STK-071** — Le nom est unique par centre **sans tenir compte de la casse, des accents ni des espaces multiples**
  (« Kit CNAS » = « KIT CNAS ») : `GROUPE_ARTICLE_NOM_EXISTANT`.
- **RG-STK-072** — Un groupe inconnu du centre est introuvable (`GROUPE_ARTICLE_INTROUVABLE`). Un article d'un autre
  centre est refusé (`GROUPE_ARTICLE_ARTICLE_INCONNU`). La gestion des groupes est réservée à `ADMIN`.
- **RG-STK-073** — La valorisation d'un groupe sur une période est restituée à la direction sans donnée nominative (voir
  RG-DIR).
