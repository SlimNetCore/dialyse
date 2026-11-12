---
status: proposed
date: 2026-11-12
sources: ADR 0140, ADR 0141, .ddd/safety-net/not1-doc1-filet.md, KB-NOT1-1..3
---

# NOT1 - realisation

Module `backend/contexts/notifications` (contexte Notifications, ADR 0140) : journal, 3 routes `/api/v1/notifications`, purge de 03:40, port `DiffusionEnTempsReel`. Refactoring sur place (ADR 0024) : memes tables, URL, roles, JSON, codes. Aucun flag, aucune facade.

## Cree (contexts/notifications)
- pom + enregistrement : `backend/contexts/notifications/pom.xml`, `backend/pom.xml` (module), `backend/platform/pom.xml` (dependance), `.ddd/arch-rules.json` (module).
- domain : `Notification`, `Audience` (roles exacts), `TypeDeNotification`, `NotificationId`, `PaginationDuJournal`, `DureesDuJournal`, `NotificationInvalide`.
- application : `ConsulterLeJournal`, `MarquerLesNotificationsLues`, `ToutMarquerLu`, `PurgerLesNotifications`, `NotifierLesDestinataires`, `Emission`, `Session`, `NotificationDuJournal` ; ports `NotificationsDuJournal`, `SessionCourante`, `Horloge`, `Identifiants`, `AccesRefuse`.
- infrastructure : `persistence/NotificationsDuJournalJdbc`, `acl/legacyidentite/SessionLegacy`, `systeme/HorlogeDuSysteme`, `systeme/IdentifiantsAleatoires`.
- interfaces : `http/JournalDesNotificationsControleur`, `NotificationReponse`, `ErreursDuJournalAdvice` ; `planifie/PurgeNocturneDesNotifications` ; `publie/NotifierPublie`, `JournalDesNotificationsPublie`.
- publishedlanguage (`notifications.v1`) : `Notifier`, `DemandeDeNotification`, `Audience`, `DiffusionEnTempsReel` (fourni par la plateforme), `JournalDesNotifications`.
- configuration : `NotificationsConfiguration`.

## Plateforme
- Supprimes (grep -w avant) : `NotificationRestController`, `NotificationJournalPort`, `NotificationJournalJdbcAdapter`, `NotificationPurgeScheduler`, test `NotificationRestControllerTest` ; 4 entrees retirees de `covered.json`.
- Modifies (tous couverts) : `NotificationService` (implemente `DiffusionEnTempsReel`, utilise `JournalDesNotifications` ; envoi STOMP extrait dans `diffuser`, comportement identique) ; 7 ACL d'emetteurs (`NotificationsDuCentreLegacy`, `AlertesDeLOptimisationLegacy`, `AlertesDuPlacementLegacy`, `NotificationsDeLaLiberationLegacy`, `NotificationsDeLaPresenceLegacy`, `RappelsFournisLegacy`, `NotificationsDesPeremptionsLegacy`) : seul le type du journal change.
- Aucun nouveau fichier de production en plateforme (le hook bloque les fichiers non couverts) ; hook jamais contourne, `seamAllowed` reste `[]`.

## Tests
- Module : 79 tests verts (domaine 16, application 15, contrat journal x2 = 24 dont meme jeu en memoire et JDBC H2, controleur 5, planificateur 3, langage publie 8, ArchUnit 8).
- Plateforme : filet NOT1 20 + 4 scenarios (24 golden-tests : 12 routes + 8 diffusion/purge ; 20 inchanges, 4 modifies a dessein), tests existants adaptes (journal+service, ACL, GMAO), `FrontieresDesContextesTest` (+2 regles), `ReglesDeGestionDocumentationTest`, `GoldensNouveauCodeGardeTest`.
- 3 passes ciblees vertes (1 normale, 2 en ordre aleatoire) ; `check-arch` : 0 violation.

## Goldens modifies (un a un, apres lecture du `.received`)
| Golden | KB | Nouveau | Ancien archive |
|---|---|---|---|
| `role_avec_souligne` | KB-NOT1-1 | `role_avec_souligne_nouveau_code.approved.json` | `_ecarts-legacy/…__role_avec_souligne_avant_not1.legacy.json` |
| `parametres_illisibles` | KB-NOT1-2 | `…_nouveau_code.approved.json` | `…_avant_not1.legacy.json` |
| `corps_mal_formes` | KB-NOT1-2 | `…_nouveau_code.approved.json` | `…_avant_not1.legacy.json` |
| `marquer_lues` | KB-NOT1-3 | `…_nouveau_code.approved.json` (lectures 4 -> 3) | `…_avant_not1.legacy.json` |

## Ecarts et decisions
KB-NOT1-1, 2, 3 corriges sur decision de l'utilisateur ; KB-NOT1-3 : notification hors audience ignoree (204), voir ADR 0141. Aucun autre ecart de parite.

## Docs
ADR 0141, `known-bugs.md` (CORRIGE 2026-11-12), glossaire notifications, RG-NOT-005, annexe de tracabilite (`JournalDesNotificationsControleur`, `PurgeNocturneDesNotifications`), 3 docs d'architecture (noms de classes).

## Questions ouvertes
- `Notifier`/`NotifierLesDestinataires` sont livres et testes mais non appeles avant NOT2 ; champ `journalisee` (ex `DURABLES`) et comportement « journal en panne » (propagation vs temps reel prioritaire) a trancher en NOT2.
- `NotificationService` implemente `DiffusionEnTempsReel` (aucun nouveau fichier de plateforme a cause du hook) : un adaptateur dedie en NOT2 ?
- Requete `POSITION(CAST(? AS VARCHAR) IN …)` verifiee sur H2 (mode PostgreSQL) uniquement.
- Retention : 60 jours (docs d'architecture disaient 30), Q10 toujours ouverte.
- Doc fonctionnelle/maquette non regenerees.
