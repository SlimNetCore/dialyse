---
status: proposed
date: 2026-11-12
sources: ADR 0140, 0141, 0143, 0153, not1-realisation.md, not2-realisation.md
---

# PL13a - realisation

## Fait
- Patient et couverture : `infrastructure/acl/notifications/` = `CatalogueDesNotificationsDeLaFiche` (7 messages) + `NotificationsDeLaFicheParLeCentreDeNotifications` (ports NotificationsDeLaFiche/Pec/Attestations) ; bean dans `PatientCouvertureConfiguration`.
- Dossier medical : `CatalogueDesNotificationsDuDossierMedical` (PRESCRIPTION_CHANGED, OBSERVANCE_NON_RESPECTEE durable) + `NotificationsDuDossierMedicalParLeCentreDeNotifications` (NotificationsDeLAnemie, SignalementDesPrescriptions, apres commit) ; nouveau port `SaisieDeLAdministrationSignalee` (saisie ANEMIE, PL13b) fourni par `SaisieInfirmierNotifier`.
- Plateforme : 3 ACL `*Legacy` supprimees ; 11 methodes `notifyXxx` retirees de `NotificationService` ; OBSERVANCE_NON_RESPECTEE retire de DURABLES.
- Regle ArchUnit anti-retour (`FrontieresDesContextesTest`) ; deux contextes : internes de Notifications interdits. pom : dependance `contexts-notifications`.
- Docs : ADR 0153, referentiel ch.15 + annexe, glossaires (notifications, patient-couverture, dossier-medical), covered.json (3 entrees retirees).

## Tests
- Nouveaux : catalogues (cles, ordre, audience, durabilite, NPE si identite incomplete) et ACL (apres commit, rollback, journal ignore, panne) dans les deux contextes.
- Modifies (appels aux methodes supprimees) : `NotificationJournalIntegrationTest` et `DiffusionEtPurgeCharacterizationIntegrationTest` appellent le port `NotificationsDeLaFiche` (golden `evenement_ponctuel` inchange) ; `NotificationServiceSaisieInfirmierTest` : evenement ponctuel via `notifySeanceUpdated`, test PRESCRIPTION_CHANGED deplace chez l'emetteur.
- Ecart rattrape : golden PC1 « ligne heritee aux colonnes nulles » (500 a la modification) = NPE de `Map.of` ; reproduit par `requireNonNull`. Aucun golden modifie.
- 3 passes ciblees vertes (2 et 3 en runOrder=random), check-arch 0. Build complet : voir retour.
