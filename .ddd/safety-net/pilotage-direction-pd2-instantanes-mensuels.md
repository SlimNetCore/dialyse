# Filet de non-régression — PD2 instantanés mensuels

Slice : `pilotage-direction-pd2-instantanes-mensuels`. Contexte : `pilotage-direction`.
Date : 2026-11-01.

## Point d'observation

- Legacy : `DirectionSnapshotService` + `DirectionSnapshotIntegrationTest` (platform).
- Nouveau : `InstantanesMensuelsControleur` (`/api/v1/pilotage/instantanes-mensuels`).

## Tests existants couvrant la slice

- `DirectionSnapshotIntegrationTest` : instantanés par centre et mois, isolation.
- Nouveaux : `InstantaneMensuelTest`, `InstantaneMensuelServiceTest`,
  `InstantanesMensuelsJdbcIntegrationTest`, `InstantanesMensuelsControleurIntegrationTest`.

## Scénarios verrouillés (multi-centre + R1)

1. Un instantané par centre et mois (R1 : `INSTANTANE_DEJA_EXISTANT` sinon).
2. Capture figée dès création ; consultation et liste paginée par centre.
3. Isolation : le centre B ne lit jamais les instantanés du centre A.
4. Verrou de clôture Facturation (OHS) : le mois doit être clos pour capturer (dette : le
   contrôle du verrou est documenté, l'ACL Facturation sera branchée au retrait du legacy).
5. Cache `ref.instants.mois` (PT3M) : clé incluant le `centerId`.

## Points d'attention

- Événement publié `InstantaneMensuelCapture` (langage `pilotagedirection.v1`).
- Données anonymisées multi-centres (R3).
