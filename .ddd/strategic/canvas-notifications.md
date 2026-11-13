---
status: approved
date: 2026-11-12
sources: [ADR 0140, .ddd/migration/reviews/inventaire-platform-restant.md (A7, A10, B8), NotificationRestController.java, NotificationJournalPort.java, NotificationService.java, NotificationPurgeScheduler.java, V29, V49]
---
# Bounded Context Canvas — Notifications (`notifications`)

| Rubrique | Contenu |
|---|---|
| Nom / module | Notifications · `backend/contexts/notifications` · `com.hemodialyse.backend.contexts.notifications` · `hemodialyse.contexts.notifications.enabled` |
| But | Acheminer les notifications émises par les contextes vers les bonnes personnes du centre (temps réel + journal durable), suivre ce qui est lu, purger. N'invente aucun message. |
| Classification | **Generic** (SD13), périmètre resserré : acheminement, journal, lecture. Le catalogue RG-NOT (textes, déclencheurs) passe aux émetteurs. |
| Rôle | gateway (diffusion) + execution légère (journal) |
| Langage | notification, type de notification, contenu (payload), audience, destinataire visé, notification lue, non lue, journal, purge, diffusion en temps réel (glossaire `glossaries/notifications.md`) |
| Règles | une notification est rattachée à un centre ; visible si l'utilisateur a un rôle de l'audience, ou est le destinataire visé ; la lecture est propre à chaque utilisateur ; une notification d'un autre centre est ignorée au marquage ; identifiant déterministe = pas de doublon si l'événement est rejoué ; liste paginée max 100 (50 par défaut), `no-store` ; purge quotidienne 03:40 des plus anciennes (durée à confirmer, Q10) ; diffusion après commit de l'émetteur |
| Données possédées | `notification_evenement` (id, center_id, type, payload, target_roles, target_user_id, cree_le), `notification_lecture` (notification_id, user_id, lue_le) |
| Commandes reçues | `Notifier.notifier(DemandeDeNotification)` (`notifications.v1`) ; REST inchangé : `GET /api/v1/notifications`, `POST …/lecture`, `POST …/lecture-tout` ; tâche 03:40 |
| Événements émis | aucun fait métier ; diffusion STOMP via le port `DiffusionEnTempsReel` (destinations inchangées) |
| Dépendances | Amont de : Patient & couverture, Dossier médical, Séance, Planification, Absences patients, Stock, GMAO (OHS+PL `notifications.v1`, ACL côté émetteur). Aval de : plateforme (session/rôles : Conformist ; STOMP : adaptateur plateforme, ACL). Pilotage direction : Separate Ways (alertes de direction ≠ notifications, à confirmer). |
| Reste en plateforme | transport STOMP (A6), session/rôles (A1), chaîne de sécurité ; `SaisieInfirmierNotifier`, `NotificationService` jusqu'à PL13d |
| Hypothèses | l'audience est déclarée par l'émetteur (Q5) ; les codes de type existants sont conservés tels quels (le front en dépend) |
| Verdict | équipe plateforme ; PL13 est un chantier par émetteur, séquentiel |

## Émetteurs (méthodes actuelles de `NotificationService`, 43)
| Émetteur | Méthodes | ACL `*Legacy` retirées | Slice |
|---|---|---|---|
| Patient & couverture, Dossier médical | patient créé/modifié, PEC validée/clôturée/supprimée, attestation créée/supprimée, prescription modifiée, observance non respectée | `NotificationsDeLaFicheParLaPlateforme`, `NotificationsDeLAnemieLegacy`, `SignalementDesPrescriptionsLegacy` | PL13a |
| Séance | séance créée/validée/modifiée/supprimée/déverrouillée, volets médical/paramédical, saisie infirmier, séances à régulariser | `DiffusionsFournieesLegacy`, `RappelsFournisLegacy` (à vérifier) | PL13b |
| Planification | présence sous/sur-effectif, libération en échec, optimisation, replanification, générateur indisponible, isolement, retour en salle ordinaire, séances déplacées, roulement, remplacement, absences d'infirmier | `NotificationsDeLaPresenceLegacy`, `NotificationsDeLaLiberationLegacy`, `NotificationsDesAbsencesDInfirmiersLegacy`, `AlertesDuPlacementLegacy`, `AlertesDeLOptimisationLegacy` (retirées, ADR 0155) | PL13c (fait) |
| Absences patients, Stock, GMAO | absences à qualifier, lots proches de la péremption, réforme proposée | `NotificationsDuCentreLegacy`, `SignalementDesSaisiesLegacy`, `NotificationsDesPeremptionsLegacy`, `GmaoEvenementsReformeListener` | PL13d |

## Slices
NOT1 (module, journal, 3 routes, purge, ~12 f., ~800 l.) → NOT2 (façade `NotificationService` sur `Notifier`, ~700 l.) → PL13a → b → c → d (séquentiel, `NotificationService` partagé). Avant NOT1 : filet sur `NotificationRestController` (hors `covered.json`).

## Questions ouvertes
1. Voir `bounded-contexts.md` § Questions ouvertes (Q5, Q10, Q11, Q12) ; l'attribution des méthodes aux émetteurs (ex. réforme proposée : GMAO ou Planification) est à vérifier sur le code au moment de la slice.
