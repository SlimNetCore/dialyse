---
status: proposed
date: 2026-11-12
context: Notifications (generic, SD13)
sources: [ADR 0140, NotificationJournalPort.java, NotificationRestController.java, NotificationService.java, V29__notification_journal.sql, V49__notification_destinataire.sql]
---
# Glossaire — Notifications

| Terme métier | Définition | Identifiant cible | Nom legacy | Synonymes à bannir | Règle / exemple | Statut |
|---|---|---|---|---|---|---|
| Notification | Message adressé aux personnes d'un centre pour les prévenir d'un fait métier, conservé au journal | `Notification` | `Alerte` (port), `AlerteResponse` (JSON), `notification_evenement` | alerte (réservé à l'Alerte de direction de Pilotage), événement | l'UI dit « centre de notifications » ; le JSON garde `AlerteResponse` | proposed |
| Demande de notification | Ordre d'un contexte émetteur de notifier : centre, type, contenu, audience | `DemandeDeNotification` | `notifyXxx(...)` | message, event | publiée dans `notifications.v1` | proposed |
| Type de notification | Code choisi et documenté par l'émetteur ; Notifications ne l'interprète pas | `TypeDeNotification` | `type` | catégorie | codes existants conservés (le front en dépend) | proposed |
| Contenu | Paires clé/valeur de texte livrées avec la notification pour que l'UI compose son affichage | `Contenu` (Map<String,String>) | `payload` | données | types JDK, pas d'objet métier | proposed |
| Audience | Ceux qui doivent voir la notification : un ou plusieurs rôles du centre, ou un utilisateur précis ; vide ⇒ tous | `Audience` | `target_roles`, `target_user_id` | destinataires (pluriel flou) | rôle sans préfixe `ROLE_` | proposed |
| Destinataire visé | Utilisateur désigné seul destinataire | `DestinataireVise` | `target_user_id` | — | prime sur les rôles à la lecture (à vérifier au code) | proposed |
| Journal | Registre durable des notifications d'un centre, retrouvées à la connexion | `NotificationsDuJournal` (port interne) ; `JournalDesNotifications` (port publié `notifications.v1`) | `NotificationJournalPort` (supprimé, NOT1) | historique (réservé à l'historique des alertes de Pilotage) | borné au centre | proposed |
| Lue / non lue | État propre à chaque utilisateur pour chaque notification | `Lecture` | `notification_lecture`, `lue` | vue, traitée | marquer lu = idempotent ; une notification d'un autre centre est ignorée | proposed |
| Tout marquer lu | Marquer lues toutes les notifications récentes visibles de l'utilisateur | `ToutMarquerLu` | `lecture-tout` | — | respecte le filtre d'audience | proposed |
| Identifiant déterministe | Identifiant calculé par l'émetteur pour qu'un fait rejoué ne crée pas de doublon | `IdentifiantDeterministe` | `existe(id)` | clé d'idempotence | — | proposed |
| Diffusion en temps réel | Envoi immédiat aux utilisateurs connectés du centre (STOMP) ; transport fourni par la plateforme | `DiffusionEnTempsReel` (port) | `SimpMessagingTemplate` | push (technique) | après commit de l'émetteur | proposed |
| Purge | Suppression nocturne (03:40) des notifications et lectures plus anciennes que la durée de conservation | `PurgerLesNotifications` | `NotificationPurgeScheduler`, `purger` | nettoyage | durée à confirmer | proposed |
| Fenêtre de consultation | Les 30 derniers jours : le journal ne propose que ces notifications (le marquage « lu » n'y est pas borné) | `DureesDuJournal.FENETRE_DE_CONSULTATION` | `FENETRE` | historique | — | proposed |
| Conservation | Durée de garde avant purge : 60 jours | `DureesDuJournal.CONSERVATION` | `CONSERVATION` | rétention (technique) | durée à confirmer (Q10) | proposed |
| Pagination du journal | Page (à partir de zéro) et taille (1 à 100, 50 par défaut) d'une consultation du journal | `PaginationDuJournal` | `page`, `size`, `TAILLE_MAX` | — | NOT1 | proposed |
| Émission | Ce qu'un émetteur demande au cas d'usage `NotifierLesDestinataires` (centre, type, contenu, audience, identifiant déterministe, journalisée) | `Emission` | `send(...)` | envoi | « journalisée » : conservée au journal, sinon simple diffusion ; NOT1 | proposed |
| Session | Qui consulte : centre retenu, compte et rôles de l'utilisateur (sans `ROLE_`) | `Session`, `SessionCourante` | `TenantScope`, `CenterAccessGuard` | contexte de sécurité | ACL `SessionLegacy` ; NOT1 | proposed |
| Catalogue de l'émetteur | Types, clés du contenu et audience des notifications d'un émetteur, construits chez lui en `DemandeDeNotification` puis confiés au port publié par son ACL sortante (PL13a : Patient et couverture, Dossier médical ; PL13b : Séance ; PL13c : Planification) | `CatalogueDesNotificationsDeLaFiche`, `CatalogueDesNotificationsDuDossierMedical`, `CatalogueDesNotificationsDeLaSeance`, `CatalogueDesNotificationsDeLaPlanification` | méthodes `notifyXxx` de `NotificationService` | message | Notifications n'invente aucun message ; ADR 0153, 0154, 0155 | proposed |
| Homonymes | « Alerte » : notification (Notifications) ≠ Alerte de direction (Pilotage, seuil dépassé) ≠ alerte d'anémie (Dossier médical, déclencheur) ; « Lue » ≠ « traitée » ; « Historique » ≠ journal | — | — | — | toujours qualifier le contexte | proposed |
