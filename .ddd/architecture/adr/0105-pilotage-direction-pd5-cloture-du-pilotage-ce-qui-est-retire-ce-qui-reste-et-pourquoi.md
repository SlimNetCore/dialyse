---
status: accepted
date: 2026-11-09
review-by: 2026-12-15
decision-makers: [utilisateur (décisions déléguées à l'assistant via le coordinateur, 2026-11-09), architecte]
context-scope: pilotage-direction, plateforme (clôture du Pilotage direction : suivi des abonnements, application/direction, gardes)
related: ADR 0024 et 0075 (refactoring sur place), 0102 (lots A), 0103 (lot B), 0104 (lot C) ; `.ddd/migration/slices/pilotage-direction-pd5-lectures-publiees.md`, `.ddd/safety-net/pilotage-direction.md` (section « Audit PD5 »), KB-DIR-01 à KB-DIR-08
---

# 0105 - Pilotage direction, lot D (PD5) : clôture du Pilotage ; ce qui est retiré, ce qui reste en plateforme et pourquoi

Décisions prises par délégation, EN VIGUEUR, **à confirmer métiers / DPO au plus tard le 2026-12-15**. Refactoring SUR PLACE (ADR 0024/0075) : mêmes tables, mêmes URL, mêmes rôles, mêmes codes, même canal STOMP ; aucune façade, aucun flag, aucune projection, aucune migration. Reproduire à l'identique : KB-DIR-01 à 08 inchangés.

## Parité
Aucun écart : aucun `.approved` modifié, aucun `_nouveau_code`, aucune archive `_ecarts-legacy`. Aucun code de production n'est modifié par PD5 (ni édition, ni suppression, ni création) : aucun seam, `seamAllowed` est resté `[]`. Seuls des tests, des gardes et de la documentation sont ajoutés.

## Décisions
1. **Filet du suivi des abonnements STOMP** (résiduel PD3-R1 soldé) : `LesAbonnementsDeLaDirectionCharacterizationTest` (8 tests) écrit et VERT sur le code existant AVANT toute autre action. Il fabrique les événements WebSocket comme Spring les émet et observe le vrai `SuivreLeTempsReel` (ports sortants en mémoire) : comptage par couple (session, abonnement) une seule fois, société suivie tant qu'un abonnement subsiste (plusieurs sessions), désabonnement inconnu sans effet, déconnexion libérant les seuls abonnements de la session (`s1/` n'est pas préfixe de `s10/`), destinations hors canal de société et identifiants illisibles ignorés, effet sur le balayage et le signalement. Règle écrite : RG-DIR-109.
2. **`DirectionSubscriptionTracker` RESTE en plateforme** (non migré). Raisons : (a) il écoute des événements Spring WebSocket (`SessionSubscribeEvent`...), or le module n'a (et ne doit pas avoir) aucune dépendance sur Spring messaging/WebSocket : le transport STOMP est un adaptateur de plateforme, comme `WebSocketDirectionPublisher` (ADR 0104) ; (b) il lit la constante `WebSocketAuthentication.SOCIETE_TOPIC_PREFIX`, et `WebSocketAuthentication` dépend de `DirectionAccessGuard` et du jeton (IAM) : le déplacer dans le module créerait un cycle Maven (la plateforme dépend du module). Le Pilotage n'est joint que par son langage publié `TempsReelDeLaDirection` (règle ArchUnit existante de PD3). Il entre dans `covered.json` (filet posé).
3. **`application/direction` : liste nominative de ce qui reste** (11 fichiers, aucun du Pilotage) : rendu PDF (`DirectionReportPdfService`, `SocieteLetterheadPort`) ; comptes (`DirectionAccountService`, `SocieteAdminAccountService`, `PasswordPolicy` : Organisation / IAM, jamais touchés) ; services de lecture des autres contextes (`DirectionAbsencesQueryService`, `DirectionCapaciteQueryService`, `DirectionGmaoQueryService`, `DirectionIndicatorsQueryService`, `DirectionStockGroupesQueryService`) et `AnonymityPolicy` (leur masquage). Vérifié : AUCUN SQL dans ces services de lecture (ils passent par `absencespatients`, `planification`, `gmao`, `dossiermedical`, `stock`, `facturation` publiés et par le langage publié du Pilotage pour la liste des centres et les alertes).
4. **Contrôleurs et garde** : `DirectionDashboardRestController` et `DirectionSnapshotRestController` restent en plateforme (`@PreAuthorize`, `DirectionAccessGuard`, rendu PDF, routes Absences/Capacité/GMAO/Stock/Indicateurs), comme aux lots A à C. `DirectionAccessGuard` revérifie le compte en SQL sur `app_user*`/`societes` (IAM, utilisé aussi par `WebSocketAuthentication` et `AuditRestController`) : reste. `DatesIllisiblesDeLaDirectionAdvice` suit les contrôleurs.
5. **Garde** : `TableDuPilotageTest` (11 tests) : liste blanche nominative des seuls lecteurs et écrivains de `direction_snapshot` (`InstantanesMensuelsJdbc`) et `direction_alert_history` (`HistoriqueDesAlertesJdbc`) ; liste nominative exacte du contenu de `application/direction` (propriétaire cible et catégorie de chacun), comptes asserted présents, ne touchant pas les tables du Pilotage et absents du module, services de lecture et rendu sans SQL ; revue au plus tard le 2026-12-15. `FrontieresDesContextesTest` (règle PD3 couvrant le tracker, règles « le legacy n'utilise le Pilotage que par son langage publié ») et ArchUnit du module : inchangés, verts.

## Ce qui est retiré par le Pilotage depuis le gel (bilan des lots A à D)
`DirectionDashboardQueryService`, `DirectionBreakdownQueryService`, `EffectifSql` (lot A) ; `DirectionSnapshotService`, `DirectionSnapshotScheduler` (lot B) ; `DirectionAlertPolicy`, `DirectionAlertHistoryService`, `DirectionRealtimeService`, `DirectionRealtimePort`, `DashboardDiff`, `DirectionRealtimeScheduler` (lot C). Le module possède ses deux tables, ses deux planificateurs (`CaptureDuMoisEcoulePlanifiee`, `TempsReelPlanifie`) et publie `TableauDeBordDeLaDirection`, `InstantanesMensuelsDeLaDirection`, `AlertesDeLaDirection`, `TempsReelDeLaDirection`, `RapportDeSyntheseDeLaDirection`. PD5 ne retire rien de plus : ce qui reste n'appartient pas au Pilotage.

## Résidus assumés (revue 2026-12-15, butoir 2027-03-31)
| Réf. | Résiduel | Propriétaire cible |
|---|---|---|
| PD5-R1 | `DirectionAbsencesQueryService` (lecture Absences, filet A4) reste en plateforme, contrôleur de la plateforme appelant | Absences de patients (jalon de reprise des lectures Direction par le contexte) |
| PD5-R2 | `DirectionCapaciteQueryService` (Planification + GMAO, filet P6) | Planification (capacité), GMAO (générateurs) |
| PD5-R3 | `DirectionGmaoQueryService` (synthèse GMAO + enregistrement des alertes `GMAO_*`, filet G6) | GMAO |
| PD5-R4 | `DirectionStockGroupesQueryService` lit `ValorisationArticlesService` (service de plateforme du Stock, non publié directement ; sa sortie est le type publié `ValorisationDuStock`), filet S4 | Stock (publier la valorisation par groupe) |
| PD5-R5 | `DirectionIndicatorsQueryService` : indicateurs cliniques (DM9) et du stock calculés par la plateforme (PD3-R3 repris ici) ; consommés par les traducteurs `IndicateursAFigerParLaPlateforme`, `IndicateursDuRapportParLaPlateforme`, `IndicateursDuTempsReelParLaPlateforme` (acl/pilotagedirection) | Dossier médical et Stock, ou Pilotage une fois les dépendances de module sans cycle |
| PD5-R6 | Comptes de direction et d'administrateur de société (`DirectionAccountService`, `SocieteAdminAccountService`, `PasswordPolicy`), `DirectionAccessGuard` : couverts par des tests classiques seulement (`DirectionIntegrationTest`, `DirectionPoliciesTest`), hors `covered.json` | Organisation / IAM (jalon O4, décision §9.6 du filet : déplacer, jamais retirer par PD5) |
| PD5-R7 | `DirectionSubscriptionTracker` et `WebSocketDirectionPublisher` : transport STOMP, restent en plateforme tant que le module n'a pas de dépendance de messagerie | Plateforme (adaptateurs de transport) |
| PD5-R8 | Les résiduels de décision métier ne sont pas levés : KB-DIR-01 (DPO), KB-DIR-02, KB-DIR-03, KB-DIR-04 (R2 option a), KB-DIR-07 ; PD2-R*, PD3-R2 (contrainte d'unicité) | Métiers / DPO |

## Questions ouvertes
1. Calendrier de reprise des lectures Direction par Absences, Planification, GMAO, Stock (PD5-R1 à R4). 2. `DirectionIndicatorsQueryService` : Pilotage, Dossier médical ou Stock (PD5-R5). 3. Faut-il un contrat de test de transport (message STOMP réel) pour le suivi des abonnements (le filet fabrique les événements Spring sans courtier) ?
