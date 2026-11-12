---
status: proposed
date: 2026-11-12
sources: ADR 0140, ADR 0141, not1-realisation.md
---

# NOT2 - realisation (PARTIELLE : decision requise)

## Fait
`backend/platform/.../application/notification/NotificationService.java` (seul fichier modifie, couvert, aucun BLOQUE) : ~654 -> ~480 lignes, 43 methodes `notifyXxx` conservees a l'identique (signatures, surcharges).
- Contenu compose par un helper unique `contenu(paires...)` (HashMap, meme ordre d'insertion donc meme JSON) + helpers de forme (`seanceDuPatient`, `absenceInfirmier`, `patientDansLIsolement`, `avecPatientFacultatif`, `identitePatient`, `visantLeCompte`).
- La `DemandeDeNotification` (notifications.v1) est construite en UN seul endroit (`demande(...)`) ; l'audience est deduite de `targetRoles`/`targetUserId`, le contenu reste inchange (cles de roles incluses). `emettre(demande, apresLeCommit)` execute la logique d'avant (idempotence, journal, apres commit, diffusion).
- Goldens NOT1/DOC1 et tests des emetteurs : inchanges, verts (aucun golden modifie).

## NON fait : delegation a `Notifier` (decision requise, pas d'ADR 0143 ecrit)
Faire de `NotificationService` une facade sur `Notifier.notifier(...)` change le comportement, donc rompt la parite exigee :
1. **Circularite** : `NotifierLesDestinataires` recoit `DiffusionEnTempsReel` = `NotificationService` ; il faudrait un injection paresseuse (`ObjectProvider`).
2. **Journal dans la transaction vs apres commit** : aujourd'hui le journal s'ecrit DANS la transaction de l'emetteur (annule avec elle) et seule la diffusion part apres commit. `Notifier` journalise ET diffuse en un appel, a faire apres commit : le journal passerait hors transaction (alerte perdue si l'ecriture echoue apres commit ; plus d'annulation conjointe).
3. **Panne du journal** : legacy = avale l'erreur et diffuse quand pas d'identifiant impose, propage si impose (REFORME_PROPOSEE et alertes rejouables). `NotifierLesDestinataires` propage toujours et ne diffuse pas.
4. **Ordre du JSON** : `LinkedHashMap` + `targetRoles` ajoutes en fin changeraient l'ordre des cles si le contenu n'inclut pas deja ces cles (contourne ici en les gardant dans le contenu).
Options : (a) ajouter au module Notifications une operation separant journalisation et diffusion differee (`Notifier` v1.1 ou port de planification apres commit) puis rebrancher ; (b) accepter 2-3 comme ecarts voulus (KB + accord utilisateur + ADR 0143). A trancher par l'utilisateur.

## Tests
Passe 1 (normale), passes 2 et 3 (`-Dsurefire.runOrder=random`) ciblees : NotificationService*Test, NotificationJournalIntegrationTest, DiffusionEtPurge (8), *LegacyTest (ACL), InterventionReformeProposee*, FrontieresDesContextesTest : vertes. `check-arch` : 0 violation. Suite complete `./mvnw -o test -fae` : voir le message de retour (log `not2-full.log` du scratchpad).

## Docs
Aucune regle de gestion touchee (comportement identique) : referentiel et annexe de tracabilite inchanges.
