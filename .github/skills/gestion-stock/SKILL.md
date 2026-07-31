---
name: gestion-stock
description:
  Règles métier et techniques du module stock (articles, lots, bons de commande/réception/sortie, PMP, FEFO,
  recalc PMP, dashboard stock, verrous, WebSocket temps réel), avec contraintes architecture hexagonale + DDD,
  isolation multi-centre, Angular 22 signals côté frontend, et couverture de tests backend/frontend.
---

# Gestion Stock - Hemodialyse

## Objectif métier

Le module stock garantit la traçabilité complète des mouvements et la valorisation fiable des consommations
(notamment pour les séances), en assurant :

- la disponibilité opérationnelle des articles par centre,
- la valorisation au PMP,
- la sortie des lots en FEFO,
- l'intégrité documentaire via bons (commande, réception, sortie),
- la synchronisation temps réel des écrans.

Ce skill complète les règles transversales de `AGENTS.md` (hexagonal/DDD, multi-centre, Angular 22, tests,
anti-deprecated), qu'il faut appliquer systématiquement.

## Règles non négociables

1. **Multi-centre strict**: toute lecture/écriture stock est filtrée par `centerId`.
2. **Aucune sortie manuelle hors workflow**: les sorties passent par `BonSortie`/mouvements stock.
3. **FEFO obligatoire** pour le choix des lots (date de péremption la plus proche d'abord).
4. **PMP calculé côté domaine** (jamais saisi par l'utilisateur).
5. **Pas de logique métier en contrôleur ou composant**: orchestration uniquement.
6. **Événements temps réel scoping center**: `/topic/center/{centerId}/events`.

## Backend (Spring Boot 4, Java 21) - Hexagonal/DDD

### Organisation attendue

- `domain/stock/model`: `Article`, `Lot`, `StockMovement`, `BonReception`, `BonSortie`, `BonCommande`, etc.
- `domain/stock/port`: `*UseCase`, `*RepositoryPort`, ports stock transverses.
- `domain/stock/service`: implémentations métier (`BonReceptionService`, `BonSortieService`, `PmpCalculator`,
  `PmpEngine`, `PmpRecalculationCoordinator`).
- `infrastructure/web/rest`: controllers REST dédiés stock.
- `infrastructure/persistence/*`: adaptateurs/repositories/entités.

### Flux métier critiques

#### 1) Bon de commande

- Création en brouillon.
- Validation selon règles métier (statut + cohérence lignes).
- Transformation possible vers bon de réception (sans perte de traçabilité).

#### 2) Bon de réception

- Cycle: `BROUILLON -> VALIDÉ`.
- À la validation: création des mouvements d'entrée + gestion des lots + mise à jour stock article.
- Envoi d'événements WebSocket de rafraîchissement stock.

#### 3) Bon de sortie

- Contrôle disponibilité stock par article.
- Allocation FEFO des lots disponibles.
- Mouvements de sortie valorisés au PMP courant.
- Mise à jour des quantités restantes par lot + événement temps réel.

### PMP (Prix Moyen Pondéré)

- Le calcul doit rester dans un service de domaine pur (ex: `PmpCalculator`).
- Règles usuelles:
  - **ENTRÉE**: nouveau PMP pondéré.
  - **SORTIE**: valorisation au PMP courant.
  - **AJUSTEMENT**: comportement explicite selon type d'ajustement.
- Arrondis cohérents (`scale` stable) et déterministes.
- Recalcul global/cascade via moteur dédié (`PmpEngine`) et coordination des verrous.

### Verrouillage / recalc

- Utiliser un coordinateur de recalcul (`PmpRecalculationCoordinator`) pour éviter collisions.
- Exposer l'état des jobs/locks au dashboard.
- Émettre un événement de lock update (`STOCK_RECALC_LOCKS_CHANGED`) pour UI live.

## Frontend (Angular 22 zoneless + Signals)

### Principes UI/state

- Utiliser `signal`, `computed`, `effect` (et patterns projet existants).
- Le `centerId` provient du contexte applicatif (`AppShellStore.currentCenterId()` ou équivalent).
- Toute action API stock inclut `centerId`.
- En cas d'événement WebSocket stock, recharger les données visibles sans fuite inter-centres.

### Écrans stock attendus

- Dashboard stock (analytics, valorisation, alertes, détails PMP).
- Bons de commande (création, validation, transformation).
- Bons de réception (saisie lignes/lots, validation).
- Bons de sortie (sélection article + quantité + lots FEFO).

### UX métier recommandée

- Afficher quantités disponibles et alertes de rupture avant validation.
- Empêcher une sortie dépassant le disponible.
- Rendre visible le statut des locks de recalcul PMP.
- Garder l'UI simple côté soignant: valorisation détaillée réservée aux vues admin/compta.

## API & événements (référence fonctionnelle)

- Endpoints de référentiels stock: `/api/v1/stock/referentiel/*`
- Endpoints dashboard stock: `/api/v1/stock/dashboard/*`
- Endpoints bons (commande/réception/sortie): selon contrôleurs dédiés module stock.
- Événements WebSocket fréquents:
  - `STOCK_MOVEMENT_CHANGED`
  - `STOCK_RECALC_LOCKS_CHANGED`

## Données et persistance

- Compatibilité H2/PostgreSQL requise.
- Horodatages UTC.
- PK UUID.
- Indexer les accès critiques stock (notamment historiques PMP, paires `center_id` + clés métier).
- Ne jamais mélanger des données de centres différents dans une même requête métier.

## Tests obligatoires (backend + frontend)

### Backend

- **Unitaires** (domain services):
  - calcul PMP (cas nominaux + bords),
  - allocation FEFO,
  - transitions de statuts des bons,
  - refus en stock insuffisant.
- **Intégration**:
  - contrôleurs REST stock,
  - persistence adapters,
  - isolation multi-centre,
  - événements WebSocket sur mouvements/locks.

### Frontend

- **Unitaires (Vitest)**:
  - logique signal/state,
  - validation formulaires bons,
  - mapping payload API.
- **Intégration/E2E (Playwright)**:
  - parcours commande -> réception -> sortie,
  - visibilité des mises à jour en temps réel,
  - isolation par centre actif.

## Anti-patterns à éviter

- Calculer le PMP dans un controller ou composant.
- Laisser l'utilisateur choisir librement le lot sans FEFO (hors exception explicitement codée et auditée).
- Envoyer des requêtes stock sans `centerId`.
- Utiliser des API dépréciées (backend ou frontend).
- Cacher les erreurs stock critiques (rupture, lock actif, article inactif).

## Checklist rapide avant merge

- [ ] `centerId` propagé dans tous les ports/use cases/endpoints/store calls.
- [ ] FEFO respecté sur les sorties.
- [ ] PMP recalculé/valorisé côté domaine uniquement.
- [ ] Événements WebSocket déclenchés sur changements stock.
- [ ] Tests unitaires + intégration ajoutés/modifiés.
- [ ] Aucune API dépréciée introduite.

## Fichiers de référence utiles

- `backend/src/main/java/com/hemodialyse/backend/domain/stock/service/PmpCalculator.java`
- `backend/src/main/java/com/hemodialyse/backend/domain/stock/service/PmpEngine.java`
- `backend/src/main/java/com/hemodialyse/backend/domain/stock/service/BonReceptionService.java`
- `backend/src/main/java/com/hemodialyse/backend/domain/stock/service/BonSortieService.java`
- `backend/src/main/java/com/hemodialyse/backend/infrastructure/web/rest/StockDashboardRestController.java`
- `frontend/src/app/core/api/stock-api.service.ts`
- `frontend/src/app/features/stock/stock-dashboard.component.ts`
- `frontend/src/app/features/stock/bons-reception.component.ts`
- `frontend/src/app/features/stock/bons-sortie.component.ts`


