---
status: accepted
date: 2026-11-13
sources: canvas-notifications.md, ADR 0049, 0053, 0143, 0153, 0154, pl13b-realisation.md, pl13c-realisation.md
---

# ADR 0155 - PL13c : la Planification construit ses notifications

## Contexte
Refactoring sur place (ADR 0024), aucun changement de comportement. Les messages de la Planification passaient par ses langages publiés `NotificationsDeLaPresence`, `NotificationsDeLaLiberation`, `AlertesDuPlacement`, `AlertesDeLOptimisation` et `NotificationsDesAbsencesDInfirmiers`, fournis par cinq ACL `*Legacy` de la plateforme vers 17 méthodes `notifyXxx` de `NotificationService`.

## Décisions
1. `CatalogueDesNotificationsDeLaPlanification` (`contexts/planification/infrastructure/acl/notifications`) construit les `DemandeDeNotification` : `INFIRMIER_SOUS_EFFECTIF/SUREFFECTIF`, `INFIRMIER_ROULEMENT_MODIFIE/REMPLACEMENT_AFFECTE` (compte visé), `INFIRMIER_ABSENCE_ENREGISTREE/DECLAREE/RETIREE`, `LIBERATION_PLACES_EN_ECHEC`, `OPTIMISATION_PROPOSITION`, `REPLANIFICATION_NOCTURNE`, `SEANCES_DEPLACEES`, `GENERATEUR_INDISPONIBLE`, `ISOLEMENT_IMPOSSIBLE`, `PATIENT_REPLACE_ISOLEMENT`, `RETOUR_SALLE_ORDINAIRE_PROPOSE`. Toutes durables. Mêmes clés, même ordre (HashMap), même audience, mêmes identifiants déterministes (UUID v3, mêmes chaînes).
2. Cinq ACL sortantes vers `notifications.v1` (`NotifierEnDeuxTemps`, `DiffusionEnTempsReel`, `JournalDesNotifications`) implémentent les ports publiés existants (les cas d'usage sont inchangés) ; une émission commune reproduit l'ancien `NotificationService.emettre` : contrôle `existe` puis repli sur violation d'intégrité concurrente, temps réel après commit pour les identifiants déterministes et pour `SEANCES_DEPLACEES`, immédiat sinon.
3. Supprimés : les cinq ACL `*Legacy` de la plateforme (et leur test, repris chez la Planification) ; 17 méthodes de `NotificationService` (présence, roulement, remplacement, absences d'infirmier, libération, optimisation, replanification, séances déplacées, générateur, isolement, retour en salle ordinaire) ; leurs types quittent `DURABLES`. Restent en plateforme (PL13d) : absences à qualifier, lots proches de la péremption, réforme proposée, saisie `ABSENCE`.
4. pom : la Planification dépend de `contexts-notifications` (langage publié). ArchUnit : la Planification ne dépend ni de la plateforme, ni des internes de Notifications, ni des autres émetteurs ; règles anti-retour (classes supprimées, méthodes dans `NotificationService`).

## Conséquences
Goldens non modifiés. Tests de message chez la Planification (clés, ordre, audience, durabilité) et des ACL (une seule fois, après commit, rollback, concurrence, panne). Les deux tests de plateforme qui imposaient un identifiant (`DiffusionEtPurgeCharacterizationIntegrationTest`, scénarios de rejeu et d'après-commit) émettent désormais l'alerte par `NotifierEnDeuxTemps` avec l'identifiant du scénario : les ports de la Planification déduisent l'identifiant de leurs paramètres.
