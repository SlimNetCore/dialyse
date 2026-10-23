---
status: proposed
date: 2026-10-22
scope: slice absences-patients-a3-detection-de-nuit (non commitée, sur A1 e006fa13 / A2 6448b5e6)
sources: git status/diff, check-arch.ts, state.ts show, fiche de slice, ADR 0040, known-bugs KB-ABS-24..27, verifications-production § 25, glossaire, 06-absences-patients.md, 12 goldens `*_nouveau_code` vs `_ecarts-legacy`
---

# Revue — Absences de patients A3 (détection de nuit)

Contrôles automatiques : `check-arch.ts` **0 violation** (0 en baseline, 0 nouvelle). `state.ts show` : pipeline en phase slices. `scan.ts --incremental`
**non lancé** (il réécrit `.ddd/scan/*`, déjà modifiés dans l'arbre ; revue en lecture seule). Aucun build/test lancé (consigne).

Goldens : 12 `*_nouveau_code` présents, 12 `_ecarts-legacy/*.legacy.json`, aucun golden caduc orphelin. Diffs relus (garde_de_centre 500→400,
deux_passages −1 notification, etat_et_date_d_evenement, notification_a_qualifier 8/5→7/4, echec_au_milieu, absence_declaree_conservee) :
chacun correspond à une décision (KB-22, 24, 25, 27, 28). Aucun autre changement de comportement trouvé dans le code relu.

## Constats

| # | Sévérité | Domaine | Constat | Preuve | Correction recommandée |
|---|---|---|---|---|---|
| 1 | major | legacy / métier (argent) | **L'effet des sorties sans date n'est pas limité au premier démarrage.** Un patient TRANSFERE/DECEDE/GREFFE/GUERRI sans `date_evenement_etat` est attendu sur **tous** les jours passés : chaque nuit crée une nouvelle absence « à qualifier » valorisée (J-1), sans fin, jusqu'à ce que la date soit saisie. La fiche (point 1) présente le risque comme lié au rattrapage de 31 jours et propose de désactiver `app.absences.startup-catchup` : ça ne coupe pas le cron. L'ADR (Conséquences) et RG-ABS-041 ne disent pas non plus que l'effet se répète. | `PatientsAttendusJdbc.java:61-62` ; fiche `absences-patients-a3-detection-de-nuit.md:87` ; ADR 0040 l.50 ; golden `patient-attendu/etat_et_date_d_evenement_nouveau_code` (J-7..J-1 pour les 4 états sans date) | Avant le déploiement, rendre **obligatoire** la saisie des dates (§ 25.2 = 0 ligne), au lieu de la laisser au choix. Corriger la fiche, l'ADR et RG-ABS-041 (« chaque nuit tant que la date manque »). Faire confirmer la décision par l'utilisateur maintenant que l'effet continu est connu. |
| 2 | minor | archi / idempotence | **Notification : on vérifie puis on envoie, sans verrou.** Deux instances qui lancent le cron 02:30 ensemble passent toutes les deux `absencesAQualifierEnAttente` et créent 2 notifications. `NotificationService.send` accepte pourtant un `idImpose` déterministe qui dédoublonne (`journal.existe`). Détection et réconciliation restent idempotentes en multi-instance : contrainte `UNIQUE (center_id, patient_id, date_seance)` (V13:69) → `EcritureConcurrente` → ignorée ; réconciliation relue `FOR UPDATE`. | `NotificationDesAbsencesAQualifier.java:37-40` ; `NotificationService.java:441-454` ; `AbsencesJdbc.java:161-163` | Passer un identifiant déterministe (centre + jour du centre, UUID v3) via `NotificationsDuCentreLegacy`, ou documenter que le déploiement tourne sur une seule instance. |
| 3 | minor | métier (à valider) | **« Ouverte » = lue par personne, tous rôles confondus.** Un INFIRMIER ou un MEDECIN qui la lit ferme la notification pour l'ADMIN et la SECRETAIRE, qui sont ceux qui qualifient. À l'inverse, une notification jamais lue bloque les suivantes pendant 30 jours avec des comptes périmés. Les comptes ne couvrent que 7 jours : une absence non qualifiée après J-7 n'est plus jamais rappelée. Ces trois points sont conformes à la décision, mais ils restent à valider (fiche, points 2 et 3). | `NotificationsDuCentreLegacy.java:39-41` ; `NotificationDesAbsencesAQualifier.java:48-59` | Faire valider explicitement : (a) lecture par un rôle qui qualifie ; (b) fenêtre 7 j contre tous les mois ouverts ; (c) durée de 30 j. |
| 4 | minor | métier / tests | **Échec isolé, mais absence perdue sans bruit.** Une absence qui échoue chaque nuit (forfait invalide) n'est jamais créée. Après 7 nuits elle sort de la fenêtre et n'apparaît plus que dans des `log.warn`, sans compte dans la notification ni dans la réponse du job. | `DetectionAbsences.java:66-70` ; `ControleDeNuitDesAbsences.java:53-55` | Suggestion : ajouter le nombre d'échecs à la notification ou à une alerte durable, ou étendre la fenêtre aux jours en échec. |
| 5 | minor | performance | **N+1 en régime normal.** La requête « patients attendus » n'exclut pas les jours qui ont déjà une absence. Chaque nuit, pour chaque patient absent sur les 7 jours (et désormais chaque sortant sans date), on ouvre une transaction et on lance un `existe`. La réconciliation fait aussi un `seances.realisee` par absence. | `PatientsAttendusJdbc.java:53-65` ; `DetectionAbsences.java:65-79` ; `ReconciliationAbsences.java:62` | Ajouter `AND NOT EXISTS (SELECT 1 FROM absence_patient a WHERE a.center_id=p.center_id AND a.patient_id=p.id AND a.date_seance=?)` dans la requête du jour. Le contrôle `existe` dans la transaction reste la garde : l'isolement n'est pas touché. |
| 6 | minor | legacy (filet) | **`covered.json` élargi de façon permanente.** `DomainServiceConfig`, composition root legacy de nombreux contextes, est maintenant éditable sans filet pour toute slice future. Seul le retrait d'un bean a été caractérisé. | `.ddd/safety-net/covered.json` (diff, dernière entrée) | Le retirer de `covered` après le commit, ou le noter comme exception ponctuelle dans `seams.md`. |
| 7 | minor | docs | **ADR 0040 encore `status: proposed`** alors que la slice est `built`. Le glossaire « Patient attendu » (« pas sorti ») contredit la nouvelle règle : un sortant sans date est attendu. | ADR 0040 l.2 ; glossaire `absences-patients.md:23` | Passer l'ADR en `approved` après validation. Préciser la définition du glossaire. |
| 8 | nit | tests | **Javadoc et noms périmés.** Les supports de caractérisation disent « jour d'exécution UTC / Clock.systemUTC » et la méthode s'appelle `attendreSiProcheDeMinuitUtc`, alors que le calcul est fait au fuseau Africa/Algiers. Le filet dépend de la date réelle (dates relatives, attente près de minuit) : c'est la méthode A1/A2 assumée. Les tests unitaires et applicatifs, eux, utilisent bien `Clock.fixed`. | `DetectionAbsencesCharacterizationSupport.java:65-67,230` ; `CycleDeVieAbsencesCharacterizationSupport.java:65,240` ; `ConsultationAbsencesCharacterizationSupport.java:54` | Renommer en `attendreSiProcheDeMinuitDuCentre` et corriger la Javadoc. |
| 9 | nit | legacy (code mort) | Des Javadoc de caractérisation citent encore des classes retirées (`AbsencePatientScheduler`, `AbsenceDonneesJdbcAdapter`). Aucune référence de code restante à `domain.absence.*` ou aux services retirés. | `RattrapageAuDemarrage…IntegrationTest.java:16` ; `ControleDeNuit…IntegrationTest.java:12` ; `PeriodeFactureeAbsences…IntegrationTest.java:16` | Mentionner « (retiré en A3) ». |
| 10 | nit | hexagonal / langage | `NotificationsDuCentre` est un port **requis** (implémenté par la plateforme), mais il est rangé dans `publishedlanguage/` et prend des `UUID` bruts. `NotificationsDuCentreLegacy` lit `Instant.now()` en dur. `PeriodeDeDetection` n'impose pas `du ≤ au` dans son constructeur canonique. | `publishedlanguage/NotificationsDuCentre.java` ; `NotificationsDuCentreLegacy.java:41` ; `PeriodeDeDetection.java:20-24` | Suggestions : le déplacer dans `application` (port sortant) ; injecter `Clock` ; valider l'ordre dans le constructeur. |

## Points vérifiés sans constat

- **Fenêtre J-7 par centre** : `PeriodeDeDetection.recente(aujourd'hui du centre, 7)` = J-7..J-1, jour courant exclu. Le cron 02:30 et `FuseauParDefaut` lisent la même propriété (`ControlerLesAbsences.java:67-68`, `ControleDeNuitDesAbsences.java:36`).
- **Transactions** : une `UniteDeTravail` par absence, aucune transaction englobante dans le câblage. Un incident de centre est capturé par centre (`ControlerLesAbsences.java:56-61`).
- **Réconciliation** : curseur `a.id > ? ORDER BY a.id LIMIT ?` (`AbsencesJdbc.java:142-143`). Pas de page sautée quand des lignes passent ANNULEE. Le domaine refuse une absence DECLAREE, qualifiée ou facturée (`AbsencePatient.java:148-167`).
- **Centres inactifs** : `COALESCE(actif, TRUE)` (`CentresActifsJdbc.java:24`).
- **`/rattrapage-detection`** : 400/415, `fin`/`finRamenee`/`echecs` additifs `NON_NULL`, JSON `{creees, annulees}` inchangé (`DetectionControleur.java:47-56`).
- **Fuseau** : `HorlogeDesCentres` remplace `Clock` dans A1/A2. Tests `FuseauDuCentreTest` sur `Clock.fixed` entre 23:00 et 00:00 UTC. Algiers n'a pas d'heure d'été.

## Verdict : **pass-with-actions**

Top 3 actions :
1. Constat 1 : corriger la mitigation (le cron répète l'effet chaque nuit) et rendre obligatoire le § 25.2 = 0 avant déploiement, avec confirmation utilisateur.
2. Constat 3 : faire valider la sémantique de la notification (rôle qui lit, fenêtre 7 j, 30 j), puis passer l'ADR 0040 en `approved`.
3. Constat 2 : identifiant de notification déterministe (ou déclarer le déploiement mono-instance) ; constat 6 : retirer `DomainServiceConfig` de `covered.json`.
