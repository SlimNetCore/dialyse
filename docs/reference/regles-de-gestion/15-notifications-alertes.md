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
- **RG-NOT-003** — Les évènements ne sont pas persistés : un utilisateur hors ligne ne les reçoit pas ; les **états** à
  surveiller (alertes de stock, absences à qualifier, alertes d'observance, sous-effectif,
  conflits de planning, alertes de la direction) restent consultables dans leurs écrans.

## 15.2 Catalogue des évènements de centre

| Évènement                                    | Déclencheur                                                      | Destinataires (cloche)                              | Règle                  |
|----------------------------------------------|------------------------------------------------------------------|-----------------------------------------------------|------------------------|
| `PATIENT_CREATED`, `PATIENT_UPDATED`         | création / modification d'une fiche patient                      | tous (rafraîchissement)                             | RG-PAT-015             |
| `ATTESTATION_CREATED`, `ATTESTATION_DELETED` | attestation de droits                                            | tous                                                | RG-ATT-004             |
| `PEC_VALIDATED`, `PEC_CLOSED`, `PEC_DELETED` | cycle de vie d'une PEC                                           | tous                                                | RG-PEC-003/004/008     |
| `SEANCE_CREATED`                             | création / scan d'une séance                                     | `INFIRMIER`, `MEDECIN`                              | RG-SEA-013             |
| `SEANCE_VALIDATED`                           | validation infirmière                                            | `INFIRMIER`, `MEDECIN`                              | RG-SEA-020             |
| `SEANCE_PARAMEDICAL_SAVED`                   | volet paramédical enregistré                                     | `INFIRMIER`, `MEDECIN`, `SECRETAIRE`                | RG-SEA-030             |
| `SEANCE_MEDICAL_SAVED`                       | volet médical enregistré                                         | `INFIRMIER`, `MEDECIN`, `SECRETAIRE`                | RG-SEA-031             |
| `SEANCE_CONSOMMABLE_CHANGED`                 | consommable ajouté / modifié / retiré                            | tous                                                | RG-SEA-022             |
| `OBSERVANCE_NON_RESPECTEE`                   | retard constaté ou rappel d'échéance EPO/fer (contrôle de 06:30) | `MEDECIN`                                           | RG-MED-073             |
| `ABSENCES_A_QUALIFIER`                       | absences détectées à qualifier (contrôle de 02:30)               | `ADMIN`, `SECRETAIRE`, `INFIRMIER`, `MEDECIN`       | RG-ABS-044             |
| `INFIRMIER_SOUS_EFFECTIF`                    | créneaux en sous-effectif dans les 14 jours (contrôle de 07:15)  | `ADMIN`, `SECRETAIRE`                               | RG-INF-046             |
| `INFIRMIER_ABSENCE_DECLAREE`                 | un infirmier déclare une absence                                 | `ADMIN`, `SECRETAIRE`                               | RG-INF-032             |
| `PATIENT_REPLACE_ISOLEMENT`                  | patient replacé automatiquement en isolement                     | `ADMIN`, `SECRETAIRE`, `MEDECIN`                    | RG-PLN-053             |
| `ISOLEMENT_IMPOSSIBLE`                       | patient à risque sans place d'isolement                          | `ADMIN`, `SECRETAIRE`, `MEDECIN`                    | RG-PLN-053             |
| `STOCK_MOVEMENT_CHANGED`                     | entrée ou sortie de stock                                        | tous (rafraîchissement)                             | RG-STK-021, RG-STK-030 |
| `STOCK_RECALC_LOCKS_CHANGED`                 | verrous de recalcul de PMP modifiés                              | tous                                                | RG-STK-024, RG-STK-041 |
| `STOCK_INVENTORY_CHANGED`                    | ouverture, clôture ou annulation d'inventaire                    | rôles du stock (`ADMIN`, `PHARMACIEN`, `INFIRMIER`) | RG-STK-057             |

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
