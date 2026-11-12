---
status: accepted
date: 2026-11-12
sources: canvas-notifications.md, ADR 0140, 0141, 0143, not1-realisation.md, not2-realisation.md, pl13a-realisation.md
---

# ADR 0153 - PL13a : Patient et couverture et Dossier médical construisent leurs notifications

## Contexte
Refactoring sur place (ADR 0024) : aucun changement de comportement. Le catalogue RG-NOT (textes, déclencheurs) passe aux émetteurs (canvas Notifications). Après NOT2, `NotificationService` (plateforme) composait encore les messages de ces deux contextes derrière trois ACL `*Legacy`.

## Décisions
1. Chaque contexte porte son **catalogue** (`CatalogueDesNotificationsDeLaFiche`, `CatalogueDesNotificationsDuDossierMedical`, `infrastructure/acl/notifications`, codes de type et clés documentés en Javadoc) qui construit la `DemandeDeNotification` : type, contenu (HashMap, mêmes clés), audience, journalisée.
2. Une **ACL sortante par émetteur** vers `notifications.v1` (`NotifierEnDeuxTemps` + `DiffusionEnTempsReel`) fournit les ports déjà publiés : journal puis diffusion, après commit quand le port l'exigeait (observance, prescription), immédiate sinon (fiche, PEC, attestation). Aucune dépendance à la plateforme ni à un autre émetteur (ArchUnit des deux contextes : internes de Notifications interdits).
3. Supprimés : `NotificationsDeLaFicheParLaPlateforme`, `NotificationsDeLAnemieLegacy`, `SignalementDesPrescriptionsLegacy` ; dans `NotificationService` les 11 méthodes `notifyPatient*`, `notifyPec*`, `notifyAttestation*`, `notifyPrescriptionChanged`, `notifyObservanceNonRespectee` ; `OBSERVANCE_NON_RESPECTEE` quitte `DURABLES` (c'est l'émetteur qui déclare « journalisée »). Règle ArchUnit anti-retour (`FrontieresDesContextesTest`).
4. La saisie temps réel `SAISIE_INFIRMIER / ANEMIE` n'est pas du catalogue du Dossier médical (saisie de l'infirmier, PL13b) : nouveau port publié `SaisieDeLAdministrationSignalee`, fourni par `SaisieInfirmierNotifier` (plateforme) jusqu'à PL13b ; `NotificationsDeLAnemie` reste inchangé.
5. Parité : une valeur absente dans l'identité du patient ou le nom de la PEC fait toujours échouer l'émission (NPE, comme `Map.of` avant) : le golden PC1 « ligne héritée aux colonnes nulles » (500) reste inchangé.

## Conséquences
Goldens non modifiés. Trois tests de la plateforme appelaient les méthodes supprimées : ils passent par les ports publiés (mêmes goldens) ou par une autre méthode de `NotificationService` (test unitaire) ; les tests de message vivent chez les émetteurs. L'ordre des clés JSON d'un message à contenu non ordonné (PATIENT_*, PEC_*) n'est pas garanti (`Map.of` avant, HashMap maintenant, stable).
