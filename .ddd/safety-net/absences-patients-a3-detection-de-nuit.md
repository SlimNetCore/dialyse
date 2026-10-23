---
status: proposed
date: 2026-10-22
slice: absences-patients-a3-detection-de-nuit
sources: [.ddd/tactical/absences-patients.md § 8, 10 (A3), 12 et décisions du 2026-10-21, .ddd/safety-net/absences-patients-a1-consultation.md et -a2-cycle-de-vie.md (méthode, harnais), lecture de AbsencePatientScheduler, AbsencePatientService, AbsenceDonneesJdbcAdapter, AbsencePatientJdbcAdapter, AbsencePatientRestController.rattraperDetection, NotificationService.notifyAbsencesAQualifier]
---
# Filet de caractérisation — A3 détection de nuit des absences (refonte sur place, ADR 0024)

## Périmètre exact
| Entrée | Classes legacy | Verrouillé |
|---|---|---|
| Cron 02:30 `controlerAbsences()` (appel direct de la méthode) | `AbsencePatientScheduler` → `AbsencePatientService.controlerCentre(7)` | oui |
| Rattrapage au démarrage `rattraperAuDemarrage()` (31 jours, sans notification, désactivable) | idem | oui (désactivé par le profil test, activé par le test en positionnant le champ du scheduler) |
| Détection d'un jour : requête « patient attendu » (jour coché, sommeil, admission, états de sortie, séjour limité, fériés, fermetures, séance réalisée) | `AbsenceDonneesJdbcAdapter.patientsAttendusSansSeance`, `AbsencePatientService.detecter`, `AbsencePatient.detectee`, `ValorisationAbsenceService` | oui |
| Réconciliation (annulation d'une absence « à qualifier » dont la séance est réalisée) | `AbsencePatientService.reconcilier`, `AbsencePatientJdbcAdapter.findPaged / saveIfAQualifier`, `AbsencePatient.annuler` | oui |
| `POST /api/v1/absences-patients/rattrapage-detection` | `AbsencePatientRestController.rattraperDetection` → `rattraperPeriode` | oui |
| Notification `ABSENCES_A_QUALIFIER` (journal `notification_evenement` + diffusion STOMP) | `NotificationService.notifyAbsencesAQualifier`, `synthese` | oui (charge utile, comptes) |

## Point d'observation et harnais
Table `absence_patient` (vue compacte ou toutes colonnes), journal des notifications, diffusions du courtier STOMP (`brokerChannel`, intercepteur sans mock) et réponse HTTP (MockMvc, Spring Security et `RoleScopeFilter` réels, H2). Le cron est déclenché par **appel direct** du scheduler (`@Autowired AbsencePatientScheduler`). **Aucun mock du code caractérisé, aucun seam, aucun code de production modifié.**
- Tests : `backend/platform/src/test/java/com/hemodialyse/backend/characterization/absencespatients/detection/` (`DetectionAbsencesCharacterizationSupport` + 6 classes). Goldens : `backend/platform/src/test/resources/characterization/absences-patients/detection/{controle-de-nuit,patient-attendu,reconciliation,absences-existantes,rattrapage-detection,demarrage-et-masse}/*.approved.json` (52 fichiers, tous < 200 lignes).
- **Dates** relatives au jour UTC d'exécution (horloge du legacy `Clock.systemUTC`), alias `<J>`, `<J-4>`… produits par le scrubber pour tout jour de J+5 à J-430 ; aucune date réelle dans les goldens (vérifié par recherche) ; jours de semaine : un patient « attendu un seul jour » est défini par le jour de semaine d'une date relative, jamais par un nom de jour ; attente de minuit UTC si < 2 min.
- **Identifiants** nommés (`<P_AMRANI>`, `<CENTRE_A>`…) ; générés → `<UUID_GENERE_n>` ; horodatages → `<HORODATAGE>`.
- **Échec d'un centre** : provoqué par données (forfait de prix négatif refusé par `ValeurAbsence`), sans mock ; le centre en échec n'a qu'un patient attendu ou le patient fautif est le suivant de l'ordre des identifiants (résultat indépendant de l'ordre de parcours des centres).

## Scénarios (55 tests, 6 classes)
| Classe | Tests | Scénarios |
|---|---|---|
| `ControleDeNuitDesAbsencesCharacterizationIntegrationTest` | 11 | 7 jours hors aujourd'hui ; absence détectée (toutes colonnes) ; idempotence + notification à chaque passage ; notification (comptes hors fenêtre, en retard) ; limite du retard J-3 / J-4 ; aucune notification sans « à qualifier » ; deux centres (valeur et notification propres) ; centre sans patient ; centre inactif ; centre en échec ; échec au milieu d'un centre |
| `PatientAttenduCharacterizationIntegrationTest` | 9 | jours cochés (et aucun jour, jours nuls) ; sommeil (vrai, faux, nul) ; admission (inclusive) ; 19 états × avec/sans date d'événement ; fériés et fermetures (centre concerné seulement, hors fenêtre) ; séance réalisée par statut ; séance d'un autre centre ; séance chaque jour |
| `ReconciliationDesAbsencesCharacterizationIntegrationTest` | 10 | annulation (toutes colonnes) ; statuts de séance (6) ; statuts d'absence intacts (justifiée, non justifiée, rattrapée, annulée) ; déclarée sans motif ; période facturée + commentaire écrasé ; séance d'un autre patient / jour / centre ; fenêtres cron (J-7…J-1) vs rattrapage ; réconciliation avant détection ; notification après réconciliation ; réponse `{creees, annulees}` |
| `DetectionEtAbsencesExistantesCharacterizationIntegrationTest` | 7 | absence existante (annulée, justifiée, non justifiée, rattrapée, à qualifier, déclarée) jamais recréée ni modifiée ; autre patient même jour ; période facturée (bornes incluses, autre patient, autre centre) ; fenêtre toute facturée ; taux selon la date ; valeur nulle / exonérée / sans TVA ; valeur figée |
| `RattrapageDeDetectionCharacterizationIntegrationTest` | 11 | nominal ; idempotence sans notification ; rôles (ADMIN, 3 refusés, DIRECTION, anonyme) ; bornes (8 cas) ; durée maximale 366 / 367 ; période ancienne > 60 j ; isolation des centres ; garde de centre ; 10 corps invalides ; valorisation ; existante + facturée |
| `RattrapageAuDemarrageEtEcrituresDeMasseCharacterizationIntegrationTest` | 7 | profil test désactivé ; 31 jours sans notification ; idempotence et réconciliation à 31 jours ; centre en échec au démarrage ; 40 patients × 7 jours (280 absences) ; 250 réconciliations paginées (KB-ABS-23) ; page suivante lue |

## Mesures
- **Suite** : 3 exécutions consécutives vertes, **55/55** (la 1re en ordre par défaut, les 2 autres avec `MethodOrderer$Random` et `ClassOrderer$Random`, ordre vérifié dans les rapports), 0 flaky. Commande : `./mvnw -o -q -pl platform -am test -Dtest="<6 classes complètes>" -Dsurefire.failIfNoSpecifiedTests=false` (ajouter `-Dcharacterization.approve=true` pour enregistrer, puis relire les goldens). ≈ 1 min par exécution.
- **Mutation PIT 1.30.0** (`backend/scripts/pit-a3-detection.sh`, `--mutators DEFAULTS`) sur `AbsencePatientScheduler`, `AbsencePatientService`, `AbsencePatientRestController`, `AbsenceDonneesJdbcAdapter`, `AbsencePatientJdbcAdapter`, `AbsencePatient`, `ValorisationAbsenceService` : **105 mutants, 80 tués (76 %), 25 survivants, 0 sans couverture**, couverture de lignes des classes mutées **97 %** (231/237). La cible comprend aussi les méthodes des autres slices exercées indirectement (accesseurs de l'agrégat) d'où un taux plus bas qu'en A2.
  - 25 survivants, **tous inatteignables ou équivalents pour la détection** : accesseurs de l'agrégat renvoyant `null` (motif, auteurs, rattrapage : valeurs nulles sur le chemin détecté) et gardes du constructeur (12), filtres `motif` / `patientId` de `findPaged` et garde `total == null` (lectures A1, 3), gardes `COUNT` nul (2), valeur de retour de `save` non lue (1), branche de course de `saveIfAQualifier` (2, couverte par `AbsencePatientIntegrationTest`, seam A2), condition d'arrêt redondante de la boucle de `reconcilier` (3), `nettoyer`/`annuler` internes (2).
- **Couverture de branches** : JaCoCo n'est pas configuré (comme en A1/A2) ; chaque branche de `detecter`, `reconcilier`, `controlerCentre`, `rattraperPeriode`, du scheduler (notification oui/non, échec, démarrage actif/inactif), de la requête « patient attendu » (fermeture, 4 familles d'états, séjour limité, sommeil, admission) et de `rattraperDetection` est exercée par un scénario nommé.

## Seams
Aucun demandé ni appliqué ; `seamAllowed` inchangé (seam `saveIfAQualifier` d'A2 conservé). Une horloge injectable permettrait d'observer le fuseau (décision 7) : non demandé, le nouveau code aura un `Horloge`.

## Fichiers protégés (`covered.json`, ajouté)
`AbsencePatientScheduler`. Déjà couverts par A1 / A2 et désormais **vraiment exercés pour la détection** : `AbsencePatientService`, `AbsencePatientJdbcAdapter`, `AbsenceDonneesJdbcAdapter`, `AbsenceDonneesPort`, `AbsencePatientRestController`, `AbsencePatient`, `ValorisationAbsenceService`. `NotificationService` n'est PAS ajouté (une seule méthode exercée sur un gros fichier).

## Bugs connus
KB-ABS-22 à 32 (`known-bugs.md`) : réconciliation des absences déclarées et en période facturée, pagination décalée (200 puis 50), patient sorti sans date d'événement jamais attendu, notification sans dédoublonnage et comptes hors fenêtre, centre inactif contrôlé, centre en échec abandonné sans notification, entrées illisibles du rattrapage en 500 et anonyme 403, bornes du rattrapage, statuts de séance non réalisés, valorisation des détectées.

## Non verrouillé (limites)
- **Fuseau** (B8, décision 7) : jours relatifs à UTC ; l'écart entre l'horloge du cron Spring (fuseau JVM) et `Clock.systemUTC` n'est pas observable. L'expression cron `0 30 2 * * *` elle-même n'est pas exécutée (méthode appelée directement) ; l'abonnement `@Scheduled` et `@EventListener(ApplicationReadyEvent)` ne sont pas vérifiés.
- **Courses** : détection de nuit simultanée à une déclaration ou à un autre contrôle (contrainte unique, B5), garde de la course du seam `saveIfAQualifier` (couverte ailleurs).
- **Performance** de la requête « patient attendu » : 40 patients × 7 jours seulement ; pas de mesure de temps (non déterministe) ; plan d'exécution PostgreSQL non vérifié (H2).
- **PostgreSQL** : comportements propres au SGBD (violations de contrainte, tri des UUID de `ORDER BY p.id`, types) non testés.
- Texte de la notification côté interface (`notification-presentation.util.ts`), journal d'audit, réception temps réel côté client, front.
- Message précis des erreurs 500 (masqué) ; ordre de parcours des centres (sans `ORDER BY`).

## Questions de décision pour A3 (nouveau code)
1. **Cron et mois clos (décision 1)** : le cron saute les jours d'un mois clos et le journalise. Pendant la nuit du 1er au 7, la fenêtre J-7 chevauche le mois précédent : confirmer qu'un mois clos n'est alors **pas** complété (absence manquante rattrapée seulement en rouvrant le mois). La notification doit-elle compter les absences « à qualifier » des mois clos (aujourd'hui toutes, KB-ABS-25) ?
2. **Fuseau du centre (décision 7)** : « aujourd'hui », la fenêtre J-7…J-1 et le « en retard » se calculent au fuseau du centre. Le **déclenchement** du cron (02:30) suit-il aussi le fuseau du centre (un déclencheur par centre) ou le fuseau du serveur ? Valeur par défaut du fuseau d'un centre sans fuseau : Africa/Algiers ?
3. **Notification (KB-ABS-25)** : conserver une ligne durable par nuit tant qu'il reste des absences (comportement actuel), ou dédoublonner / compter seulement les nouvelles ? Compter les « à qualifier » hors fenêtre et des centres inactifs (KB-ABS-26) ?
4. **Réconciliation** : B2 corrigé (détectées seulement). Reproduire B3 (réconciliation dans une période facturée) ? Traiter tout en un passage (KB-ABS-23, écart de volume : 250 annulées d'un coup au lieu de 200) ?
5. **Patient attendu (KB-ABS-24)** : reproduire « sorti sans date d'événement = jamais attendu, même dans le passé » (absences passées non détectables) ou borner par une date par défaut ? Reproduire `DECEDE`/`GREFFE` exclus le jour de l'événement alors que `TRANSFERE`/`GUERRI` l'incluent ?
6. **Performance de la requête** : une requête par jour et par centre (7 à 31 jours) avec `NOT EXISTS` sur `seances`, puis une lecture + écriture par patient absent. Faut-il une seule requête sur la fenêtre (anti-jointure) et des écritures par lot, au risque de changer l'ordre de création et l'isolement d'échec (une absence = une transaction, B5) ? Index à vérifier sur `seances (center_id, patient_id, date_seance)`.
7. **Échec d'un centre (KB-ABS-27)** : abandon du centre entier au premier incident (actuel) ou isolement par absence (une absence en échec n'arrête pas les suivantes, journalisée) ?
8. **Rattrapage de détection** : entrées illisibles en 500 (KB-ABS-28) et fin silencieusement ramenée à hier : reproduire ou aligner (400, indication dans la réponse) ?
9. **Centre inactif (KB-ABS-26)** : continuer à contrôler tous les centres ?
