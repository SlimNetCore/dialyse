---
status: proposed
date: 2026-11-13
sources: ADR 0140, 0143, 0153, 0154, 0155, pl13a-realisation.md, pl13b-realisation.md
---

# PL13c - realisation

## Fait
- Planification : `infrastructure/acl/notifications/` = `CatalogueDesNotificationsDeLaPlanification` (15 types, toutes durables, HashMap, audience, identifiants deterministes) + `EmissionDesNotificationsDeLaPlanification` (journal puis temps reel ; `existe` + repli sur violation concurrente ; apres commit pour id deterministe et `SEANCES_DEPLACEES`) + 5 ACL `*ParLeCentreDeNotifications` implementant les ports publies existants (`NotificationsDeLaPresence`, `NotificationsDeLaLiberation`, `AlertesDuPlacement`, `AlertesDeLOptimisation`, `NotificationsDesAbsencesDInfirmiers`). Beans dans `PlanificationConfiguration` ; pom : dependance `contexts-notifications`.
- Supprimes : 5 ACL `*Legacy` de la plateforme + `NotificationsDeLaLiberationLegacyTest` (repris chez la Planification) ; 17 methodes de `NotificationService` (presence, roulement, remplacement, absences d'infirmier, liberation, optimisation, replanification, seances deplacees, generateur, isolement, retour salle ordinaire) ; leurs types hors de `DURABLES`.
- Conserves (PL13d) : `notifyAbsencesAQualifier`, `notifyLotsProchesPeremption`, `notifyReformeProposee`, `notifySaisieInfirmier` + `SaisieInfirmierNotifier`.
- ArchUnit (`FrontieresDesContextesTest`) : `la_planification_ne_depend_ni_de_la_plateforme_ni_des_internes_de_notifications`, `les_notifications_de_la_planification_ne_passent_plus_par_la_plateforme`, `les_methodes_de_planification_ne_reviennent_pas_dans_le_service_de_notification`.
- Docs : ADR 0155, referentiel ch.15 + annexe (RG-NOT), canvas notifications, glossaires (notifications, planification), covered.json (5 entrees retirees).

## Tests
- Nouveaux (ecrits avant le code, sans date du jour) : `CatalogueDesNotificationsDeLaPlanificationTest` (cles, ordre, audience, durabilite, NPE), `NotificationsDeLaPlanificationParLeCentreDeNotificationsTest` (une seule fois, apres commit, rollback, concurrence, panne, ordre).
- Modifies (appels aux methodes supprimees) : `NotificationServiceSaisieInfirmierTest`, `NotificationJournalIntegrationTest` (ports de la Planification), `DiffusionEtPurgeCharacterizationIntegrationTest` (ports ; les scenarios a identifiant IMPOSE - rejeu, apres commit, alerte personnelle - emettent via `NotifierEnDeuxTemps` avec l'identifiant du scenario car les ports derivent l'identifiant de leurs parametres, sinon les alias `<N62>`/`<N63>` du golden changeaient). Aucun golden modifie.
- 3 passes ciblees vertes (2 et 3 en runOrder=random, jeu elargi presence/liberation/infirmier/optimisation/isolement/generateur/notification/frontieres/planning/absence/place). 
- check-arch : 0 violation.
- Build complet `-o test -fae` : plateforme 3460 tests, 1 echec, 0 erreur ; tous les modules de contexte verts. Les 13 tests date-dependants habituels (Seance*, Direction*) sont VERTS a cette heure (build lance apres minuit). Seul echec : `InventairePl7CharacterizationIntegrationTest.le_contenu_de_la_feuille_de_comptage_...` (Stock, 17/12/2027 attendu, 18/12/2027 recu : peremption calculee a partir de l'heure, build a 01h locale / 00h UTC). Hors des 13, sans lien avec les notifications (aucun fichier Stock touche), non rejoue sur HEAD propre (un rejeu isole echoue en resolution Maven sans `-am`). Aucun autre echec.
