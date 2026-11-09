---
slice: pilotage-direction-pd3-alertes
status: built
date: 2026-11-09
order: 3
context: pilotage-direction
sources: [.ddd/tactical/pilotage-direction.md, ADR 0066, 0075, 0102, 0103, 0104, .ddd/safety-net/pilotage-direction.md]
---

# PD3 — Alertes direction (lot C)

> **Reprise (2026-11-09, ADR 0104)** : refactoring SUR PLACE. La table parallèle `alertes_direction` du module gelé est abandonnée ; le contexte possède la table legacy **`direction_alert_history`** (V1 : `societe_id`, `center_id`, `centre_nom`, `code`, `severity`, `valeur`, `first_seen_at`, `resolved_at`). Aucune façade, aucun flag, aucune projection, aucune migration.

## Cas d'usage
Lever les alertes d'un centre (politique), enregistrer leur apparition et leur résolution (historique), consulter l'historique (`/api/v1/direction/alerts/history`), suivre le temps réel (détection des changements, diffusion STOMP `/topic/societe/{id}/dashboard`).

## Fait
- Domaine : `PolitiqueDAlertes`, `AlerteLevee`, `GraviteDAlerte`, `CleDAlerte`, `AlerteOuverte`, `ConstatDuCentre`, `RapprochementDesAlertes`, `LimiteDHistorique`, `ConstatDesIndicateurs`, `Changement`.
- Application : `EnregistrerLesAlertes`, `ConsulterLHistoriqueDesAlertes`, `SuivreLeTempsReel` ; ports `HistoriqueDesAlertes`, `ConstatsDuTableauDeBord`, `SocieteDUnCentre`, `DiffusionDesChangements`.
- Adaptateurs sortants : `HistoriqueDesAlertesJdbc`, `SocieteDUnCentreJdbc`, `ConstatsDuTableauDeBordParLeTableauDeBord` (ACL), `DiffusionParLeCanalFourni`.
- Adaptateurs entrants : `AlertesPubliees`, `TempsReelPublie`, `TempsReelPlanifie` ; langages publiés `AlertesDeLaDirection`, `TempsReelDeLaDirection` ; ports fournis `IndicateursDuTempsReelFournis`, `DiffusionDuTableauDeBordFournie`.
- Plateforme : traducteur `IndicateursDuTempsReelParLaPlateforme` ; `WebSocketDirectionPublisher` implémente le port fourni ; `DirectionIndicatorsQueryService`, `DirectionGmaoQueryService`, `DirectionDashboardRestController`, `NotificationService`, `WebSocketStockEventPublisher`, `WebSocketInventaireEventPublisher` consomment le langage publié ; `DirectionSubscriptionTracker` : seul le type injecté change (seam, fermé).
- Retirés : `DirectionAlertPolicy`, `DirectionAlertHistoryService`, `DirectionRealtimeService`, `DirectionRealtimePort`, `DashboardDiff`, `DirectionRealtimeScheduler`, et leurs 3 tests classiques (repris dans le module).

## Parité
4 goldens PD3 + 13 autres goldens PD1/PD2/PD4 + `DirectionRealtimeIntegrationTest` : INCHANGÉS, aucun écart (aucun `_nouveau_code`, aucune archive). Plan de bascule : sans objet (refactoring sur place, ADR 0024) ; retour arrière = revert du commit.

## Décisions
R2 option (a) reproduite : identité (centre, code) des lignes ouvertes, sans jour ; valeur, gravité et nom FIGÉS à la première apparition ; réapparition = nouvelle ligne ; alimentation seulement pendant qu'une direction est connectée (KB-DIR-04). Horloge JVM (KB-DIR-03). `limit` borné [1, 200]. Voir ADR 0104.

## Résiduels (revue 2026-12-15, butoir 2027-03-31)
PD3-R1 `DirectionSubscriptionTracker` sans filet, réservé PD5 · PD3-R2 aucune contrainte d'unicité de l'alerte ouverte (course) · PD3-R3 indicateurs fournis par la plateforme jusqu'à PD5 · PD3-R4 contrôleur et jeton en plateforme · PD3-R5 `centers` lu en SQL (jalon O4).

## Questions ouvertes
Historique hors connexion · fuseau de l'horodatage · `limit` illisible en 400 · date de référence des alertes de stock · contrainte d'unicité (ADR 0104).

## Données
Aucune migration. Détections SELECT seulement : §71 de `.ddd/migration/verifications-production.md`.
