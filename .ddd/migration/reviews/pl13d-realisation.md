---
status: proposed
date: 2026-11-13
sources: ADR 0140, 0143, 0153, 0154, 0155, 0156, pl13c-realisation.md
---

# PL13d - realisation

## Fait
- Stock / Absences patients / GMAO (emetteur de REFORME_PROPOSEE, ecouteur de son propre `EquipementMisHorsService`) : `infrastructure/acl/notifications/` = catalogue + emission + ACL sortante vers notifications.v1 ; beans dans les configurations de contexte ; pom : `contexts-notifications`. `GmaoTestApplication` fournit un emetteur factice (Notifications est fourni par la plateforme).
- Plateforme : `DiffusionEnTempsReelStomp` (seul transport STOMP, destinations/payloads inchanges). Supprimes : `NotificationService`, `SaisieInfirmierNotifier`, `NotificationsDuCentreLegacy`, `SignalementDesSaisiesLegacy`, `NotificationsDesPeremptionsLegacy`, `GmaoEvenementsReformeListener` + 2 tests repris dans les contextes. `JournalDesNotifications` publie conserve (utilise par les ACL, pas transitoire).
- ArchUnit : `le_service_de_notification_et_les_acl_legacy_d_emission_ne_reviennent_pas`, `stock_absences_et_gmao_ne_parlent_a_notifications_que_par_son_langage_publie`.
- Docs : ADR 0156, referentiel ch.15 + 09 + annexe, canvas notifications, glossaire notifications, covered.json (5 entrees retirees).

## Tests
- Nouveaux (avant le code, sans date du jour) : 7 (Stock), 7 (Absences), 5 (GMAO).
- Modifies : `NotificationJournalIntegrationTest`, `DiffusionEtPurgeCharacterizationIntegrationTest` (type injecte), `FrontieresDesContextesTest`. Aucun golden modifie.
- 3 passes ciblees platform vertes (378 tests ; passes 2 et 3 en runOrder=random) ; passe modules verte.
- check-arch : 0 violation.
- Build complet `-o test -fae` : plateforme 3450 tests, 1 echec : `InventairePl7CharacterizationIntegrationTest.le_contenu_de_la_feuille_de_comptage_...` (date, pre-existant, liste). Tous les modules de contexte verts.
- Glossaires stock / absences-patients / gmao : termes deja presents (alerte de peremption, notification du jour, proposition de reforme), non modifies.
