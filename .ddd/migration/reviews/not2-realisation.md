---
status: proposed
date: 2026-11-12
sources: ADR 0140, ADR 0141, ADR 0143, not1-realisation.md
---

# NOT2 - realisation

## Fait
- `NotificationService` (plateforme, seul fichier de production modifie, couvert, aucun BLOQUE) : ~654 -> ~480 lignes, 43 `notifyXxx` aux signatures inchangees ; contenu par un helper unique `contenu(paires...)` (HashMap, meme ordre d'insertion) ; `DemandeDeNotification` construite en un seul endroit (`demande`) ; emission via `NotifierEnDeuxTemps` (ADR 0143, option a).
- Module notifications (notifications.v1) : `NotifierEnDeuxTemps`, `NotificationAEnvoyer` (publishedlanguage), `JournaliserLaNotification` (application), `NotifierEnDeuxTempsPublie` (interfaces/publie), 2 beans dans `NotificationsConfiguration`. Journal dans la transaction de l'emetteur (erreur avalee sans identifiant impose, propagee avec), diffusion apres commit par `NotificationService`. Cycle casse sans `@Lazy` : le cas d'usage ne depend pas du transport.
- Ecart de contrat assume (documente ADR 0143) : `JournaliserLaNotification` n'ajoute pas `targetRoles` aux evenements ouverts a tous (sinon le JSON STOMP changeait, golden `evenement_ponctuel` en echec detecte puis corrige) ; `NotifierLesDestinataires` (Notifier) garde l'ajout systematique, teste.
- Goldens NOT1/DOC1 : aucun modifie ni approuve. Ordre des cles et JSON inchanges.

## Tests
- Nouveau : `JournaliserLaNotificationTest` (8 : journalisation immediate, ordre des cles, non journalisee, rejeu, panne avalee, panne propagee, centre absent, pas de cle ajoutee) sur double en memoire ; le journal reel est couvert par les tests de contrat existants et `NotificationJournalIntegrationTest`.
- Modifie (justifie) : `NotificationServiceSaisieInfirmierTest` - seul le montage (constructeur : `NotifierEnDeuxTemps` au lieu de `JournalDesNotifications`) et les `verify` du journal (signature `enregistrer(Notification)`) ; memes scenarios et assertions de comportement.
- 3 passes ciblees vertes (2 et 3 en `-Dsurefire.runOrder=random`), `check-arch` : 0 violation. Suite complete : voir retour.

## Docs
Aucune regle de gestion modifiee : referentiel et annexe de tracabilite inchanges. ADR 0143 ajoute.

## Restant
Les 7 ACL `*Legacy` d'emission et `JournalDesNotifications` (transitoire) sont retires en PL13.
