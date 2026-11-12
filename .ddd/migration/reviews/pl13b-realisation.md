---
status: proposed
date: 2026-11-13
sources: ADR 0140, 0141, 0143, 0153, 0154, pl13a-realisation.md
---

# PL13b - realisation

## Fait
- Séance : `infrastructure/acl/notifications/` = `CatalogueDesNotificationsDeLaSeance` (SEANCE_CREATED/VALIDATED/PARAMEDICAL_SAVED/CONSOMMABLE_CHANGED/SUPPRIMEE/DEVERROUILLEE, SAISIE_INFIRMIER 7 types, SEANCES_A_REGULARISER durable + id déterministe) + 3 ACL (`DiffusionsDeSeanceParLeCentreDeNotifications`, `RappelsDeRegularisationParLeCentreDeNotifications`, `SignalementDesAdministrationsSaisiesParLeCentreDeNotifications`) + `EmissionDesNotificationsDeLaSeance` (journal puis diffusion). Beans dans `SeanceConfiguration`, pom : dépendance `contexts-notifications`.
- Nouveau port publié seance.v1 `SignalementDesAdministrationsSaisies` (ANEMIE) consommé par le Dossier médical ; port transitoire `SaisieDeLAdministrationSignalee` supprimé (Seance -> dossier-medical aurait créé un cycle Maven).
- Supprimés : `DiffusionsFournies`, `RappelsFournis`, `DiffusionsParLeLegacy`, `RappelsParLeLegacy`, `DiffusionsFournieesLegacy`, `RappelsFournisLegacy` (+ leurs tests) ; dans `NotificationService` : 9 méthodes notifySeance* / notifySeancesARegulariser (dont `notifySeanceMedicalSaved`, sans appelant) ; SEANCES_A_REGULARISER hors DURABLES.
- Conservés (PL13d) : `NotificationService.notifySaisieInfirmier` et `SaisieInfirmierNotifier`, réduit à la saisie ABSENCE (utilisée par `SignalementDesSaisiesLegacy`, Absences). Écart avec la consigne « supprimer SaisieInfirmierNotifier » : il reste un émetteur hors Séance.
- ArchUnit : `les_notifications_de_la_seance_ne_passent_plus_par_la_plateforme`, `la_seance_ne_depend_ni_de_la_plateforme_ni_d_un_autre_emetteur`, `les_methodes_de_seance_ne_reviennent_pas_dans_le_service_de_notification`.
- Docs : ADR 0154, référentiel ch.15 + annexe, glossaires (notifications, seance), covered.json inchangé (glob `acl/seance/*.java` toujours valide : `FichesFourniesParPatientCouverture` reste).

## Tests
- Nouveaux (écrits avant le code) : `CatalogueDesNotificationsDeLaSeanceTest` (clés, ordre, audience, durabilité, NPE), `NotificationsDeLaSeanceParLeCentreDeNotificationsTest` (après commit, rollback, journal ignoré, panne), `RappelsParLeCentreDeNotificationsContratTest` (même contrat que le fake).
- Modifiés : `TraducteursDesLangagesPubliesFournisTest`, `TraducteursDeLaSuppressionTest`, `NotificationServiceSaisieInfirmierTest` (appels aux méthodes supprimées), `RappelsContrat` (Javadoc). Aucun golden modifié.
- 3 passes ciblées (2 et 3 en runOrder=random) : seuls échecs = 10 tests date-dépendants, `SeanceScanIntegrationTest` (9) et `SeanceDeverrouillageIntegrationTest` (1), identiques sur HEAD propre (git stash -u, constat du 2026-11-13, vendredi). Non liés aux notifications. check-arch : 0 violation.
- Build complet `-o test -fae` : 3462 tests, 13 échecs, 0 erreur = les 10 ci-dessus + `LecturesFinancieresDirectionCharacterizationIntegrationTest` (1) + `DirectionGmaoCharacterizationIntegrationTest` (2), soit les 13 date-dépendants signalés avant la slice (ces deux dernières classes non rejouées sur HEAD propre).
- Proposition (non appliquée) : injecter l'horloge du centre (`HorlogeDuCentre` / `Clock` fixe, jour de dialyse programmé) dans ces deux classes, ou fixer le `Clock` sur un jour ouvré du planning du jeu de données.
