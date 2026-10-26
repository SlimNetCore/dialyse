---
status: proposed
date: 2026-10-26
deciders: utilisateur (décisions du 2026-10-24 sur la slice S8) ; à valider : points de la section « Points à valider »
related: ADR 0013, 0024, 0045 (décision 8) ; `.ddd/tactical/stock.md` § 7.2 ; `.ddd/migration/slices/stock-s8-peremptions-retrait.md`
---

# ADR 0046 — Stock S8 : péremptions notifiées, événement `LotProcheExpiration` v1, retrait de `bc-article`

## Contexte

La fiche S8 (23/10) prévoyait la notification des péremptions, six événements d'intégration et le retrait de `bc-stock` et
`bc-article`. Au 24/10 `bc-stock` n'existait déjà plus (S5 à S7) ; restaient `bc-article` (5 fichiers), le contrôle quotidien
`ExpirationAlertScheduler` (journal technique seul, KB-STK-53), trois relais temps réel (KB-STK-54) et deux codes d'erreur
incohérents (KB-STK-55, 56).

## Décisions

1. **`ControlerLesPeremptions`** (contexte Stock, adapter planifié `ControleDesPeremptionsPlanifie`, 07:00) :
   centres **actifs** seulement ; lots en stock (> 0) d'**articles actifs** dont la péremption est au plus tard à J+30 (borne
   incluse) ; « aujourd'hui » est le jour du fuseau du centre (`centers.fuseau_horaire`, UTC si absent) ; déclencheur serveur au
   fuseau `app.stock.fuseau-par-defaut`, par défaut `app.absences.fuseau-par-defaut` (`Africa/Algiers`).
2. **Notification** : une alerte durable `LOTS_PROCHES_PEREMPTION` par centre et par jour du centre, pour `ADMIN` et
   `PHARMACIEN`, à identifiant déterministe (UUID v3 de `type|centre|jour`, `NotificationService.send(..., idImpose)`) :
   sûre en multi-instance et au rejeu. Elle porte les comptes (lots, périmés) et la date la plus proche ; le détail des lots est au tableau de bord du stock
   (RG-STK-060). Une insertion concurrente de l'identifiant par une autre instance est traitée comme « déjà émise » par l'ACL
   (pas d'erreur, pas de journal d'échec).
3. **Lots déjà périmés, sans nouvelle table** : signalés le lendemain de leur péremption (première fois) puis rappelés toutes
   les semaines (jours écoulés ≡ 1 mod 7). Un lot **reçu déjà périmé** est signalé au premier contrôle qui suit sa réception
   (le lendemain du jour de sa création, une seule fois). La décision ne dépend que de la date de péremption, du jour du centre
   et du jour de création du lot (`RegleDesPeremptions`) : aucun état à conserver, donc idempotente. **Limite assumée** : si le
   contrôle de J+1 est manqué (panne, redéploiement), un lot qui vient de périmer reste silencieux jusqu'au rappel de J+8 ; les
   lots périmés restent visibles en permanence au tableau de bord (RG-STK-060).
4. **Isolation par échec** : un centre en erreur est consigné (journal technique, niveau ERROR) sans arrêter les autres ; le
   résultat du cas d'usage porte l'incident.
