---
slice: stock-s7-inventaire
status: characterized
date: 2026-10-23
order: 8
context: stock
sources: [.ddd/tactical/stock.md (Inventaire, PeriodeDeStock), RG-STK-050 à 057, ADR 0009, 0024, 0045 (décisions 6 et 7)]
---

# S7 — Inventaire et période de stock (clôture)

## Cas d'usage

`OuvrirUnInventaire`, `ConsulterLEtat`, `ConsulterUnInventaire`, `ListerLesInventaires`, `Compter`, `AjouterUneLigne`,
`RetirerUneLigne`, `TelechargerLaFeuille`,
`ImporterUneFeuille`, `ReporterLeTheorique`, `CloturerLInventaire`, `AnnulerLInventaire`, `ImprimerLeRapport` (PV) ;
`PeriodeDeStock` devient le propriétaire des
règles centre-entier (verrou partagé pour chaque mouvement, exclusif à l'ouverture/clôture/annulation).

## Entrées legacy

`InventaireRestController` (220 l.) `/api/v1/stock/inventaires` : `GET /{id}/rapport`, `GET`, `GET /etat`, `POST`,
`GET /{id}`, `PUT /{id}/lignes/{ligneId}`,
`POST /{id}/lignes`, `DELETE /{id}/lignes/{ligneId}`, `POST /{id}/reporter-theorique`, `POST /{id}/cloturer`,
`POST /{id}/annuler`, `GET|POST /{id}/feuille-comptage` ;
`Inventaire` (228 l.), `LigneInventaire` (212 l.), `InventaireService` (362 l.), `InventaireApplicationService`,
`InventoryGuardedMovementRepository` (garde **sans
verrou**, l. 63-72), `WebSocketInventaireEventPublisher`, modèle de document `INVENTAIRE_STOCK`.

## Données

Tables `inventaires`, `inventaire_lignes` (V4), `lots`, `stock_movements` (clôture, mouvement `INVENTAIRE`), `articles`.
`PeriodeDeStock` : déduite de `inventaires`
(en cours / dernière clôture) avec verrou sur une ligne par centre — décider entre une petite table additive
(`periodes_de_stock`, comme `mois_absences`) et un verrou
consultatif PostgreSQL ; **recommandation : table additive** (portable H2/PG, testable).

## Écart décidé (ADR 0045 / modèle)

Garde d'inventaire **sous verrou** : une réception commencée avant l'ouverture se termine avant le gel (l'ouverture
attend), aucune écriture après le gel ;
inventaire refusé tant qu'une sortie pour séances n'est pas rapprochée (si S6b livrée).

## Filet (points d'argent § 11.3)

Goldens : ouverture (date requise, non future, > dernière clôture, ≥ dernier mouvement, recalcul en cours refusé, un
seul en cours), théorique figé (lot non épuisé
d'article actif, FEFO, ligne sans lot), comptage (≥ 0, 3 décimales, lot trouvé, doublon, ligne non retirable), import de
feuille (anomalies, feuille d'un autre
inventaire), report du théorique, clôture (incomplète, écart sans motif, lot disparu ; stock des lots = compté,
mouvements figés, `INVENTAIRE` au PMP, PMP recalculé),
annulation sans effet, mouvements refusés pendant l'inventaire et dans la période close, PV « PROVISOIRE ». Reprendre
`InventaireServiceTest`,
`InventaireIntegrationTest`, `InventaireStockReportTemplateTest`. Concurrence : ouverture pendant une réception ; deux
ouvertures simultanées.

## Bascule / retour arrière / retrait

Sans façade ; retour arrière = version précédente (la table additive reste, sans effet). Retrait : contrôleur,
`InventaireService`, `InventoryGuardedMovementRepository`.

## Dépendances et risques

Dépend de S5 (`RejeuDuPmp`, `StockArticle.poserStockInventorie`) et de S6. **Risque élevé** : transaction centre-entier,
argent (écarts valorisés), Comptabilité.

## Filet de caractérisation (2026-10-24) — phase 7a livrée

- **Test** : `InventaireCharacterizationIntegrationTest` (13 scénarios, 70 goldens dans
  `characterization/stock/referentiel/inventaire/`), rejoué à l'identique (déterministe) sur le legacy.
- **Couvre** : ouverture (théorique figé : lots non épuisés des articles actifs, FEFO, ligne sans lot ; date future,
  sans date, déjà en cours, avant la dernière clôture, avant le dernier mouvement), comptage (nominal, 3 / 4 décimales,
  négatif, absent, ligne inconnue, motif trop long), lignes ajoutées (lot requis, lot épuisé rattaché, doublon,
  retrait),
  report du théorique, clôture (incomplète, écart sans motif, nominale : lots, mouvements figés et `INVENTAIRE`,
  articles),
  annulation (sans effet, puis les mouvements reprennent), rôles et cloisonnement par centre, feuille Excel (aveugle ou
  avec
  théorique, import rempli avec anomalies, autre inventaire, format non Excel, fichier vide, classeur sans colonnes,
  inventaire annulé), PV PDF (en cours puis clôturé).
- **Non couvert** : recalcul en cours (verrou de recalcul) et concurrence (ouverture pendant une réception, deux
  ouvertures) — à écrire avec la construction (`PeriodeDeStock` sous verrou), comme S5 et S6.
- **Anomalies relevées** (`known-bugs.md`) : **KB-STK-50** (la clôture met à zéro le stock et le PMP d'un article
  inactif,
  écart avec ses lots — gravité élevée, à trancher avant la construction), KB-STK-51 (comptage négatif : code générique
  `BUSINESS_ERROR`), KB-STK-52 (matrice des rôles et cloisonnement à reproduire).
- **Reste (7b)** : domaine `Inventaire` / `PeriodeDeStock`, ports et adapters JDBC dans `contexts/stock`, contrôleur,
  import/export de feuille, PV, garde sous verrou, notifications ; puis retrait du legacy.

## Construction (7b) — avancement 2026-10-24

- **Décision KB-STK-50** : à la clôture, seuls les articles qui ont une ligne d'inventaire voient leurs mouvements figés
  et leur
  PMP rejoué ; un article inactif (hors théorique) garde son stock, son PMP et ses mouvements (écart décidé par rapport
  au legacy,
  à consigner dans `_ecarts-legacy` avec le golden `cloture_articles`).
- **Domaine livré** (`contexts/stock/domain/inventaire`) : `Inventaire` (agrégat immuable : ouvrir, compter, ajouter /
  retirer
  une ligne, reporter le théorique, clôturer, annuler, `articlesInventories()`), `LigneDInventaire`,
  `StatutDInventaire`,
  `PeriodeDeStock` (contrôles d'ouverture dans l'ordre du legacy, avec T43). Codes et messages du legacy conservés
  (`BUSINESS_ERROR` pour le comptage négatif, KB-STK-51 à trancher). `InventaireTest` : 9 tests ; ArchUnit vert.
- **Reste** : ports (`Inventaires`, `PeriodesDeStock`, mouvements gelés, lots, articles), cas d'usage (ouvrir,
  consulter, lister, compter,
  ajouter / retirer, importer une feuille, reporter, clôturer, annuler, état), adapters JDBC (ligne de verrou par centre
  pour la garde),
  contrôleur HTTP (mêmes routes et rôles), feuille Excel et PV (PDF) déplacés ou appelés par le nouveau contrôleur,
  notifications temps
  réel, câblage de la garde de mouvements des autres slices sur `PeriodeDeStock`, tests de concurrence, puis retrait du
  legacy (`InventaireRestController`, `InventaireService`, `InventoryGuardedMovementRepository`).

### Suite 7b (2026-10-24) — ports, cas d'usage et adaptateurs livrés

- **Ports** (`domain/inventaire`) : `Inventaires` (créer avec la référence `INV-nnnnn`, relire, verrouiller,
  enregistrer, période, liste,
  verrou exclusif du centre), `StockInventorie` (théorique figé, lots, mouvements, écritures de clôture).
- **Cas d'usage** (`application/inventaire/GestionDesInventaires`) : ouvrir (T43, période, mouvements après la date,
  recalcul), consulter,
  lister, état, compter, ajouter / retirer une ligne, reporter le théorique, clôturer (transaction unique : stock des
  lots, lots trouvés,
  gel des mouvements et rejeu du PMP **limités aux articles inventoriés**, stock de départ `INVENTAIRE`), annuler.
- **Adapters** : `InventairesJdbc`, `StockInventorieJdbc` (mêmes tables V4, aucune migration ; verrou de la ligne du
  centre dans `centers`),
  `NotificationsDInventaireSpring` (évènement publié `InventaireModifie`), câblage dans `StockContexteConfiguration`.
- **Tests** : `GestionDesInventairesTest` (7), `InventaireTest` (9) ; ArchUnit vert. Les adaptateurs JDBC seront prouvés
  par le rejeu du
  filet de caractérisation une fois le contrôleur basculé.
- **Reste** : import de la feuille Excel (cas d'usage `importerUnComptage` + lecture), contrôleur HTTP (mêmes routes,
  rôles et JSON), PV et
  feuille déplacés ou appelés par le nouveau contrôleur, relais `InventaireModifie` → WebSocket côté plateforme, bascule
  du contrôleur et
  rejeu des 70 goldens (écart décidé : KB-STK-50, à placer en `_ecarts-legacy`), garde de mouvements des autres slices
  sur
  `PeriodeDeStock` sous verrou partagé, tests de concurrence (T20, T21, T44-inventaire), puis retrait du legacy.
