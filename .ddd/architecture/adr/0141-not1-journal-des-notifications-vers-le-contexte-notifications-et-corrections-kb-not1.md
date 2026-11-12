---
status: accepted
date: 2026-11-12
decision-makers: [utilisateur, architecte]
context-scope: notifications, platform
related: ADR 0010, 0011, 0024, 0140 ; `.ddd/safety-net/not1-doc1-filet.md` ; `.ddd/safety-net/known-bugs.md` KB-NOT1-1 à 3 ; `.ddd/migration/reviews/not1-realisation.md`
---

# 0141 - NOT1 : le journal des notifications passe au contexte Notifications ; corrections KB-NOT1-1, 2, 3

Refactoring sur place (ADR 0024) : mêmes tables `notification_evenement` et `notification_lecture`, mêmes 3 URLs `/api/v1/notifications` (liste, `/lecture`, `/lecture-tout`), mêmes champs JSON (`AlerteResponse` : `id`, `type`, `centerId`, `payload`, `timestamp`, `lue`), mêmes rôles (session + garde de centre), mêmes codes. Pas de façade ni de flag. Module Maven `contexts/notifications` (ADR 0011), enregistré dans `backend/pom.xml` et dépendance de `platform`.

## Décision de placement
| Élément | Où |
|---|---|
| `NotificationRestController`, `NotificationJournalPort`, `NotificationJournalJdbcAdapter`, `NotificationPurgeScheduler` (plateforme) | supprimés |
| Domaine | `Notification`, `Audience`, `TypeDeNotification`, `NotificationId`, `PaginationDuJournal`, `DureesDuJournal` (30 j de consultation, 60 j de conservation) |
| Application | `ConsulterLeJournal`, `MarquerLesNotificationsLues`, `ToutMarquerLu`, `PurgerLesNotifications`, `NotifierLesDestinataires` ; ports `NotificationsDuJournal`, `SessionCourante`, `Horloge`, `Identifiants` |
| Adapters | `NotificationsDuJournalJdbc` (même contrat de test que le double en mémoire), `SessionLegacy` (ACL de session, réflexion sur le principal), `JournalDesNotificationsControleur` + `ErreursDuJournalAdvice`, `PurgeNocturneDesNotifications` (03:40, conservation 60 j inchangée) |
| Langage publié `notifications.v1` | `Notifier` + `DemandeDeNotification` + `Audience` (ADR 0140 D6) ; `DiffusionEnTempsReel` (port FOURNI par la plateforme) ; `JournalDesNotifications` (`enregistrer`, `existe`) : voir D2 |

## Décisions de conception
- D1. `NotificationService` implémente `DiffusionEnTempsReel` (le transport STOMP et l'avertissement de la direction restent en plateforme, destinations et message inchangés). Choix minimal : aucun nouveau fichier de plateforme (le hook de sécurité legacy bloque la création hors `covered.json`). Un adaptateur dédié pourra le remplacer en NOT2.
- D2. Tant que `NotificationService` et les sept ACL `*Legacy` d'émetteurs existent (NOT2, PL13a-d), ils écrivent et testent le journal par `JournalDesNotifications`, port publié minimal (`enregistrer`, `existe`) ; l'audience est lue dans les clés `targetRoles` / `targetUserId` du contenu (convention de ces émetteurs et du front). Ce port disparaît avec `NotificationService` (PL13d).
- D3. `Notifier` / `NotifierLesDestinataires` sont livrés et testés mais PAS encore appelés : le branchement de `NotificationService` est NOT2 (ADR 0140). Si le journal échoue, rien n'est diffusé et l'erreur remonte (l'ancien comportement « le temps réel reste prioritaire » est conservé dans `NotificationService`, non touché). Décision à reprendre en NOT2.
- D4. Les rôles sont stockés et comparés exactement : `POSITION(',ROLE,' IN target_roles)`, et non plus `LIKE` (voir KB-NOT1-1). Les espaces autour d'un rôle sont retirés à l'enregistrement.
- D5. Le champ `journalisee` de `DemandeDeNotification` remplace l'ensemble `DURABLES` de `NotificationService` : c'est l'émetteur qui sait si sa notification est durable. À valider en NOT2.

## Écarts décidés par l'utilisateur (2026-11-12)
| KB | Décision |
|---|---|
| KB-NOT1-1 | Corrigé : le filtre de visibilité compare le rôle exact ; le souligne n'est plus un joker (`GMAO_REFORME` ne voit plus `GMAOXREFORME`) |
| KB-NOT1-2 | Corrigé : `page`, `size`, `centerId` illisibles, corps absent ou illisible = 400 `BAD_REQUEST` ; `{}`, `ids:null`, champ inconnu seul = 400 `VALIDATION_ERROR` ; `ids:[null]` reste 204 (identifiant absent ignoré) |
| KB-NOT1-3 | Corrigé : ne peut être marquée lue que la notification du centre dont l'audience inclut l'utilisateur. Réponse retenue : ignorée, 204, comme un identifiant inconnu ou d'un autre centre (comportement le plus proche de la route, aucune fuite sur l'existence ni l'audience d'une notification). La fenêtre de 30 jours ne s'applique pas au marquage (inchangé) |

## Conséquences
- Goldens modifiés (4) : `role_avec_souligne`, `parametres_illisibles`, `corps_mal_formes`, `marquer_lues` passent en `*_nouveau_code.approved.json` ; anciens archivés dans `notifications/not1/_ecarts-legacy/*_avant_not1.legacy.json`. Les 20 autres scénarios (liste, pagination, fenêtre, cloisonnement, garde de centre, lecture-tout, diffusion, rejeu, après-commit, purge, cron) sont identiques.
- Postgres : la requête `POSITION(CAST(? AS VARCHAR) IN colonne)` est vérifiée sur H2 (mode PostgreSQL) seulement ; à rejouer sur PostgreSQL réel avant mise en production.
- `FrontieresDesContextesTest` verrouille la disparition des quatre classes et l'usage du seul langage publié.
- Règles de gestion : RG-NOT-005 mise à jour ; annexe de traçabilité mise à jour. Glossaire Notifications : termes `Session`, `Emission`, `Pagination du journal` proposés.
- Docs fonctionnelle et maquette : non régénérées ici.
