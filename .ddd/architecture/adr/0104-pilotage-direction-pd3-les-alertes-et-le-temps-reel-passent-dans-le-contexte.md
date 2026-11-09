---
status: accepted
date: 2026-11-09
review-by: 2026-12-15
decision-makers: [utilisateur (décisions déléguées à l'assistant via le coordinateur, 2026-11-09), architecte]
context-scope: pilotage-direction, plateforme (alertes de la direction, historique, temps réel)
related: ADR 0024 et 0075 (refactoring sur place), 0102 (lot A), 0103 (lot B) ; `.ddd/migration/slices/pilotage-direction-pd3-alertes.md`, `.ddd/safety-net/pilotage-direction.md` (§9.2 R2), KB-DIR-02, KB-DIR-03, KB-DIR-04, KB-DIR-07
---

# 0104 - Pilotage direction, lot C (PD3) : les alertes, leur historique et le temps réel passent dans le contexte ; la plateforme garde le canal STOMP, le suivi des abonnements et les indicateurs

Décisions prises par délégation, EN VIGUEUR, **à confirmer métiers / DPO au plus tard le 2026-12-15**. Refactoring SUR PLACE (ADR 0024/0075) : même table `direction_alert_history`, mêmes URL (`/api/v1/direction/alerts/history`, `/indicators`, `/gmao`), mêmes rôles, mêmes codes, même canal STOMP `/topic/societe/{id}/dashboard` et même forme du message, aucune façade, aucun flag, aucune projection, aucun événement de domaine ni outbox, aucune migration. SQL repris MOT POUR MOT.

## Parité
Les 4 goldens PD3 (`alertes/ouverture_et_resolution`, `historique`, `seuils`, `temps_reel`), les 13 autres goldens PD1/PD2/PD4, `DirectionRealtimeIntegrationTest` (5) : **aucun écart**, aucun `.approved` modifié, aucun `_nouveau_code`, aucune archive `_ecarts-legacy`. Seuls changent, dans les tests, les types et les noms des appels (le filet pilote désormais `SuivreLeTempsReel` construit sur les adaptateurs du module et le diffuseur STOMP réel).

## Comportements reproduits (statu quo, à confirmer)
| Réf. | Comportement reproduit | Confirmation attendue |
|---|---|---|
| KB-DIR-04 / R2 option (a) | Identité d'une alerte ouverte = (centre, code), SANS jour ; valeur, gravité et nom du centre FIGÉS à la première apparition ; une réapparition (même le jour même) ouvre une NOUVELLE ligne ; alimentation seulement pendant qu'une direction est connectée | Métiers : la règle « une alerte par centre et par jour » du module gelé est abandonnée définitivement ; faut-il un historique hors connexion ? |
| KB-DIR-03 | Horloge de la JVM (UTC) pour l'horodatage des alertes (`HorlogeDuPilotage`) | Fuseau de référence |
| KB-DIR-02 | `limit` illisible sur `/alerts/history` répond 500 (la conversion de type reste au contrôleur de la plateforme) ; `limit` hors bornes est RAMENÉ à [1, 200], jamais refusé | Métiers : 400 plutôt que 500 ? |
| KB-DIR-07 | Alertes de stock évaluées à la date de FIN de la période demandée | Date de référence du stock |
| Course | Aucune contrainte d'unicité sur (société, centre, code) ouvert : deux recalculs simultanés peuvent ouvrir deux lignes | Voir PD3-R2 |
| Échec | Un recalcul qui échoue est journalisé et ignoré (ni historique ni diffusion), et le constat de référence reste l'ancien | — |

Détail non reproduit à l'identique, SANS effet observable : l'ancien `snapshot()` construisait le constat APRÈS l'écriture de l'historique ; le nouvel ordre (lire tout, écrire l'historique, puis ranger le constat) ne diffère que si la construction du constat lève une exception (donnée nulle imprévue), cas non observé par le filet.

## Décisions
1. **Domaine** : `PolitiqueDAlertes` (six codes, seuils inclusifs 80 % / 50 %), `AlerteLevee`, `GraviteDAlerte` (`WARNING`/`CRITICAL` : noms de contrat conservés), `CleDAlerte` (centre, code), `RapprochementDesAlertes` (ouvrir / résoudre, jamais retoucher), `LimiteDHistorique` (borne [1, 200]), `ConstatDesIndicateurs` et `Changement` (ex `DashboardDiff`).
2. **Application** : `EnregistrerLesAlertes`, `ConsulterLHistoriqueDesAlertes`, `SuivreLeTempsReel` (abonnés, signaux de centre, constat de référence, plafond 60) ; ports `HistoriqueDesAlertes`, `ConstatsDuTableauDeBord`, `SocieteDUnCentre`, `DiffusionDesChangements`.
3. **Adaptateurs** : `HistoriqueDesAlertesJdbc` (propriétaire de `direction_alert_history`), `SocieteDUnCentreJdbc` (`centers.societe_id`), `ConstatsDuTableauDeBordParLeTableauDeBord` (ACL : relit le tableau de bord du lot A et les indicateurs fournis, range le constat avec les noms d'indicateurs du contrat client), `DiffusionParLeCanalFourni`.
4. **Langage publié** : `AlertesDeLaDirection` (`evaluer`, `enregistrer`, `historique` ; enregistrements aux noms du JSON servi), `TempsReelDeLaDirection` (`abonneAjoute`, `abonneRetire`, `estSuivie`, `unCentreAChange`). Ports FOURNIS par la plateforme : `IndicateursDuTempsReelFournis` (indicateurs masqués de la période par défaut, restés en plateforme depuis DM9) et `DiffusionDuTableauDeBordFournie` (transport STOMP).
5. **Ce qui reste en plateforme (justifié)** : `WebSocketDirectionPublisher` (adaptateur de sortie, `SimpMessagingTemplate`) ; `DirectionSubscriptionTracker` (écouteurs d'événements WebSocket, SANS filet : NON migré, réservé PD5 ; seul le type injecté a changé, par seam inscrit dans `seams.md`) ; `DirectionIndicatorsQueryService` (indicateurs, DM9) qui appelle la politique par le langage publié ; `DirectionGmaoQueryService` (alertes `GMAO_*`, même enregistrement publié) ; `DirectionDashboardRestController` (jeton et garde de société, comme aux lots A et B) ; les signaleurs `NotificationService`, `WebSocketStockEventPublisher`, `WebSocketInventaireEventPublisher` (appellent `unCentreAChange`). Les requêtes `Direction*QueryService` d'Absences, Capacité, GMAO et Stock ne sont pas touchées.
6. **Planificateur dans le module** : `TempsReelPlanifie` (`interfaces/planifie`), mêmes cadences que `DirectionRealtimeScheduler` (1 s, balayage 10 s après 10 s), lues par un test.
7. **Retraits** : `DirectionAlertPolicy`, `DirectionAlertHistoryService`, `DirectionRealtimeService`, `DirectionRealtimePort`, `DashboardDiff`, `DirectionRealtimeScheduler` et leurs trois tests classiques (`DirectionAlertPolicyTest`, `DirectionAlertHistoryServiceTest`, `DashboardDiffTest`) repris dans le module (`PolitiqueDAlertesTest`, `LesAlertesDeLaDirectionTest` + contrat historique en mémoire et JDBC, `ConstatDesIndicateursTest`). `seamAllowed` n'a servi qu'à éditer `DirectionSubscriptionTracker` (remis à `[]`).
8. **Gardes** : `TableDuPilotageTest` (propriétaire nominatif de `direction_alert_history`, plus aucun lecteur de plateforme, 2 entrées), `TableDeLOrganisationTest` (entrée de `centers` déplacée vers `SocieteDUnCentreJdbc`), `FrontieresDesContextesTest` (nouvelle règle : signaleurs et indicateurs par le langage publié), ArchUnit du module (inchangé, vert).

## Résidus assumés (revue 2026-12-15, butoir 2027-03-31)
- PD3-R1 : `DirectionSubscriptionTracker` sans test de caractérisation ; à caractériser et migrer avec PD5.
- PD3-R2 : aucune contrainte d'unicité sur l'alerte ouverte (course entre recalculs simultanés, deux lignes) ; contrainte partielle `UNIQUE (societe_id, center_id, code) WHERE resolved_at IS NULL` à décider avec les métiers (détection : §71.1 de `verifications-production.md`).
- PD3-R3 : `IndicateursDuTempsReelFournis` : les indicateurs restent calculés par la plateforme (DM9) ; à lever quand un contexte les reprendra (PD5).
- PD3-R4 : le contrôleur `/alerts/history` et le jeton restent en plateforme (comme les lots A et B).
- PD3-R5 : `SocieteDUnCentreJdbc` lit `centers` en SQL : à remplacer par `organisation.v1` (jalon O4), comme `SocietesEtCentresJdbc` (PD2-R1).

## Questions ouvertes
1. Historique hors connexion (KB-DIR-04) : faut-il alimenter l'historique par une tâche indépendante des abonnés ? 2. Fuseau de l'horodatage (KB-DIR-03). 3. `limit` illisible : 400 (KB-DIR-02). 4. Date de référence des alertes de stock (KB-DIR-07). 5. Contrainte d'unicité de l'alerte ouverte (PD3-R2).
