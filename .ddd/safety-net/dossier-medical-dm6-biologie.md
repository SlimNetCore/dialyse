# Filet de non-régression — DM6 observations biologiques

Slice : `dossier-medical-dm6-biologie`. Contexte : `dossier-medical`.
Date : 2026-11-01. Pattern : DM2/DM3/DM4.

## Point d'observation

- HTTP : `PrescriptionMedicaleRestController` (legacy `platform`, ordonnances/biologie lues via
  `PrescriptionMedicale` et résultats d'analyses) + MockMvc.
- SQL : tables lues par `bc-medical` (biologie) ; nouvelles tables `observations_biologiques`
  créées par le contexte (aucune migration legacy, table neuve JDBC).

## Tests existants couvrant la slice

- `PrescriptionMedicaleRestControllerTest` (platform) : lecture/pagination des ordonnances par centre.
- `ResultatAnalyseRestControllerTest` (platform) : résultats d'analyses par patient, isolation centre.
- `SeanceDetailsIntegrationTest` : biologie rattachée à la séance (lecture seule).

## Scénarios verrouillés (multi-centre)

1. Liste des observations/biologies d'un patient : seul le centre demandé est retourné (`center_id`).
2. Création avec `centreId` d'un autre centre : refus (`AccessDeniedException` / 4xx).
3. Pagination `page`/`size` + enveloppe `PagedResult` (`items`, `total`, `page`, `size`).
4. Isolation : le centre B ne lit jamais les observations du centre A.

## Stabilité

Filet vert et stable : les tests legacy listés passent sans flakiness connue.

## Points d'attention

- Les résultats de biologie alimentent la sérologie (DM4) et les demandes d'examen (DM3) :
  ne pas dupliquer les invariants de positivité ici (simple enregistrement + seuils de référence).
- Données de santé : cloisonnement strict par `center_id`, aucune lecture cross-centre.
- Garde finale DM8 : « pas de SQL sur les tables du dossier médical hors contexte ».