5. **Événement d'intégration `LotProcheExpiration` v1** (langage publié, un par lot signalé) : `version`, `identifiant`
   (déterministe : lot + jour du centre, clé d'idempotence), centre, jour du contrôle, article, code, lot, numéro, péremption,
   quantité, `perime`. Publié dans une unité de travail (registre Spring Modulith, ADR 0013), **avant** la notification ;
   livraison au moins une fois : les consommateurs sont idempotents sur `identifiant`. **Aucun consommateur de production aujourd'hui** : le contrat est prêt, la notification est directe et ne dépend pas de l'Outbox
   (YAGNI assumé). Le registre n'enregistre que pour des écouteurs transactionnels : la garantie « au moins une fois » n'existe
   donc qu'à partir du premier écouteur ; un écouteur de test (`@ApplicationModuleListener`) prouve la livraison.
6. **Événements différés** : `ReceptionValidee`, `SortiePourSeancesEnregistree`, `RapprochementValide`, `PmpRecalcule`,
   `InventaireCloture` ne sont **pas créés** : différés **jusqu'à l'existence de consommateurs** (la Comptabilité continue de
   lire `OperationsDeStock`). Les trois relais temps réel (`MouvementsDeStockModifies`, `VerrousDeRecalculModifies`,
   `InventaireModifie`) sont conservés tels quels (contrat des écrans).
7. **Retrait de `bc-article`** : module, poms, Dockerfile et imports supprimés ; `ArticleRepositoryPort` et son adaptateur sont
   remplacés par le port publié `CatalogueDArticles` dans la Séance. Tables inchangées. L'entité JPA `ArticleJpaEntity` est
   conservée (elle crée la table `articles` en H2 de test, comme `LotJpaEntity`) ; son retrait (avant le 2026-12-31, daté dans la classe)
   passe par un schéma de test fourni par `db/schema.sql`. Le verrou ArchUnit « la Séance n'appelle plus `BonSortieUseCase` » (classe disparue) est remplacé par
   deux règles de `FrontieresDesContextesTest` : plus aucun import de `domain.stock` / `domain.article`, et la Séance n'utilise
   le Stock que par son langage publié.
8. **Codes d'erreur unifiés en 400** (écarts décidés, goldens `*_nouveau_code`, anciens goldens en `_ecarts-legacy`) :
   article **inactif** sur les raccourcis de consommables, l'ajout de consommable et la **validation d'une séance** : 400
   `BAD_REQUEST` (c'était 422 `BUSINESS_RULE_VIOLATION`) ; la validation est tracée par KB-STK-57, un test unitaire
   (`SeanceDomainServiceTest`) et le golden `validation_article_inactif_nouveau_code` ; article inconnu ou d'un autre centre : déjà 400 ; date illisible sur
   `GET /api/v1/direction/stock-groupes` : 400 `BAD_REQUEST` (c'était 500) via `DatesIllisiblesDeLaDirectionAdvice` étendu. Le
   plafond de 12 raccourcis reste. Le cache de valorisation (écriture SQL directe non visible) est conservé.

## Écarts avec le legacy (KB-STK-53 à 57)

| Legacy | Nouveau |
|---|---|
| Journal technique seul, aucune notification | Alerte durable pharmacien + admin, une par centre et par jour |
| Tous les centres (inactifs compris) | Centres actifs |
| Articles inactifs inclus ; périmés signalés chaque jour | Articles actifs ; périmés le lendemain puis chaque semaine |
| Aucune déduplication | Identifiant déterministe (notification) ; clé d'idempotence (événement) |
| Date UTC, cron au fuseau JVM | Jour du centre ; cron au fuseau configuré |

## Conséquences

+ Le pharmacien et l'admin sont enfin prévenus ; le contrat `LotProcheExpiration` v1 est disponible aux futurs consommateurs.
+ Plus aucun module `bc-stock` / `bc-article` ; le Stock est prêt pour `/ddd-verify`.
− Fuseau : un centre sans `fuseau_horaire` est en UTC (comportement du contexte), le déclencheur est à 07:00 d'Alger (06:00 UTC) :
  même date, donc **aucun écart** pour les centres UTC et UTC+1 ; seul un centre à UTC−7 ou au-delà obtiendrait « hier ». Code
  inchangé (décision), documenté seulement.
− Multi-instance : une insertion concurrente de l'identifiant de l'alerte est absorbée par l'ACL ; les événements v1 peuvent
  être publiés deux fois (même identifiant, consommateurs idempotents).
− Volumétrie : un événement par lot et par jour tant qu'il est dans la fenêtre (≈ 30 jours) ; à purger/observer si un
  consommateur lent s'abonne.

## Points à valider

**Restent à l'utilisateur** : (1) texte de la notification (4 langues, en particulier le kabyle) et destinataires (`ADMIN`,
`PHARMACIEN`) ; (2) le contrôle SELECT du point 30 de `verifications-production.md` avant la première notification. Le reste des
points de la revue du 2026-10-26 est traité (voir la fiche de slice).
