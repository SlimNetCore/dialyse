---
status: accepted
date: 2026-11-12
sources: ADR 0141, .ddd/migration/reviews/not2-realisation.md
---

# ADR 0143 - NOT2 : `NotifierEnDeuxTemps` dans notifications.v1

## Contexte
`Notifier.notifier` journalise ET diffuse en un appel, à faire après le commit. Or la plateforme journalise dans la transaction de l'émetteur (annulée avec elle) et ne diffuse qu'après le commit ; une panne du journal est avalée sans identifiant imposé, propagée avec. Déléguer à `Notifier` changeait ces règles.

## Décision (option a, validée par l'utilisateur)
Nouvelle opération du langage publié : `NotifierEnDeuxTemps.journaliser(DemandeDeNotification) -> Optional<NotificationAEnvoyer>`.
- Temps 1 (dans la transaction de l'émetteur) : journalise ; identifiant déterministe déjà au journal -> `Optional.empty()` ; panne + identifiant imposé -> exception ; panne sans identifiant imposé -> avalée, rendue dans `panneDuJournal` (l'émetteur la consigne), diffusion due sans identifiant.
- Temps 2 (après commit, à l'émetteur) : `DiffusionEnTempsReel.diffuser(...)` avec `NotificationAEnvoyer`.
- Le contenu de l'émetteur est conservé (ordre des clés inclus) ; la clé `targetRoles` n'est ajoutée que si l'audience a des rôles ou si l'émetteur l'avait déjà (pas de clé ajoutée aux évènements ouverts à tous : JSON STOMP inchangé).
- Cycle de dépendance rompu sans `@Lazy` : le cas d'usage `JournaliserLaNotification` ne dépend pas du transport ; `NotificationService` (qui fournit le transport) injecte `NotifierEnDeuxTemps`, pas `Notifier`.

## Conséquences
`Notifier` reste pour les futurs émetteurs hors transaction. `JournalDesNotifications` (transitoire) n'est plus utilisé par `NotificationService` ; retrait en PL13 avec les ACL `*Legacy`. Aucune règle de gestion modifiée.
