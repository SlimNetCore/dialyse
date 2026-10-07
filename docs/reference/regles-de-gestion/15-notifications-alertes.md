# 15 — Notifications temps réel et catalogue des alertes

> Préfixe `RG-NOT`. Ce chapitre **recense, en un seul endroit, tout ce que le système surveille et signale**, avec son
> déclencheur, son destinataire et le chapitre qui en détaille la règle. Sources :
> `NotificationService`, `WebSocketStockEventPublisher`, `WebSocketInventaireEventPublisher`,
> `WebSocketDirectionPublisher`, `DirectionRealtimeService`, schedulers.

## 15.1 Mécanisme

- **RG-NOT-001** — Les évènements d'un centre sont poussés sur `/topic/center/{centerId}/events` (STOMP sur `/ws`) avec
  un type, le centre, une charge utile et un horodatage ; un évènement peut porter
  `targetRoles` : la **cloche de notifications** de l'interface ne l'affiche qu'aux utilisateurs ayant l'un de ces rôles
  (RG-TRV-060). Sans `targetRoles`, il sert surtout à rafraîchir les écrans ouverts.
- **RG-NOT-002** — Chaque évènement de centre signale aussi à la direction qu'une donnée a changé (RG-TRV-061,
  RG-DIR-101).
- **RG-NOT-003** — Les évènements **temps réel** ne sont pas persistés : un utilisateur hors ligne ne les reçoit pas
  (sauf les alertes durables, RG-NOT-005) ; les **états** à surveiller (alertes de stock, absences à qualifier, alertes
  d'observance, sous-effectif, conflits de planning, alertes de la direction) restent consultables dans leurs écrans.
- **RG-NOT-005** — **Journal durable des alertes.** Les alertes qui demandent une action sont, en plus d'être poussées
  en
  temps réel, **enregistrées** dans le journal du centre (`INFIRMIER_SOUS_EFFECTIF`, `INFIRMIER_ABSENCE_DECLAREE`,
  `INFIRMIER_ABSENCE_ENREGISTREE`, `OPTIMISATION_PROPOSITION`, `ABSENCES_A_QUALIFIER`, `SEANCES_A_REGULARISER`,
  `PATIENT_REPLACE_ISOLEMENT`, `ISOLEMENT_IMPOSSIBLE`, `GENERATEUR_INDISPONIBLE`, `SEANCES_DEPLACEES`,
  `INFIRMIER_SUREFFECTIF`) ; les autres
  évènements (création, mise à jour) ne valent que sur le moment. À sa connexion, la cloche relit les 50 alertes
  récentes (30 derniers jours) **destinées à ses rôles** dans **son centre** (`GET /api/v1/notifications`, paginé) : une
  proposition calculée à 02:30 est donc retrouvée même si personne n'était connecté. La **lecture est propre à chaque
  utilisateur** (`POST /api/v1/notifications/lecture` et `/lecture-tout`) ; une alerte d'un autre centre ne peut ni se
  lire
  ni se marquer lue. Le journal ne ralentit ni ne fait échouer l'alerte temps réel (une base indisponible est ignorée) ;
  les alertes de plus de 60 jours sont purgées chaque jour à 03:40 (`NotificationPurgeScheduler`). *Source :*
  `NotificationService`, `NotificationJournalPort`, `NotificationJournalJdbcAdapter`,
  `NotificationRestController`.
- **RG-NOT-006** — **Alertes liées à l'exploitation.** Un **générateur de dialyse en service qui devient indisponible**
  (maintenance, attente de pièce, panne, réforme — par la fiche équipement ou une intervention GMAO) alerte `ADMIN` et
  `SECRETAIRE` (`GENERATEUR_INDISPONIBLE`) avec le code du générateur, son nouveau statut, le nombre de patients dont
  c'est
  la place habituelle et les cinq premiers noms : leurs séances sont à déplacer. Aucune alerte pour un autre équipement,
  pour un générateur déjà indisponible, ni à la création. Une **absence d'infirmier saisie par le secrétariat** alerte
  `ADMIN` (`INFIRMIER_ABSENCE_ENREGISTREE`) ; saisie par l'administrateur, elle ne le prévient pas. L' **application
  d'une
  proposition d'optimisation** qui déplace des patients ou des séances alerte `MEDECIN` et `SECRETAIRE`
  (`SEANCES_DEPLACEES`). Le contrôle quotidien de sous-effectif (RG-INF-046) prévient aussi `MEDECIN` ; son contrôle
  jumeau
  de **sur-effectif** (`INFIRMIER_SUREFFECTIF`, RG-INF-047) prévient `ADMIN` des infirmiers payés au-delà de l'effectif
  requis, avec les heures concernées. Un échec de ces
  alertes ne fait jamais échouer l'opération qu'elles accompagnent. Depuis la cloche, un lien mène à l'écran qui traite
  l'alerte : l'optimisation du bon périmètre (`Maintenance` pour un générateur, `Couverture` pour une absence ou un
  sous-effectif), la proposition elle-même, ou le planning ; ces liens vers l'optimisation ne sont proposés qu'à
  `ADMIN` et `SECRETAIRE`. *Source :* `GenerateurIndisponibleService`, `AbsenceInfirmierService`,
  `OptimisationApplicationService`.

- **RG-NOT-004** — **Toute saisie d'un infirmier prévient le médecin du centre** : l'évènement `SAISIE_INFIRMIER`
  (destinataire `MEDECIN`) nomme l'auteur, le patient, la date et la nature de la saisie — séance créée ou validée
  (`SEANCE_CREEE`, `SEANCE_VALIDEE`), volet paramédical (`PARAMEDICAL`), consommable ajouté / modifié / retiré
  (`CONSOMMABLE_AJOUT`, `CONSOMMABLE_MODIF`, `CONSOMMABLE_RETRAIT`), traitement anémie (`ANEMIE`), absence de patient
  (`ABSENCE`). La notification accompagne la saisie sans jamais la faire échouer (une panne du canal temps réel est
  ignorée). Les évènements de séance historiques ne visent plus le médecin : il n'est prévenu que par ce message.
  *Source :* `SaisieInfirmierNotifier`, `NotificationService.notifySaisieInfirmier`.

## 15.2 Catalogue des évènements de centre

| Évènement                                    | Déclencheur                                                                                                | Destinataires (cloche)                              | Règle                  |
|----------------------------------------------|------------------------------------------------------------------------------------------------------------|-----------------------------------------------------|------------------------|
| `PATIENT_CREATED`, `PATIENT_UPDATED`         | création / modification d'une fiche patient                                                                | tous (rafraîchissement)                             | RG-PAT-015             |
| `ATTESTATION_CREATED`, `ATTESTATION_DELETED` | attestation de droits                                                                                      | tous                                                | RG-ATT-004             |
| `PEC_VALIDATED`, `PEC_CLOSED`, `PEC_DELETED` | cycle de vie d'une PEC                                                                                     | tous                                                | RG-PEC-003/004/008     |
| `SEANCE_CREATED`                             | création / scan d'une séance                                                                               | `INFIRMIER`, `SECRETAIRE`                           | RG-SEA-013             |
| `SEANCE_VALIDATED`                           | validation infirmière                                                                                      | `INFIRMIER`, `SECRETAIRE`                           | RG-SEA-020             |
| `SEANCE_PARAMEDICAL_SAVED`                   | volet paramédical enregistré                                                                               | `INFIRMIER`, `SECRETAIRE`                           | RG-SEA-030             |
| `SEANCE_MEDICAL_SAVED`                       | volet médical enregistré                                                                                   | `INFIRMIER`, `MEDECIN`, `SECRETAIRE`                | RG-SEA-031             |
| `SEANCE_SUPPRIMEE`                           | séance supprimée par l'administrateur (motif journalisé)                                                   | `ADMIN`, `INFIRMIER`, `SECRETAIRE`                  | RG-SEA-050             |
| `SEANCE_CONSOMMABLE_CHANGED`                 | consommable ajouté / modifié / retiré                                                                      | `INFIRMIER`, `SECRETAIRE`                           | RG-SEA-022             |
| `SAISIE_INFIRMIER`                           | toute saisie d'un infirmier (voir RG-NOT-004)                                                              | `MEDECIN`                                           | RG-NOT-004             |
| `OBSERVANCE_NON_RESPECTEE`                   | retard constaté ou rappel d'échéance EPO/fer (contrôle de 06:30)                                           | `MEDECIN`                                           | RG-MED-073             |
| `ABSENCES_A_QUALIFIER`                       | absences détectées à qualifier (contrôle de 02:30)                                                         | `ADMIN`, `SECRETAIRE`, `INFIRMIER`, `MEDECIN`       | RG-ABS-044             |
| `SEANCES_A_REGULARISER`                      | séances des 7 derniers jours jamais validées, pas encore déverrouillées (rappel de 07:00)                  | `ADMIN`                                             | RG-SEA-046             |
| `SEANCE_DEVERROUILLEE`                       | l'administrateur déverrouille une séance oubliée pour régularisation                                       | `INFIRMIER`                                         | RG-SEA-046             |
| `INFIRMIER_SOUS_EFFECTIF`                    | créneaux en sous-effectif dans les 14 jours (contrôle de 07:15)                                            | `ADMIN`, `SECRETAIRE`, `MEDECIN`                    | RG-INF-046, RG-NOT-006 |
| `INFIRMIER_SUREFFECTIF`                      | infirmiers au-delà de l'effectif requis dans les 14 jours, salle sans patient comprise (contrôle de 07:15) | `ADMIN`                                             | RG-INF-047, RG-NOT-006 |
| `INFIRMIER_ABSENCE_ENREGISTREE`              | le secrétariat enregistre l'absence d'un infirmier                                                         | `ADMIN`                                             | RG-NOT-006             |
| `GENERATEUR_INDISPONIBLE`                    | un générateur en service passe en maintenance, attente de pièce, panne ou réforme                          | `ADMIN`, `SECRETAIRE`                               | RG-NOT-006             |
| `SEANCES_DEPLACEES`                          | une proposition d'optimisation appliquée déplace des patients ou des séances                               | `MEDECIN`, `SECRETAIRE`                             | RG-NOT-006             |
| `OPTIMISATION_PROPOSITION`                   | proposition utile de la replanification nocturne (sous-effectif, maintenance, gain)                        | `ADMIN`                                             | RG-PLN-100             |
| `INFIRMIER_ABSENCE_DECLAREE`                 | un infirmier déclare une absence                                                                           | `ADMIN`, `SECRETAIRE`                               | RG-INF-032             |
| `PATIENT_REPLACE_ISOLEMENT`                  | patient replacé automatiquement en isolement                                                               | `ADMIN`, `SECRETAIRE`, `MEDECIN`                    | RG-PLN-053             |
| `ISOLEMENT_IMPOSSIBLE`                       | patient à risque sans place d'isolement                                                                    | `ADMIN`, `SECRETAIRE`, `MEDECIN`                    | RG-PLN-053             |
| `STOCK_MOVEMENT_CHANGED`                     | entrée ou sortie de stock                                                                                  | tous (rafraîchissement)                             | RG-STK-021, RG-STK-030 |
| `STOCK_RECALC_LOCKS_CHANGED`                 | verrous de recalcul de PMP modifiés                                                                        | tous                                                | RG-STK-024, RG-STK-041 |
| `STOCK_INVENTORY_CHANGED`                    | ouverture, clôture ou annulation d'inventaire                                                              | rôles du stock (`ADMIN`, `PHARMACIEN`, `INFIRMIER`) | RG-STK-057             |

## 15.3 États surveillés par le système (consultables dans les écrans)

| Surveillance                     | Cadence / fraîcheur                                                 | Produit                                                                                               | Règle                              |
|----------------------------------|---------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------|------------------------------------|
| Absences de patients             | contrôle nocturne 02:30, 3 jours rétro-contrôlés                    | absences « à qualifier », « en retard » (> 3 jours)                                                   | RG-ABS-040 à 044                   |
| Observance EPO / fer             | contrôle quotidien 06:30                                            | alertes `RETARD_CONSTATE` et `RAPPEL_ECHEANCE`                                                        | RG-MED-073                         |
| Sous-effectif infirmiers         | contrôle quotidien 07:15, horizon 14 jours                          | alertes de présence, conflits                                                                         | RG-INF-045/046                     |
| Conflits de planning             | à la demande (planning réel)                                        | générateur double, salle surchargée, isolement non respecté, générateur mixte, patients à replanifier | RG-PLN-041/042                     |
| Placement incohérent             | à l'enregistrement d'une fiche                                      | refus `PLACEMENT_*`                                                                                   | RG-PLN-050                         |
| Stock                            | à la demande                                                        | péremption (≤ 30 j), rupture, seuil                                                                   | RG-STK-060                         |
| Péremption J-30                  | quotidien 07:00                                                     | consignation au journal technique uniquement (pas de message)                                         | RG-STK-061                         |
| Disponibilité d'un générateur    | à l'affectation d'un patient                                        | avertissement non bloquant                                                                            | RG-GMA-017                         |
| Interventions et plans en retard | à la demande                                                        | compteurs de relance                                                                                  | RG-GMA-030, RG-GMA-052             |
| Réforme recommandée              | à la demande                                                        | fiche équipement, direction                                                                           | RG-GMA-063                         |
| Cibles cliniques KDIGO           | à la demande                                                        | statut dans / sous / au-dessus de la cible                                                            | RG-MED-081                         |
| Alertes de la direction          | temps réel (1 s / 10 s)                                             | Kt/V, Hb, observance, stock, péremption, GMAO                                                         | RG-DIR-031, RG-DIR-051, RG-DIR-100 |
| Licence                          | à chaque requête (60 s en cache), contrôle en ligne quotidien 03:15 | refus 402                                                                                             | RG-SEC-032, RG-SEC-036             |
| Journal d'audit                  | écriture asynchrone (3 s), purge 03:30                              | traces des actions                                                                                    | RG-SEC-050 à 053                   |
| Instantanés de la direction      | le 1er du mois à 02:30                                              | mois précédent figé                                                                                   | RG-DIR-090                         |
