---
status: accepted
date: 2026-11-13
sources: canvas-notifications.md, ADR 0088, 0091, 0100, 0143, 0153, pl13a-realisation.md, pl13b-realisation.md
---

# ADR 0154 - PL13b : la Séance construit ses notifications

## Contexte
Refactoring sur place (ADR 0024), aucun changement de comportement. Les messages de la Séance passaient par les langages publiés `DiffusionsFournies` et `RappelsFournis`, fournis par deux ACL de la plateforme (`DiffusionsFournieesLegacy`, `RappelsFournisLegacy`) vers `NotificationService`.

## Décisions
1. `CatalogueDesNotificationsDeLaSeance` (`contexts/seance/infrastructure/acl/notifications`) construit les `DemandeDeNotification` : `SEANCE_CREATED/VALIDATED/PARAMEDICAL_SAVED/CONSOMMABLE_CHANGED/SUPPRIMEE/DEVERROUILLEE`, `SAISIE_INFIRMIER` (7 types de saisie, dont `ANEMIE`), `SEANCES_A_REGULARISER` (durable, identifiant déterministe centre + jour du centre). Mêmes clés, même ordre (HashMap), même audience.
2. Trois ACL sortantes vers `notifications.v1` (`NotifierEnDeuxTemps` + `DiffusionEnTempsReel`) : diffusions de séance (immédiates, échec journalisé et avalé), rappel (journal dans la transaction du cas d'usage, temps réel après commit, `false` si l'identifiant est déjà journalisé), saisie `ANEMIE` (après commit, jamais bloquante).
3. Les ports `DiffusionsFournies`, `RappelsFournis`, leurs traducteurs `DiffusionsParLeLegacy`, `RappelsParLeLegacy` et les deux ACL de la plateforme sont supprimés ; les cas d'usage gardent leurs ports `DiffusionsDeSeance` et `RappelsDeRegularisation`.
4. Le port transitoire `SaisieDeLAdministrationSignalee` (Dossier médical, ADR 0153) disparaît : le Dossier médical dépend du langage publié de la Séance `SignalementDesAdministrationsSaisies` (seance.v1), fourni par la Séance (le sens inverse créerait un cycle Maven). Auteur lu dans la session de la Séance, nom du patient dans `FichesDesPatients`.
5. `NotificationService` : retrait de `notifySeance*` (Created, Validated, MedicalSaved, ParamedicalSaved, Updated, Supprimee, Deverrouillee) et `notifySeancesARegulariser` ; `SEANCES_A_REGULARISER` quitte `DURABLES`. `SEANCE_MEDICAL_SAVED` n'avait aucun appelant : supprimé avec la méthode. Reste en plateforme (PL13d) : `notifySaisieInfirmier` et `SaisieInfirmierNotifier`, réduit à la saisie `ABSENCE` des Absences de patients.
6. ArchUnit : règle anti-retour (classes supprimées, méthodes de séance dans `NotificationService`) ; la Séance ne dépend ni de la plateforme, ni du Dossier médical, ni des internes de Notifications.

## Conséquences
Goldens non modifiés. Tests de message chez la Séance (clés, ordre, audience, durabilité, après commit, rollback, journal ignoré, panne) ; le contrat des rappels passe sur l'ACL réelle. Écart consigné : `SEANCES_A_REGULARISER` ne vérifie plus `existe` avant d'émettre mais laisse le journal répondre « déjà enregistré » (même résultat, un aller-retour de moins).
