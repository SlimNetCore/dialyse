# Filet de non-régression — PD1 tableau de bord

Slice : `pilotage-direction-pd1-tableau-bord`. Contexte : `pilotage-direction`.
Date : 2026-11-01.

## Point d'observation

- HTTP : `Direction*RestController` legacy (platform) + nouveau
  `TableauxDeBordControleur` (`/api/v1/pilotage/tableau-de-bord`).
- Read models ; aucune migration legacy (table neuve `tableaux_de_bord`).

## Tests existants couvrant la slice

- `DirectionRestControllerTest`, `DirectionRealtimeIntegrationTest`,
  `DirectionSnapshotIntegrationTest` (platform) : indicateurs par centre, isolation.
- Nouveaux : `TableauDeBordTest`, `TableauDeBordServiceTest`,
  `TableauxDeBordJdbcIntegrationTest`, `TableauxDeBordControleurIntegrationTest`.

## Scénarios verrouillés (multi-centre)

1. Consultation par `centreId` : seul le centre demandé est retourné.
2. Actualisation (alimentation du read model par événements publiés).
3. Pagination quand applicable ; société anonymisée (pas de données nominatives).
4. Cache `ref.alertes.centre` : clé incluant le `centerId`.

## Points d'attention

- Lecture seule ailleurs (R4) : aucune écriture cross-context.
- Le rendu reste une vue agrégée JSON ; l'impression PDF/HTML relève de PD4.
