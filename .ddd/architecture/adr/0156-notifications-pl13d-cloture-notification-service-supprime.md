---
status: accepted
date: 2026-11-13
sources: ADR 0140, 0143, 0153, 0154, 0155
---

# ADR 0156 - PL13d : clôture du chantier notifications, `NotificationService` supprimé

## Contexte
Refactoring sur place (ADR 0024), aucun changement de comportement. Restaient dans `NotificationService` : `notifyAbsencesAQualifier`, `notifySaisieInfirmier` (saisie `ABSENCE`), `notifyLotsProchesPeremption`, `notifyReformeProposee`, plus quatre classes de la plateforme (`NotificationsDuCentreLegacy`, `SignalementDesSaisiesLegacy`, `NotificationsDesPeremptionsLegacy`, `GmaoEvenementsReformeListener`) et `SaisieInfirmierNotifier`.

## Décision
1. Chaque message passe chez son émetteur : Absences patients (`ABSENCES_A_QUALIFIER`, `SAISIE_INFIRMIER/ABSENCE`), Stock (`LOTS_PROCHES_PEREMPTION`), GMAO (`REFORME_PROPOSEE`, écouteur de son propre événement `EquipementMisHorsService`). Même schéma que PL13a-c : catalogue (types, ordre des clés, rôles, durabilité, identifiants déterministes), émission en deux temps (journal puis temps réel, après commit pour les identifiants imposés), ACL sortante vers `notifications.v1`.
2. Supprimés : `NotificationService`, `SaisieInfirmierNotifier`, les quatre classes ci-dessus. `JournalDesNotifications` publié reste (utilisé par les ACL pour `existe`).
3. La plateforme ne fournit plus que le transport : `DiffusionEnTempsReelStomp` (destinations, payload et avertissement de la direction identiques à l'ancien `diffuser`).
4. ArchUnit : `NotificationService`, `SaisieInfirmierNotifier` et les `*Legacy` d'émission ne reviennent pas ; Stock, Absences et GMAO ne dépendent ni de la plateforme ni des internes de Notifications.

## Conséquences
- Types, payloads, rôles, destinataires, idempotence et ordre après commit inchangés ; aucun golden modifié.
- Tous les contextes émetteurs dépendent de `contexts-notifications` (langage publié seul) ; aucun ne dépend d'un autre émetteur.
- Les tests d'intégration Spring de la GMAO fournissent un émetteur factice (`GmaoTestApplication`), Notifications étant fourni par la plateforme.
